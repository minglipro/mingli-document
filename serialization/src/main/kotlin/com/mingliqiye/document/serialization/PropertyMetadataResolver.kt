package com.mingliqiye.document.serialization

/**
 * 属性元数据解析器。
 *
 * 用于补充 Java 反射不可见的属性注解，例如 Kotlin 无 use-site target 的
 * 属性注解。返回 `null` 表示没有额外注解。
 */
public fun interface PropertyMetadataResolver {

    /**
     * 解析属性注解。
     *
     * @param type 声明类型
     * @param propertyName Java 属性名
     * @return 额外注解；没有时返回 `null`
     */
    public fun resolve(type: Class<*>, propertyName: String): List<Annotation>?
}
