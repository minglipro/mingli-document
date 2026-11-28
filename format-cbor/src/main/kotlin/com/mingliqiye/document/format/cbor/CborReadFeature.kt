package com.mingliqiye.document.format.cbor

/**
 * CBOR 读取特性，对齐 上游实现 `CBOR 底层库` 的
 * `CBORParser.Feature`（上游实现 2.20+，上游实现 3.x 中更名为 `CBORReadFeature`）
 * 以及通用的 `JsonParser.Feature.STRICT_DUPLICATE_DETECTION`。
 *
 * 默认值与 上游实现 2.x 保持一致（除 [STRICT_DUPLICATE_DETECTION] 为通用特性外，
 * 三个 CBOR 专属读取特性默认全部关闭）。上游实现 3.x 默认全部开启，可通过
 * [CborConfigBuilder.enable] 显式开启。
 *
 * 开启 [READ_UNDEFINED_AS_EMBEDDED_OBJECT] 或 [READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT]
 * 时，`CborMapper.parse(ByteArray)` 的 Token 快路径会被禁用（Node 引用模型需要
 * 表示 embedded object），见 [CborMapper.createTokenReader]。
 */
public enum class CborReadFeature {

    /**
     * 按 CBOR 规范（RFC 8949）解码 tag 3 负数 bignum。
     *
     * 关闭时保持 上游实现 2.19 及更早版本的历史解码（把 bignum 内容按二进制补码
     * 取负），默认关闭；上游实现 3.x 默认开启。
     */
    DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING,

    /**
     * 把 `undefined`（`0xF7`）暴露为 `VALUE_EMBEDDED_OBJECT`，以便与 `null` 区分。
     *
     * Node 映射为标记对象 `{"$cborUndefined": true}`。关闭时 `undefined` 读取为
     * [com.mingliqiye.document.core.NullNode]，默认关闭；上游实现 3.x 默认开启。
     */
    READ_UNDEFINED_AS_EMBEDDED_OBJECT,

    /**
     * 把 CBOR simple value（major type 7）暴露为 `VALUE_EMBEDDED_OBJECT`。
     *
     * Node 映射为标记对象 `{"$cborSimpleValue": <整数>}`。关闭时 simple value 读取
     * 为数字（`NumberNode`），默认关闭；上游实现 3.x 默认开启。
     */
    READ_SIMPLE_VALUE_AS_EMBEDDED_OBJECT,

    /**
     * 通用 上游实现 解析特性：同一 map 内出现重复 key 时抛解析异常。
     *
     * 关闭时保持 上游实现 默认行为（重复 key 后者覆盖前者）。默认关闭。
     */
    STRICT_DUPLICATE_DETECTION,
}
