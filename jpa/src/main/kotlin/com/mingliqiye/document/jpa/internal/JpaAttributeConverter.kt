package com.mingliqiye.document.jpa.internal

import com.mingliqiye.document.jpa.exception.JpaConfigException
import com.mingliqiye.document.jpa.exception.JpaMappingException
import jakarta.persistence.AttributeConverter
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType

/**
 * 已解析的 JPA [AttributeConverter]：转换器实例 + `AttributeConverter<X, Y>` 的泛型类型。
 *
 * 属性类型为 X、数据库列类型为 Y 时：
 * - 写出：实体属性值 X → 数据库值 Y → [com.mingliqiye.document.core.Node]；
 * - 读入：Node → 数据库值 Y → 实体属性值 X。
 *
 * 转换器实例在元数据解析阶段创建一次并按类缓存，可跨线程复用；转换器实现本身
 * 需要线程安全（与 JPA 提供者的要求一致）。
 *
 * 与 Hibernate 等 JPA 提供者一致，转换方法在实体属性值/数据库值为 null 时也会被
 * 调用（`convertToDatabaseColumn(null)` / `convertToEntityAttribute(null)`）：
 * 转换器必须能处理 null。转换器抛出的运行时异常会包装为 [JpaMappingException] 并
 * 保留 cause。
 */
internal class JpaAttributeConverter private constructor(
    /** 转换器实现类。 */
    val converterClass: Class<*>,
    /** 转换器实例。 */
    private val instance: AttributeConverter<Any?, Any?>,
    /** 实体属性类型 X。 */
    val entityType: Class<*>,
    /** 数据库列类型 Y。 */
    val databaseType: Class<*>,
) {

    /** 转换器是否适用于给定属性类型（基本类型与包装类型等价）。 */
    fun appliesTo(attributeType: Class<*>): Boolean = boxedType(entityType) == boxedType(attributeType)

    /**
     * 实体属性值 → 数据库值。
     *
     * @param value 非 null 的实体属性值
     * @param path 当前文档路径，用于异常信息
     */
    fun convertToDatabaseColumn(value: Any?, path: String): Any? {
        val converted = try {
            instance.convertToDatabaseColumn(value)
        } catch (e: RuntimeException) {
            throw JpaMappingException(
                "Attribute converter ${converterClass.name} failed to convert an entity attribute at $path",
                e,
            )
        }
        if (converted != null && !boxedType(databaseType).isInstance(converted)) {
            throw JpaMappingException(
                "Attribute converter ${converterClass.name} returned ${converted.javaClass.name} " +
                    "but its declared database type is ${databaseType.name} at $path",
            )
        }
        return converted
    }

    /**
     * 数据库值 → 实体属性值。
     *
     * @param dbData 非 null 的数据库值
     * @param path 当前文档路径，用于异常信息
     */
    fun convertToEntityAttribute(dbData: Any?, path: String): Any? {
        val converted = try {
            instance.convertToEntityAttribute(dbData)
        } catch (e: RuntimeException) {
            throw JpaMappingException(
                "Attribute converter ${converterClass.name} failed to convert a database value at $path",
                e,
            )
        }
        if (converted != null && !boxedType(entityType).isInstance(converted)) {
            throw JpaMappingException(
                "Attribute converter ${converterClass.name} returned ${converted.javaClass.name} " +
                    "but its declared entity attribute type is ${entityType.name} at $path",
            )
        }
        return converted
    }

    override fun toString(): String = "JpaAttributeConverter(${converterClass.name})"

    internal companion object {

        /**
         * 解析并实例化转换器类。
         *
         * JPA 要求转换器类实现 [AttributeConverter] 并提供无参构造器；本实现还要求
         * `AttributeConverter<X, Y>` 的泛型参数可解析（直接实现接口或沿父类链实现）。
         *
         * @param converterClass 转换器类
         * @param path 配置/属性路径，用于异常信息
         */
        fun of(converterClass: Class<*>, path: String): JpaAttributeConverter {
            if (!AttributeConverter::class.java.isAssignableFrom(converterClass)) {
                throw JpaConfigException(
                    "Attribute converter ${converterClass.name} does not implement " +
                        "jakarta.persistence.AttributeConverter at $path",
                )
            }
            if (converterClass.isInterface || Modifier.isAbstract(converterClass.modifiers)) {
                throw JpaConfigException("Attribute converter ${converterClass.name} must be a concrete class at $path")
            }
            val parameterized = converterTypeOf(converterClass)
                ?: throw JpaConfigException(
                    "Cannot resolve the AttributeConverter<X, Y> generic types of ${converterClass.name}; " +
                        "implement AttributeConverter directly or through a parameterized superclass at $path",
                )
            if (parameterized.actualTypeArguments.size < 2) {
                throw JpaConfigException(
                    "Cannot resolve the AttributeConverter<X, Y> generic types of ${converterClass.name} at $path",
                )
            }
            val entityType = rawTypeOf(parameterized.actualTypeArguments[0])
                ?: throw JpaConfigException(
                    "Cannot resolve the entity attribute type of attribute converter ${converterClass.name} at $path",
                )
            val databaseType = rawTypeOf(parameterized.actualTypeArguments[1])
                ?: throw JpaConfigException(
                    "Cannot resolve the database column type of attribute converter ${converterClass.name} at $path",
                )
            val instance = instantiate(converterClass, path)
            return JpaAttributeConverter(converterClass, instance, entityType, databaseType)
        }

        private fun instantiate(converterClass: Class<*>, path: String): AttributeConverter<Any?, Any?> {
            val constructor = try {
                converterClass.getDeclaredConstructor()
            } catch (e: NoSuchMethodException) {
                throw JpaConfigException(
                    "Attribute converter ${converterClass.name} must declare a no-argument constructor at $path",
                    e,
                )
            }
            @Suppress("UNCHECKED_CAST")
            return try {
                constructor.isAccessible = true
                constructor.newInstance() as AttributeConverter<Any?, Any?>
            } catch (e: InvocationTargetException) {
                throw JpaConfigException(
                    "Constructor of attribute converter ${converterClass.name} failed at $path",
                    e.cause ?: e,
                )
            } catch (e: ReflectiveOperationException) {
                throw JpaConfigException("Cannot instantiate attribute converter ${converterClass.name} at $path", e)
            } catch (e: IllegalArgumentException) {
                throw JpaConfigException("Cannot instantiate attribute converter ${converterClass.name} at $path", e)
            }
        }

        /**
         * 查找类层次上直接实现的 `AttributeConverter<X, Y>` 参数化接口。
         *
         * 支持 `class X : AttributeConverter<A, B>` 以及父类实现、子类继承该接口的常见写法；
         * 通过中间泛型基类间接实现且无法解析泛型实参时返回 `null`（调用方报配置错误）。
         */
        private fun converterTypeOf(type: Class<*>): ParameterizedType? {
            var current: Class<*>? = type
            while (current != null && current != Any::class.java) {
                for (genericInterface in current.genericInterfaces) {
                    if (genericInterface is ParameterizedType &&
                        genericInterface.rawType == AttributeConverter::class.java
                    ) {
                        return genericInterface
                    }
                }
                current = current.superclass
            }
            return null
        }
    }
}
