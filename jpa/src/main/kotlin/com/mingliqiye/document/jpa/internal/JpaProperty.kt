package com.mingliqiye.document.jpa.internal

import com.mingliqiye.document.jpa.exception.JpaMappingException
import jakarta.persistence.EnumType
import jakarta.persistence.TemporalType
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type

/**
 * JPA 属性类型。
 */
internal enum class JpaPropertyKind {

    /** 标量属性：字符串、数字、布尔、枚举、日期时间、二进制、集合/Map 值等。 */
    SCALAR,

    /** `@Embedded`/`@EmbeddedId` 嵌入对象。 */
    EMBEDDED,

    /** `@OneToOne`/`@OneToMany`/`@ManyToOne`/`@ManyToMany` 关联。 */
    ASSOCIATION,

    /** `@ElementCollection` 元素集合（元素为标量或嵌入对象）。 */
    ELEMENT_COLLECTION,
}

/**
 * 关联类型。
 */
internal enum class JpaAssociationKind {
    ONE_TO_ONE,
    ONE_TO_MANY,
    MANY_TO_ONE,
    MANY_TO_MANY,
}

/**
 * 单个 JPA 属性的映射元数据。
 *
 * 同时保存读取与写入所需的反射成员：字段访问使用 [field]，属性访问使用 [getter]/[setter]。
 * 二者都允许存在，读取优先字段，写入优先可写的字段、其次 setter。
 */
internal class JpaProperty(
    /** Java/Kotlin 属性名。 */
    val name: String,
    /** 文档节点字段名（经命名策略转换）。 */
    val nodeName: String,
    /** 列名（`@Column(name)` 或属性名）；关联/嵌入属性使用属性名。 */
    val columnName: String,
    val kind: JpaPropertyKind,
    /** 属性的声明类型（集合属性为集合类型本身）。 */
    val declaredType: Class<*>,
    /** 属性的完整泛型类型，用于解析集合元素类型。 */
    val genericType: Type,
    val field: Field?,
    val getter: Method?,
    val setter: Method?,
    /** 是否 `@Id`。 */
    val id: Boolean,
    /** 是否 `@EmbeddedId`。 */
    val embeddedId: Boolean,
    /** 是否允许 null（`@Column(nullable)`/关联 `optional`）。 */
    val nullable: Boolean,
    /** `@Enumerated` 的值；未标注时为 `null`。 */
    val enumType: EnumType?,
    /** `@Temporal` 的值；未标注时为 `null`。 */
    val temporalType: TemporalType?,
    /** 关联类型；非关联属性为 `null`。 */
    val associationKind: JpaAssociationKind?,
    /** 集合元素类型：关联目标类型、`@ElementCollection` 元素类型或 Map 值类型。 */
    val elementType: Class<*>?,
    /** 是否 transient 属性（仅在 `includeTransient` 开启时出现）。 */
    val transientProperty: Boolean,
    /** 显式/自动应用的属性转换器（`@Convert` / autoApply）；非转换属性为 `null`。 */
    val converter: JpaAttributeConverter? = null,
    /** 是否为 Map 关联 / Map `@ElementCollection`。 */
    val map: Boolean = false,
    /** Map 键类型；无法从泛型确定且使用 `@MapKey` 时为 `null`（映射时按目标实体的 `@Id` 解析）。 */
    val mapKeyType: Class<*>? = null,
    /** Map 键枚举策略（`@MapKeyEnumerated`）；未标注时为 `null`（按 JPA 默认 ORDINAL）。 */
    val mapKeyEnumerated: EnumType? = null,
    /** Map 键日期策略（`@MapKeyTemporal`）；未标注时为 `null`。 */
    val mapKeyTemporal: TemporalType? = null,
    /** `@MapKey` 指定的目标实体属性名；空字符串表示使用目标实体的主键。 */
    val mapKeyPropertyName: String? = null,
    /** Map 键是否为实体引用（`@MapKeyJoinColumn`）：文档键为键实体的标识符文本。 */
    val mapKeyEntity: Boolean = false,
    /**
     * 属性级 `@Convert(attributeName = ...)` 覆盖规则，键为相对该属性的点分路径
     * （例如 `@Embedded` 属性上的 `zipCode` / `geo.lat`）。
     */
    val nestedConvertOverrides: Map<String, JpaConvertOverride> = emptyMap(),
) {

    /** 声明类型是否为基本类型。 */
    val primitive: Boolean
        get() = declaredType.isPrimitive

    /** 是否为“显式 @Convert 的关联属性”：整个关联值按基本属性转换后写出/读入。 */
    val associationConverted: Boolean
        get() = kind == JpaPropertyKind.ASSOCIATION && converter != null

    /** 是否可读取。 */
    val canRead: Boolean
        get() = this.field != null || this.getter != null

    /** 是否可写入：可写字段（非 final）或 setter。 */
    val canWrite: Boolean
        get() = this.setter != null || (this.field != null && !Modifier.isFinal(this.field.modifiers))

    /** 是否为关联属性。 */
    val association: Boolean
        get() = kind == JpaPropertyKind.ASSOCIATION

    /**
     * 读取实例上的属性值。
     *
     * @param instance 目标对象
     * @param path 当前文档路径，用于异常信息
     */
    fun read(instance: Any, path: String): Any? {
        val member = field
        if (member != null) {
            return try {
                member.isAccessible = true
                member.get(instance)
            } catch (e: ReflectiveOperationException) {
                throw JpaMappingException("Cannot read property '$name' at $path", e)
            } catch (e: IllegalArgumentException) {
                throw JpaMappingException("Cannot read property '$name' at $path", e)
            }
        }
        val accessor = getter ?: throw JpaMappingException("Property '$name' is not readable at $path")
        return try {
            accessor.isAccessible = true
            accessor.invoke(instance)
        } catch (e: InvocationTargetException) {
            throw JpaMappingException("Getter of property '$name' failed at $path", e.cause ?: e)
        } catch (e: ReflectiveOperationException) {
            throw JpaMappingException("Cannot read property '$name' at $path", e)
        }
    }

    /**
     * 写入实例上的属性值。
     *
     * @param instance 目标对象
     * @param value 属性值
     * @param path 当前文档路径，用于异常信息
     */
    fun write(instance: Any, value: Any?, path: String) {
        val member = field
        if (member != null && !Modifier.isFinal(member.modifiers)) {
            try {
                member.isAccessible = true
                member.set(instance, value)
                return
            } catch (e: ReflectiveOperationException) {
                throw JpaMappingException("Cannot write property '$name' at $path", e)
            } catch (e: IllegalArgumentException) {
                throw JpaMappingException("Cannot assign value of type ${value?.javaClass?.name} to property '$name' at $path", e)
            }
        }
        val accessor = setter
        if (accessor != null) {
            invokeSetter(accessor, instance, value, path)
            return
        }
        if (member != null && Modifier.isFinal(member.modifiers)) {
            // Kotlin `val` 之类不可写字段：只能依赖 setter；没有 setter 时明确报错。
            throw JpaMappingException("Property '$name' is read-only and cannot be written at $path")
        }
        throw JpaMappingException("Property '$name' is not writable at $path")
    }

    private fun invokeSetter(accessor: Method, instance: Any, value: Any?, path: String) {
        try {
            accessor.isAccessible = true
            accessor.invoke(instance, value)
        } catch (e: InvocationTargetException) {
            throw JpaMappingException("Setter of property '$name' failed at $path", e.cause ?: e)
        } catch (e: ReflectiveOperationException) {
            throw JpaMappingException("Cannot write property '$name' at $path", e)
        } catch (e: IllegalArgumentException) {
            throw JpaMappingException(
                "Cannot assign value of type ${value?.javaClass?.name} to property '$name' at $path",
                e,
            )
        }
    }
}
