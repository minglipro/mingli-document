package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentTypeInfo
import com.mingliqiye.document.annotation.DocumentTypeName

/**
 * 多态子类型注册表。
 *
 * 每个注册的基类/接口对应一个注册表，保存类型名与子类之间的双向映射。
 * 通过 [DocumentMapperConfigBuilder.subtype] 注册，Mapper 构建配置时创建，
 * 之后不可变。显式 [DocumentTypeName] 注解优先于注册时提供的类型名；
 * 注册名会作为别名保留，仍可用于反序列化。
 *
 * @property baseClass 注册的基类/接口
 * @property typeProperty 文档中记录类型名的属性名
 */
public class SubtypeRegistry internal constructor(
    public val baseClass: Class<*>,
    public val typeProperty: String,
    private val subtypeByName: Map<String, Class<*>>,
    private val nameBySubtype: Map<Class<*>, String>,
) {

    /** 反序列化可接受的类型名集合（含显式注解名与注册别名），保持注册顺序。 */
    public val typeNames: Set<String>
        get() = subtypeByName.keys

    /**
     * 按类型名查找已注册的子类。
     *
     * @param typeName 文档中的类型名
     * @return 子类；未注册时返回 `null`
     */
    public fun subtypeClass(typeName: String): Class<*>? = subtypeByName[typeName]

    /**
     * 按子类查找序列化使用的类型名。
     *
     * 只匹配显式注册过的子类；子类的子类不会回退到父类类型名，避免丢失实际类型。
     *
     * @param subtype 子类
     * @return 类型名；未注册时返回 `null`
     */
    public fun typeName(subtype: Class<*>): String? = nameBySubtype[subtype]
}

/**
 * 已注册基类到子类型注册表的不可变映射。
 */
internal class SubtypeRegistries(
    private val registries: Map<Class<*>, SubtypeRegistry>,
) {

    val isEmpty: Boolean
        get() = registries.isEmpty()

    fun registryFor(baseClass: Class<*>): SubtypeRegistry? = registries[baseClass]

    fun all(): Collection<SubtypeRegistry> = registries.values

    fun toMap(): Map<Class<*>, SubtypeRegistry> = registries

    companion object {

        val EMPTY: SubtypeRegistries = SubtypeRegistries(emptyMap())

        /**
         * 从构建器收集的注册项构建注册表，并校验注册项合法性。
         *
         * @param registrations 基类到类型名/子类注册项的映射
         * @param typeProperties 基类显式指定的类型属性名
         */
        fun build(
            registrations: Map<Class<*>, List<SubtypeRegistration>>,
            typeProperties: Map<Class<*>, String>,
        ): SubtypeRegistries {
            if (registrations.isEmpty()) {
                return EMPTY
            }
            require(typeProperties.keys.all { it in registrations }) {
                "typeProperty registered for a base without subtypes: " +
                    typeProperties.keys.filter { it !in registrations }.joinToString { it.name }
            }
            val result = LinkedHashMap<Class<*>, SubtypeRegistry>()
            for ((baseClass, items) in registrations) {
                result[baseClass] = buildRegistry(baseClass, items, typeProperties[baseClass])
            }
            return SubtypeRegistries(result)
        }

        private fun buildRegistry(
            baseClass: Class<*>,
            items: List<SubtypeRegistration>,
            explicitTypeProperty: String?,
        ): SubtypeRegistry {
            require(items.isNotEmpty()) { "No subtypes registered for ${baseClass.name}" }
            val annotatedProperty = baseClass.getAnnotation(DocumentTypeInfo::class.java)?.property
            if (annotatedProperty != null && annotatedProperty.isBlank()) {
                throw IllegalArgumentException(
                    "DocumentTypeInfo property on ${baseClass.name} must not be blank",
                )
            }
            val typeProperty = explicitTypeProperty ?: annotatedProperty ?: DEFAULT_TYPE_PROPERTY
            val subtypeByName = LinkedHashMap<String, Class<*>>()
            val nameBySubtype = LinkedHashMap<Class<*>, String>()
            for (item in items) {
                val subtype = item.subtypeClass
                require(baseClass.isAssignableFrom(subtype)) {
                    "Subtype ${subtype.name} is not assignable to ${baseClass.name}"
                }
                val annotatedName = subtype.getAnnotation(DocumentTypeName::class.java)?.value
                if (annotatedName != null && annotatedName.isBlank()) {
                    throw IllegalArgumentException(
                        "DocumentTypeName on ${subtype.name} must not be blank",
                    )
                }
                val typeName = annotatedName ?: item.typeName
                val previous = nameBySubtype.put(subtype, typeName)
                if (previous != null && previous != typeName) {
                    throw IllegalArgumentException(
                        "Subtype ${subtype.name} is registered with multiple type names " +
                            "for ${baseClass.name}: '$previous' and '$typeName'",
                    )
                }
                registerName(subtypeByName, item.typeName, subtype, baseClass)
                if (typeName != item.typeName) {
                    registerName(subtypeByName, typeName, subtype, baseClass)
                }
            }
            return SubtypeRegistry(baseClass, typeProperty, subtypeByName, nameBySubtype)
        }

        private fun registerName(
            subtypeByName: MutableMap<String, Class<*>>,
            typeName: String,
            subtype: Class<*>,
            baseClass: Class<*>,
        ) {
            val previous = subtypeByName.putIfAbsent(typeName, subtype)
            require(previous == null || previous == subtype) {
                "Type name '$typeName' for ${baseClass.name} is already registered by " +
                    "${previous?.name}"
            }
        }
    }
}

/**
 * 构建器暂存的一条子类型注册项。
 */
internal class SubtypeRegistration(
    val typeName: String,
    val subtypeClass: Class<*>,
)

private const val DEFAULT_TYPE_PROPERTY = "type"
