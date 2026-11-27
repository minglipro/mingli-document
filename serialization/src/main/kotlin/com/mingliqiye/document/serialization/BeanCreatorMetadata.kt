package com.mingliqiye.document.serialization

import java.lang.reflect.Constructor
import java.lang.reflect.Type

/**
 * [com.mingliqiye.document.annotation.DocumentCreator] 构造参数元数据。
 *
 * @property index 构造参数位置（从 0 开始）
 * @property javaName 反射可见的 Java 参数名；未保留参数名时为 `null`
 * @property documentName 文档字段名，已应用参数上的
 *   [com.mingliqiye.document.annotation.DocumentName]
 * @property explicitDocumentName 参数上显式标注的文档字段名；未标注时为 `null`
 * @property type 参数泛型类型
 */
internal class BeanCreatorParameter(
    val index: Int,
    val javaName: String?,
    val documentName: String,
    val explicitDocumentName: String?,
    val type: Type,
)

/**
 * 预编译的 [com.mingliqiye.document.annotation.DocumentCreator] 构造器元数据。
 *
 * 每个类型只解析一次并缓存，反序列化时直接按文档字段名查找参数，避免重复反射。
 *
 * @property ownerType 声明构造器的类型
 * @property constructor 需要使用的构造器
 * @property parameters 构造参数元数据，顺序与构造器一致
 */
internal class BeanCreatorMetadata(
    val ownerType: Class<*>,
    val constructor: Constructor<*>,
    val parameters: List<BeanCreatorParameter>,
) {

    /** 构造参数个数。 */
    val argumentCount: Int
        get() = parameters.size

    /** 构造参数可接受的文档字段名集合。 */
    val documentNames: Set<String> = parameters.mapTo(LinkedHashSet()) { it.documentName }

    private val parametersByDocumentName: Map<String, BeanCreatorParameter> =
        parameters.associateBy { it.documentName }

    private val parametersByJavaName: Map<String, BeanCreatorParameter> =
        parameters.mapNotNull { parameter -> parameter.javaName?.let { it to parameter } }.toMap()

    /** 按文档字段名查找构造参数。 */
    fun parameter(documentName: String): BeanCreatorParameter? = parametersByDocumentName[documentName]

    /** 按 Java 参数名查找构造参数；参数名未保留时返回 `null`。 */
    fun parameterByJavaName(javaName: String): BeanCreatorParameter? = parametersByJavaName[javaName]

    /** 创建与参数个数一致的实参数组。 */
    fun newArguments(): Array<Any?> = arrayOfNulls(argumentCount)
}
