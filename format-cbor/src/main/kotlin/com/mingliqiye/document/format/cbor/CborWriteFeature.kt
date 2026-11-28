package com.mingliqiye.document.format.cbor

/**
 * CBOR 写出特性，对齐 上游实现 `CBOR 底层库` 的
 * `CBORGenerator.Feature`（上游实现 2.20+，上游实现 3.x 中更名为 `CBORWriteFeature`）。
 *
 * 默认值与 上游实现 2.x 保持一致：[WRITE_MINIMAL_INTS] 默认开启，其余默认关闭。
 * 上游实现 3.x 中 [ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING] 默认开启。
 */
public enum class CborWriteFeature {

    /**
     * 整数使用最小长度编码。
     *
     * 开启（默认）时能放进 1/2/4/8 字节就写最短形式；关闭时按值的类型宽度写定长
     * （int 范围写 4 字节、long 范围写 8 字节，更大的整数仍写 bignum）。
     */
    WRITE_MINIMAL_INTS,

    /**
     * 在文档开头写出 CBOR 自描述 tag（`55799`，字节 `0xD9 0xD9 0xF7`）。
     *
     * 由 上游实现 `CBORFactory` 在创建 generator 时写出，只影响文档首个字节。
     * 默认关闭。
     */
    WRITE_TYPE_HEADER,

    /**
     * 宽松 UTF 编码：字符串中出现非法代理对（unpaired surrogate）时写成
     * Unicode 替换字符 `U+FFFD` 而不是抛异常。
     *
     * 默认关闭（抛 [com.mingliqiye.document.format.cbor.exception.CborException]）。
     */
    LENIENT_UTF_ENCODING,

    /**
     * 生成 CBOR stringref 扩展（共享字符串引用，tag 256 / tag 25）。
     *
     * 默认关闭。开启后输出需由支持 stringref 的读取器解析（上游实现 的 CBOR 读取器
     * 默认支持）。
     */
    STRINGREF,

    /**
     * `double` 尽量写成 4 字节 float（无精度损失时）。
     *
     * 默认关闭。注意：本模块的数字模型以十进制文本为基础，Node 写出与非 token 直写
     * 路径仍使用 CBOR 十进制分数（tag 4）；本特性只在 `CborTokenWriter` 的
     * `writeDouble` 路径（Bean 的 `Double` 字段）生效：开启时写二进制浮点
     * （能省 4 字节则 float32，否则 float64），关闭时沿用模块历史的十进制分数写法。
     */
    WRITE_MINIMAL_DOUBLES,

    /**
     * 按 CBOR 规范（RFC 8949）编码 tag 3 负数 bignum。
     *
     * 关闭时保持 上游实现 2.19 及更早版本的历史编码（把绝对值写成 bignum 内容），
     * 默认关闭；上游实现 3.x 默认开启。与读取端
     * [CborReadFeature.DECODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING] 配套使用可
     * 保证大负数往返一致。
     */
    ENCODE_USING_STANDARD_NEGATIVE_BIGINT_ENCODING,

    /**
     * 写出对象时遇到重复字段名抛异常，默认关闭。
     *
     * 对齐 上游实现 通用生成器的 `JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION`。
     * Bean / Map / Node 序列化不会产生重复字段名；该特性主要约束直接使用
     * [CborTokenWriter] 的调用方。开启后重复字段名抛
     * [com.mingliqiye.document.format.cbor.exception.CborException]。
     */
    STRICT_DUPLICATE_DETECTION,
}
