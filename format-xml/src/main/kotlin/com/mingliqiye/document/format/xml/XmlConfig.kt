package com.mingliqiye.document.format.xml

import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.FieldKey
import com.mingliqiye.document.serialization.JsonConverter

/**
 * XML Mapper 配置。
 *
 * 在字段级转换配置基础上增加 XML 根元素名。
 */
public class XmlConfig internal constructor(
    fieldConverters: Map<FieldKey, JsonConverter<*>>,
    /** 写出时的根元素名。 */
    public val rootName: String,
) : DocumentMapperConfig(fieldConverters) {

    public companion object {

        /**
         * 创建 XML 配置构建器。
         */
        @JvmStatic
        public fun builder(): XmlConfigBuilder = XmlConfigBuilder()
    }
}
