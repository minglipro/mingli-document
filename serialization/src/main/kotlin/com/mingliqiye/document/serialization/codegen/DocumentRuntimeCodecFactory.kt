package com.mingliqiye.document.serialization.codegen

import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import java.util.function.Function

/**
 * 运行期 Bean 编解码器工厂。
 *
 * 基于字段反射元数据与 [DocumentAccessorCodegen] 生成的访问器，按需构建
 * [DocumentBaseConverter]；转换器注册到 Mapper 后，该类型的 Bean 映射会走这条
 * 预编译路径，而不是每次重新查找反射属性。
 *
 * 支持：
 * - `@DocumentName` 字段名
 * - [PropertyNamingStrategy] 命名策略
 * - `@DocumentIgnore` 忽略字段
 * - 无参构造器 + setter 反序列化
 * - `writeNulls` 控制 null 字段
 *
 * 限制：不支持构造器参数注入、Builder、不可变对象、多态子类型。
 *
 * @param namingStrategy 字段命名策略
 * @param writeNulls 是否写出 null 字段
 */
public class DocumentRuntimeCodecFactory(
    private val namingStrategy: PropertyNamingStrategy = PropertyNamingStrategy.AS_IS,
    private val writeNulls: Boolean = false,
) {

    private val converters = ConcurrentHashMap<Class<*>, DocumentBaseConverter<*>>()

    /**
     * 为指定类型获取运行期转换器；相同类型复用缓存实例。
     *
     * @param type 目标类型
     * @return 双向转换器
     */
    @Suppress("UNCHECKED_CAST")
    public fun <T> converter(type: Class<T>): DocumentBaseConverter<T> = converters.computeIfAbsent(type) { buildConverter(it) } as DocumentBaseConverter<T>

    private fun <T> buildConverter(type: Class<T>): DocumentBaseConverter<T> {
        val properties = propertiesOf(type)
        return RuntimeBeanConverter(type, properties, writeNulls)
    }

    private fun propertiesOf(type: Class<*>): List<RuntimeProperty> {
        val result = ArrayList<RuntimeProperty>()
        val seen = HashSet<String>()
        var current: Class<*>? = type
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (!isSupportedField(field) || !seen.add(field.name)) {
                    continue
                }
                if (isIgnored(type, field)) {
                    continue
                }
                result.add(property(type, field))
            }
            current = current.superclass
        }
        return result
    }

    private fun isIgnored(type: Class<*>, field: Field): Boolean = field.isAnnotationPresent(DocumentIgnore::class.java) ||
        findGetter(type, field.name)?.isAnnotationPresent(DocumentIgnore::class.java) == true ||
        propertyAnnotations(type, field.name)?.isAnnotationPresent(DocumentIgnore::class.java) == true

    private fun propertyAnnotations(type: Class<*>, property: String): java.lang.reflect.Method? {
        val capitalized = property.replaceFirstChar { it.uppercaseChar() }
        for (name in listOf("get$capitalized\$annotations", "is$capitalized\$annotations")) {
            try {
                return type.getDeclaredMethod(name)
            } catch (_: NoSuchMethodException) {
                // 继续尝试
            }
        }
        return null
    }

    private fun isSupportedField(field: Field): Boolean {
        val modifiers = field.modifiers
        return !Modifier.isStatic(modifiers) &&
            !Modifier.isTransient(modifiers) &&
            !field.isSynthetic &&
            !field.name.startsWith("$")
    }

    private fun property(type: Class<*>, field: Field): RuntimeProperty {
        val accessors = DocumentAccessorCodegen()
        val getter = accessors.getter(type, field.name) ?: Function { target -> field.apply { isAccessible = true }.get(target) }
        val setter = accessors.setter(type, field.name)
        val annotation = field.getAnnotation(DocumentName::class.java)
            ?: findGetter(type, field.name)?.getAnnotation(DocumentName::class.java)
            ?: propertyAnnotations(type, field.name)?.getAnnotation(DocumentName::class.java)
        val documentName = annotation?.value ?: namingStrategy.translate(field.name)
        return RuntimeProperty(
            documentName = documentName,
            type = field.genericType,
            getter = getter,
            setter = setter,
            field = field,
        )
    }

    private fun findGetter(type: Class<*>, property: String): java.lang.reflect.Method? {
        val capitalized = property.replaceFirstChar { it.uppercaseChar() }
        for (name in listOf("get$capitalized", "is$capitalized")) {
            try {
                return type.getMethod(name)
            } catch (_: NoSuchMethodException) {
                // 继续尝试
            }
        }
        return null
    }

    internal class RuntimeProperty(
        val documentName: String,
        val type: java.lang.reflect.Type,
        val getter: Function<Any, Any?>,
        val setter: BiConsumer<Any, Any?>?,
        val field: Field,
    )

    /**
     * 内部运行期转换器。
     */
    internal class RuntimeBeanConverter<T>(
        private val beanType: Class<T>,
        private val properties: List<RuntimeProperty>,
        private val writeNulls: Boolean,
    ) : DocumentBaseConverter<T> {

        private val byName = properties.associateBy { it.documentName }

        override fun getType(): DocumentTypeReference<T> = DocumentTypeReference.of(beanType)

        override fun serialize(value: T, context: com.mingliqiye.document.serialization.SerializationContext): com.mingliqiye.document.core.Node {
            val fields = LinkedHashMap<String, com.mingliqiye.document.core.Node>(properties.size)
            for (property in properties) {
                val propertyValue = property.getter.apply(value as Any)
                if (propertyValue == null && !writeNulls) {
                    continue
                }
                fields[property.documentName] = if (propertyValue == null) {
                    com.mingliqiye.document.core.NullNode.INSTANCE
                } else {
                    context.mapper.toNode(propertyValue)
                }
            }
            return com.mingliqiye.document.core.ObjectNode.from(fields)
        }

        @Suppress("UNCHECKED_CAST")
        override fun deserialize(
            node: com.mingliqiye.document.core.Node,
            context: com.mingliqiye.document.serialization.DeserializationContext,
        ): T? {
            val objectNode = node.asObjectOrNull() ?: return null
            val bean = try {
                beanType.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            } catch (e: Exception) {
                throw DocumentSerializationException("Cannot instantiate ${beanType.name}", e)
            }
            for ((name, child) in objectNode.asMap()) {
                val property = byName[name] ?: continue
                val setter = property.setter ?: continue
                val value = try {
                    context.mapper.fromNode(child, DocumentTypeReference.of<Any>(property.type))
                } catch (e: Exception) {
                    throw DocumentSerializationException("Cannot convert field '$name' of ${beanType.name}", e)
                }
                setter.accept(bean as Any, value)
            }
            return bean
        }
    }
}
