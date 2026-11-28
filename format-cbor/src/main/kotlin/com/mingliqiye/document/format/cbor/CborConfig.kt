package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ByteArrayPool
import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * CBOR Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 CBOR 专属选项。
 *
 * [preserveTags] 开启可选 CBOR tag 保真：
 * - 读取时使用 [com.fasterxml.上游库.dataformat.cbor.CBORParser] 流式递归，保留每个
 *   值的 CBOR tag，并包装为 `{"$cborTag": <tag 整数>, "value": <Node>}`；
 * - 写出时识别该包装对象（恰好两个字段且 tag 为 `>= 0` 的整数），先写 tag 再写 value；
 * - 保真模式下 `CborMapper.parse(ByteArray)` 的 Token 快路径被禁用（回退 Node 解析），
 *   `CborMapper.formatBytes` 也改为 Node（[CborWriter]）写出。
 *
 * 默认关闭，保持原有解析/字节输出行为。
 *
 * [bufferPool] 为可选的输出缓冲区池：设置后 `CborMapper.formatBytes` /
 * `formatAll` / `formatCanonicalBytes` 以及 `Node.toCborBytes` 等内存写出路径会从池
 * 借用中间缓冲区并在完成后归还，减少临时 `byte[]` 分配；返回的 `ByteArray` 仍是
 * 调用方独占的副本。默认 `null`，行为与未池化时完全一致。
 *
 * [readFeatures] / [writeFeatures] 对齐 上游实现 `CBOR 底层库` 的
 * `CBORParser.Feature` / `CBORGenerator.Feature`（上游实现 2.20+；上游实现 3.x 中更名为
 * `CBORReadFeature` / `CBORWriteFeature`），并额外包含通用
 * `STRICT_DUPLICATE_DETECTION`。默认值与 上游实现 2.x 一致：
 * [DEFAULT_READ_FEATURES] 为空，[DEFAULT_WRITE_FEATURES] 只含
 * [CborWriteFeature.WRITE_MINIMAL_INTS]。
 *
 * 包装协议保留 [TAG_FIELD] 与 [VALUE_FIELD] 两个字段名：普通对象若恰好只含这两个
 * 字段且 tag 为整数，会被当作 tag 包装对象。
 */
public class CborConfig internal constructor(
    base: DocumentMapperConfig,
    /** 是否保留 CBOR tag（`{"$cborTag": ..., "value": ...}` 包装协议），默认 `false`。 */
    public val preserveTags: Boolean = false,
    /** 可选的输出缓冲区池，默认 `null`（不池化）。 */
    public val bufferPool: ByteArrayPool? = null,
    /** CBOR 读取特性集合，默认 [DEFAULT_READ_FEATURES]（空，对齐 上游实现 2.x）。 */
    public val readFeatures: Set<CborReadFeature> = DEFAULT_READ_FEATURES,
    /** CBOR 写出特性集合，默认 [DEFAULT_WRITE_FEATURES]（只含最小整数编码）。 */
    public val writeFeatures: Set<CborWriteFeature> = DEFAULT_WRITE_FEATURES,
) : DocumentMapperConfig(base) {

    /** 是否按 CBOR 规范解码 tag 3 负数 bignum。 */
    public val standardNegativeBigIntDecoding: Boolean
        get() = CborReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING in readFeatures

    /** 是否把 `undefined` 暴露为 embedded object（Node 标记对象）。 */
    public val undefinedAsEmbeddedObject: Boolean
        get() = CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT in readFeatures

    /** 是否把 CBOR simple value 暴露为 embedded object（Node 标记对象）。 */
    public val simpleValueAsEmbeddedObject: Boolean
        get() = CborReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT in readFeatures

    /** 是否开启重复 map key 检测。 */
    public val strictDuplicateDetection: Boolean
        get() = CborReadFeature.STRICT_DUPLICATE_DETECTION in readFeatures

    /** 整数是否使用最小长度编码（上游实现 默认开启）。 */
    public val writeMinimalInts: Boolean
        get() = CborWriteFeature.WRITE_MINIMAL_INTS in writeFeatures

    /** 是否写出 CBOR 自描述 tag 头（55799）。 */
    public val writeTypeHeader: Boolean
        get() = CborWriteFeature.WRITE_TYPE_HEADER in writeFeatures

    /** 是否宽松编码非法代理对（替换为 `U+FFFD`）。 */
    public val lenientUtfEncoding: Boolean
        get() = CborWriteFeature.LENIENT_UTF_ENCODING in writeFeatures

    /** 是否生成 CBOR stringref 共享字符串引用。 */
    public val stringReferences: Boolean
        get() = CborWriteFeature.STRINGREF in writeFeatures

    /** 是否把 token 直写路径的 `double` 写成最小长度二进制浮点。 */
    public val writeMinimalDoubles: Boolean
        get() = CborWriteFeature.WRITE_MINIMAL_DOUBLES in writeFeatures

    /** 是否按 CBOR 规范编码 tag 3 负数 bignum。 */
    public val standardNegativeBigIntEncoding: Boolean
        get() = CborWriteFeature.ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING in writeFeatures

    /** 写出时是否对重复字段名抛异常。 */
    public val writeStrictDuplicateDetection: Boolean
        get() = CborWriteFeature.STRICT_DUPLICATE_DETECTION in writeFeatures

    /**
     * 指定读取特性是否开启。
     *
     * @param feature 读取特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: CborReadFeature): Boolean = feature in readFeatures

    /**
     * 指定写出特性是否开启。
     *
     * @param feature 写出特性
     * @return 开启时返回 `true`
     */
    public fun isEnabled(feature: CborWriteFeature): Boolean = feature in writeFeatures

    public companion object {

        /** tag 包装对象中保存 tag 整数的字段名。 */
        public const val TAG_FIELD: String = "\$cborTag"

        /** tag 包装对象中保存实际值的字段名。 */
        public const val VALUE_FIELD: String = "value"

        /** `undefined` 标记对象的字段名（[CborReadFeature.READ_UNDEFINED_AS_EMBEDDED_OBJECT]）。 */
        public const val UNDEFINED_FIELD: String = "\$cborUndefined"

        /** CBOR simple value 标记对象的字段名（[CborReadFeature.READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT]）。 */
        public const val SIMPLE_VALUE_FIELD: String = "\$cborSimpleValue"

        /**
         * 默认读取特性：空集合，对齐 上游实现 2.x（上游实现 3.x 默认开启三个 CBOR
         * 读取特性）。
         */
        public val DEFAULT_READ_FEATURES: Set<CborReadFeature> = emptySet()

        /**
         * 默认写出特性：只开启 [CborWriteFeature.WRITE_MINIMAL_INTS]，对齐 上游实现 2.x。
         */
        public val DEFAULT_WRITE_FEATURES: Set<CborWriteFeature> = setOf(CborWriteFeature.WRITE_MINIMAL_INTS)

        /**
         * 创建 CBOR 配置构建器。
         */
        @JvmStatic
        public fun builder(): CborConfigBuilder = CborConfigBuilder()
    }
}
