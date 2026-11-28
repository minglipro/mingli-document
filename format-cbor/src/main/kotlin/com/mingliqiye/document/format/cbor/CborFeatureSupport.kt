package com.mingliqiye.document.format.cbor

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.dataformat.cbor.CBORFactory
import com.fasterxml.jackson.dataformat.cbor.CBORGenerator
import com.fasterxml.jackson.dataformat.cbor.CBORParser
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import java.util.concurrent.ConcurrentHashMap

/**
 * CBOR 特性与 上游实现 Factory 的映射、缓存。
 *
 * 本模块的读取/写出都通过 [CBORFactory] 创建 上游实现 parser/generator，因此特性开关
 * 直接落到 上游实现 的 `CBORParser.Feature` / `CBORGenerator.Feature` /
 * `StreamReadFeature.STRICT_DUPLICATE_DETECTION` 上，不存在「空开关」。
 */
internal object CborFeatureSupport {

    private val READ_FEATURE_MAPPING: Map<CborReadFeature, CBORParser.Feature> = mapOf(
        CborReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING to
            CBORParser.Feature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING,
        CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT to
            CBORParser.Feature.READ_UNDEFINED_AS_EMBEDDED_OBJECT,
        CborReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT to
            CBORParser.Feature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT,
    )

    private val WRITE_FEATURE_MAPPING: Map<CborWriteFeature, CBORGenerator.Feature> = mapOf(
        CborWriteFeature.WRITE_MINIMAL_INTS to CBORGenerator.Feature.WRITE_MINIMAL_INTS,
        CborWriteFeature.WRITE_TYPE_HEADER to CBORGenerator.Feature.WRITE_TYPE_HEADER,
        CborWriteFeature.LENIENT_UTF_ENCODING to CBORGenerator.Feature.LENIENT_UTF_ENCODING,
        CborWriteFeature.STRINGREF to CBORGenerator.Feature.STRINGREF,
        CborWriteFeature.WRITE_MINIMAL_DOUBLES to CBORGenerator.Feature.WRITE_MINIMAL_DOUBLES,
        CborWriteFeature.ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING to
            CBORGenerator.Feature.ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING,
    )

    private val FACTORY_CACHE = ConcurrentHashMap<Int, CBORFactory>()

    /**
     * 返回按读取/写出特性配置好的 [CBORFactory]（相同特性组合复用缓存实例）。
     *
     * [CBORFactory] 是线程安全的，可在多个读取/写出之间共享。
     */
    fun factory(
        readFeatures: Set<CborReadFeature>,
        writeFeatures: Set<CborWriteFeature>,
    ): CBORFactory {
        val key = (readMask(readFeatures) shl 8) or writeMask(writeFeatures)
        return FACTORY_CACHE.computeIfAbsent(key) { buildFactory(readFeatures, writeFeatures) }
    }

    /**
     * 把 `undefined` 映射为可区分的 Node 标记对象。
     */
    fun undefinedNode(): Node = ObjectNode.of(CborConfig.UNDEFINED_FIELD to BooleanNode.of(true))

    /**
     * 把 CBOR simple value 映射为可区分的 Node 标记对象。
     *
     * @param value simple value 的整数编码
     */
    fun simpleValueNode(value: Int): Node = ObjectNode.of(CborConfig.SIMPLE_VALUE_FIELD to NumberNode.of(value))

    private fun buildFactory(
        readFeatures: Set<CborReadFeature>,
        writeFeatures: Set<CborWriteFeature>,
    ): CBORFactory {
        val builder = CBORFactory.builder()
        for ((feature, jacksonFeature) in READ_FEATURE_MAPPING) {
            builder.configure(jacksonFeature, feature in readFeatures)
        }
        for ((feature, jacksonFeature) in WRITE_FEATURE_MAPPING) {
            builder.configure(jacksonFeature, feature in writeFeatures)
        }
        builder.configure(
            StreamReadFeature.STRICT_DUPLICATE_DETECTION,
            CborReadFeature.STRICT_DUPLICATE_DETECTION in readFeatures,
        )
        val factory = builder.build()
        factory.configure(
            JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION,
            CborWriteFeature.STRICT_DUPLICATE_DETECTION in writeFeatures,
        )
        return factory
    }

    private fun readMask(features: Set<CborReadFeature>): Int = features.fold(0) { mask, feature -> mask or (1 shl feature.ordinal) }

    private fun writeMask(features: Set<CborWriteFeature>): Int = features.fold(0) { mask, feature -> mask or (1 shl feature.ordinal) }
}

/**
 * 该读取特性是否需要 Node 读取路径（不能走 Token 直读）。
 *
 * `undefined` / simple value 的 embedded object 无法用 `DocumentTokenType` 表达，
 * 需要在 Node 层使用标记对象，因此开启时禁用 Token 快路径。
 */
internal val CborReadFeature.requiresNodeReader: Boolean
    get() = this == CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT ||
        this == CborReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT
