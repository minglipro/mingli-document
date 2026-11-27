package com.mingliqiye.document.format.avro

import com.mingliqiye.document.format.avro.exception.AvroParseException
import org.apache.avro.file.CodecFactory

/**
 * Avro Object Container File（OCF）支持的压缩 codec。
 *
 * 对应 OCF 头部 `avro.codec` 元数据值；读取时由 `DataFileReader` 依据头部自动
 * 识别，写出时通过 [AvroMapper.formatContainerBytes] /
 * [AvroMapper.appendContainer] 的 codec 参数显式指定。
 *
 * [SNAPPY] 依赖 `snappy-java`（本模块已作为运行时依赖引入，Apache-2.0）；
 * 只支持 [NULL]、[DEFLATE]、[SNAPPY] 三种，其它 codec（如 bzip2、xz、
 * zstandard）在读取时抛
 * [com.mingliqiye.document.format.avro.exception.AvroParseException]。
 *
 * @property codecName OCF 头部 `avro.codec` 元数据值
 */
public enum class AvroCodec(
    public val codecName: String,
) {
    /** 不压缩。 */
    NULL("null"),

    /** Java deflate 压缩。 */
    DEFLATE("deflate"),

    /** Google Snappy 压缩。 */
    SNAPPY("snappy"),
    ;

    internal fun toCodecFactory(): CodecFactory = when (this) {
        NULL -> CodecFactory.nullCodec()
        DEFLATE -> CodecFactory.deflateCodec(CodecFactory.DEFAULT_DEFLATE_LEVEL)
        SNAPPY -> CodecFactory.snappyCodec()
    }

    public companion object {

        /**
         * 按 OCF `avro.codec` 元数据值查找 codec。
         *
         * @param codecName codec 名称，允许首尾空白
         * @return codec；未知名称返回 `null`
         */
        @JvmStatic
        public fun fromCodecName(codecName: String): AvroCodec? {
            val normalized = codecName.trim()
            return entries.firstOrNull { it.codecName == normalized }
        }

        /**
         * 解析 OCF 头部的 codec 名称，缺失（`null`/空串）按 [NULL] 处理。
         *
         * @param codecName OCF 头部 `avro.codec` 元数据值
         * @return 支持的 codec
         * @throws AvroParseException 名称非空且不在支持范围内时抛出
         */
        internal fun fromContainerCodec(codecName: String?): AvroCodec {
            if (codecName.isNullOrEmpty()) {
                return NULL
            }
            return fromCodecName(codecName) ?: throw AvroParseException("Unsupported Avro OCF codec: '$codecName'")
        }
    }
}
