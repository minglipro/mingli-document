package com.mingliqiye.document.format.csv

import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.JsonDeserializer
import com.mingliqiye.document.serialization.JsonSerializer
import com.mingliqiye.document.serialization.ObjectInstantiator
import com.mingliqiye.document.serialization.PropertyMetadataResolver

/**
 * CSV 文档映射器。
 *
 * CSV 只表达平坦表格：根节点为数组，每个元素是一行对象；写出时根对象被当作单行。
 * 分隔符与表头由 [CsvConfig] 控制。
 */
public open class CsvMapper internal constructor(
    serializers: Map<Class<*>, JsonSerializer<*>>,
    deserializers: Map<Class<*>, JsonDeserializer<*>>,
    objectInstantiator: ObjectInstantiator?,
    propertyMetadataResolver: PropertyMetadataResolver?,
    config: DocumentMapperConfig,
) : DocumentMapper(
    "csv",
    { source ->
        val csvConfig = config as? CsvConfig
        CsvReader(
            source,
            delimiter = csvConfig?.delimiter ?: DEFAULT_CSV_DELIMITER,
            header = csvConfig?.header ?: DEFAULT_CSV_HEADER,
        ).read()
    },
    { node, _ ->
        val csvConfig = config as? CsvConfig
        CsvWriter(
            delimiter = csvConfig?.delimiter ?: DEFAULT_CSV_DELIMITER,
            header = csvConfig?.header ?: DEFAULT_CSV_HEADER,
        ).write(node)
    },
    serializers,
    deserializers,
    objectInstantiator,
    propertyMetadataResolver,
    config,
) {

    /**
     * 创建默认 CSV 映射器。
     */
    public constructor() : this(emptyMap(), emptyMap(), null, null, DocumentMapperConfig.empty())

    /**
     * 使用通用 Mapper 配置创建映射器。
     *
     * @param config 通用 Mapper 配置
     */
    public constructor(config: DocumentMapperConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 使用 CSV 配置创建映射器。
     *
     * @param config CSV 配置
     */
    public constructor(config: CsvConfig) : this(emptyMap(), emptyMap(), null, null, config)

    /**
     * 供格式专用 Mapper 子类使用的构造器。
     */
    protected constructor(objectInstantiator: ObjectInstantiator?) :
        this(emptyMap(), emptyMap(), objectInstantiator, null, DocumentMapperConfig.empty())

    public companion object {

        /**
         * 创建 CSV Mapper 构建器。
         */
        @JvmStatic
        public fun builder(): CsvMapperBuilder = CsvMapperBuilder()
    }
}
