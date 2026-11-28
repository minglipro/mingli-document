package com.mingliqiye.document.serialization

import java.lang.reflect.Constructor
import java.lang.reflect.Type

/**
 * [com.mingliqiye.document.annotation.DocumentCreator] 构造参数元数据。
 *
 * @property index 构造参数位置（从 0 开始）
 * @property javaName 反射可见的 Java 参数名；未保留参数名时为 `null`
 * @property documentName 文档字段名：显式命名的翻译结果，或继承自字段/getter 元数据、
 *   类级 [com.mingliqiye.document.annotation.DocumentNaming] 与全局命名策略的结果
 * @property explicitDocumentName 由参数上显式标注（[com.mingliqiye.document.annotation.DocumentName]
 *   或参数级 [com.mingliqiye.document.annotation.DocumentNaming]）得到的文档字段名；
 *   未标注时为 `null`，序列化时用于保证读写字段名对称
 * @property type 参数泛型类型
 * @property ignored 参数对应的 record 组件被 `@DocumentIgnore` 标记：不参与文档字段
 *   匹配，构造时始终使用 Java 默认值
 */
internal class BeanCreatorParameter(
    val index: Int,
    val javaName: String?,
    val documentName: String,
    val explicitDocumentName: String?,
    val type: Type,
    val ignored: Boolean = false,
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

    /** 构造参数可接受的文档字段名集合；忽略参数不参与匹配。 */
    val documentNames: Set<String> = parameters
        .filterNot { it.ignored }
        .mapTo(LinkedHashSet()) { it.documentName }

    private val parametersByDocumentName: Map<String, BeanCreatorParameter> =
        parameters.filterNot { it.ignored }.associateBy { it.documentName }

    private val parametersByJavaName: Map<String, BeanCreatorParameter> =
        parameters
            .filterNot { it.ignored }
            .mapNotNull { parameter -> parameter.javaName?.let { it to parameter } }
            .toMap()

    /** 按文档字段名查找构造参数。 */
    fun parameter(documentName: String): BeanCreatorParameter? = parametersByDocumentName[documentName]

    /** 按 Java 参数名查找构造参数；参数名未保留时返回 `null`。 */
    fun parameterByJavaName(javaName: String): BeanCreatorParameter? = parametersByJavaName[javaName]

    /** 创建与参数个数一致的实参数组。 */
    fun newArguments(): Array<Any?> = arrayOfNulls(argumentCount)
}
