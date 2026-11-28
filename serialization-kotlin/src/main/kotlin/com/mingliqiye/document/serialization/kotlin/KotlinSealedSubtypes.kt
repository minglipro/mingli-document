package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.annotation.DocumentTypeName
import com.mingliqiye.document.serialization.DocumentMapperConfigBuilder
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.reflect.KClass

/**
 * 一条 sealed 子类型多态注册绑定。
 *
 * @property baseClass 多态基类/接口（JVM 类型）
 * @property typeName 文档中的类型名（子类 [DocumentTypeName] 注解优先）
 * @property subtypeClass 具体子类（JVM 类型）
 */
public class KotlinSealedSubtype internal constructor(
    public val baseClass: Class<*>,
    public val typeName: String,
    public val subtypeClass: Class<*>,
) {

    override fun toString(): String = "${baseClass.name}[$typeName]=${subtypeClass.name}"
}

/**
 * 反射发现 sealed class / sealed interface 的全部具体叶子子类，生成多态注册绑定。
 *
 * 子类型名优先取子类上的 [DocumentTypeName]；没有注解时使用简单类名，
 * 简单类名重复时回退到 JVM 全限定名。生成的绑定可直接用于
 * [DocumentMapperConfigBuilder.subtype]，适合需要同时配置格式专属选项
 * （例如 `JsonConfig.builder()`）的场景：
 * ```kotlin
 * val config = JsonConfig.builder()
 * for (binding in kotlinSealedSubtypeBindings(Shape::class)) {
 *     config.subtype(binding.baseClass, binding.typeName, binding.subtypeClass)
 * }
 * val mapper = JsonMapper.builder()
 *     .config(config.build())
 *     .addModule(kotlinModule())
 *     .build()
 * ```
 *
 * @param baseClasses sealed 基类/接口
 * @return 按发现顺序排列的绑定；sealed 子类递归展开
 * @throws IllegalArgumentException 传入类型不是 sealed class / sealed interface 时抛出
 * @throws DocumentSerializationException sealed 层级包含无法多态映射的子类型时抛出：
 *   value class、enum、非 sealed 的抽象中间子类（无法反射发现其后代）
 */
public fun kotlinSealedSubtypeBindings(vararg baseClasses: KClass<*>): List<KotlinSealedSubtype> {
    val result = ArrayList<KotlinSealedSubtype>()
    for (baseClass in baseClasses) {
        result.addAll(discoverSealedSubtypes(baseClass))
    }
    return result
}

/**
 * 为 sealed class / sealed interface 自动注册全部具体叶子子类的多态绑定。
 *
 * ```kotlin
 * val config = DocumentMapperConfig.builder()
 *     .kotlinSealedSubtypes(Shape::class, Notification::class)
 *     .build()
 * val mapper = JsonMapper.builder()
 *     .config(config)
 *     .addModule(kotlinModule())
 *     .build()
 * ```
 *
 * 类型属性名仍由 [com.mingliqiye.document.annotation.DocumentTypeInfo] 决定
 * （默认 `type`），可用 [DocumentMapperConfigBuilder.typeProperty] 覆盖。
 *
 * @param baseClasses sealed 基类/接口
 * @return 当前配置构建器
 */
public fun DocumentMapperConfigBuilder.kotlinSealedSubtypes(
    vararg baseClasses: KClass<*>,
): DocumentMapperConfigBuilder {
    for (binding in kotlinSealedSubtypeBindings(*baseClasses)) {
        subtype(binding.baseClass, binding.typeName, binding.subtypeClass)
    }
    return this
}

private fun discoverSealedSubtypes(baseClass: KClass<*>): List<KotlinSealedSubtype> {
    require(baseClass.isSealed) {
        "${baseClass.java.name} is not a sealed class or sealed interface"
    }
    val leaves = ArrayList<KClass<*>>()
    collectSealedLeaves(baseClass, leaves)
    if (leaves.isEmpty()) {
        throw IllegalArgumentException(
            "No registrable sealed subclasses found for ${baseClass.java.name}; " +
                "the hierarchy only contains abstract, value class or enum subclasses",
        )
    }
    val names = assignTypeNames(leaves)
    return leaves.map { subtype ->
        KotlinSealedSubtype(baseClass.java, names[subtype] ?: subtype.java.name, subtype.java)
    }
}

private fun collectSealedLeaves(kClass: KClass<*>, leaves: MutableList<KClass<*>>) {
    for (subtype in kClass.sealedSubclasses) {
        when {
            subtype.isSealed -> collectSealedLeaves(subtype, leaves)
            subtype.isValue -> throw DocumentSerializationException(
                "Sealed subtype ${subtype.java.name} is a value class; " +
                    "value classes cannot be serialized as polymorphic object subtypes",
            )

            subtype.java.isEnum -> throw DocumentSerializationException(
                "Sealed subtype ${subtype.java.name} is an enum; " +
                    "enums serialize as strings and cannot be polymorphic object subtypes",
            )

            subtype.isAbstract || subtype.java.isInterface -> throw DocumentSerializationException(
                "Sealed subtype ${subtype.java.name} of ${kClass.java.name} is a non-sealed abstract " +
                    "class or interface; its concrete descendants cannot be discovered by reflection. " +
                    "Register them explicitly with DocumentMapperConfigBuilder.subtype",
            )

            else -> leaves.add(subtype)
        }
    }
}

private fun assignTypeNames(leaves: List<KClass<*>>): Map<KClass<*>, String> {
    val names = LinkedHashMap<KClass<*>, String>()
    for (subtype in leaves) {
        val annotated = subtype.java.getAnnotation(DocumentTypeName::class.java)?.value
        if (annotated != null) {
            names[subtype] = annotated
        }
    }
    val fallback = LinkedHashMap<KClass<*>, String>()
    for (subtype in leaves) {
        if (subtype in names) {
            continue
        }
        fallback[subtype] = subtype.simpleName ?: subtype.java.name
    }
    val duplicates = fallback.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    for ((subtype, name) in fallback) {
        names[subtype] = if (name in duplicates) subtype.java.name else name
    }
    return names
}
