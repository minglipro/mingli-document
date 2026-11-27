package com.mingliqiye.document.format.xml

import com.mingliqiye.document.serialization.FieldKey
import com.mingliqiye.document.serialization.JsonConverter
import java.util.LinkedHashMap

/**
 * [XmlConfig] 构建器。
 */
public class XmlConfigBuilder {

    private val fieldConverters = LinkedHashMap<FieldKey, JsonConverter<*>>()
    private var rootName = "root"

    /**
     * 为指定字段绑定转换器。
     *
     * @param declaringClass 声明类
     * @param name 属性名
     * @param converter 字段转换器
     * @return 当前构建器
     */
    public fun field(
        declaringClass: Class<*>,
        name: String,
        converter: JsonConverter<*>,
    ): XmlConfigBuilder {
        fieldConverters[FieldKey(declaringClass, name)] = converter
        return this
    }

    /**
     * 设置根元素名。
     */
    public fun rootName(rootName: String): XmlConfigBuilder {
        this.rootName = rootName
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): XmlConfig = XmlConfig(LinkedHashMap(fieldConverters), rootName)
}
