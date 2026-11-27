package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * CBOR Mapper 配置。
 *
 * 在通用字段/读写配置基础上增加 CBOR 专属选项。
 *
 * [preserveTags] 开启可选 CBOR tag 保真：
 * - 读取时使用 [com.fasterxml.jackson.dataformat.cbor.CBORParser] 流式递归，保留每个
 *   值的 CBOR tag，并包装为 `{"$cborTag": <tag 整数>, "value": <Node>}`；
 * - 写出时识别该包装对象（恰好两个字段且 tag 为 `>= 0` 的整数），先写 tag 再写 value；
 * - 保真模式下 `CborMapper.parse(ByteArray)` 的 Token 快路径被禁用（回退 Node 解析），
 *   `CborMapper.formatBytes` 也改为 Node（[CborWriter]）写出。
 *
 * 默认关闭，保持原有解析/字节输出行为。
 *
 * 包装协议保留 [TAG_FIELD] 与 [VALUE_FIELD] 两个字段名：普通对象若恰好只含这两个
 * 字段且 tag 为整数，会被当作 tag 包装对象。
 */
public class CborConfig internal constructor(
    base: DocumentMapperConfig,
    /** 是否保留 CBOR tag（`{"$cborTag": ..., "value": ...}` 包装协议），默认 `false`。 */
    public val preserveTags: Boolean = false,
) : DocumentMapperConfig(base) {

    public companion object {

        /** tag 包装对象中保存 tag 整数的字段名。 */
        public const val TAG_FIELD: String = "\$cborTag"

        /** tag 包装对象中保存实际值的字段名。 */
        public const val VALUE_FIELD: String = "value"

        /**
         * 创建 CBOR 配置构建器。
         */
        @JvmStatic
        public fun builder(): CborConfigBuilder = CborConfigBuilder()
    }
}
