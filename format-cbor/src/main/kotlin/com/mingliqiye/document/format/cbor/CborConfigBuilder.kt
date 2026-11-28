package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ByteArrayPool
import com.mingliqiye.document.serialization.DocumentMapperConfig

/**
 * [CborConfig] 构建器。
 */
public class CborConfigBuilder internal constructor() {

    private var base: DocumentMapperConfig = DocumentMapperConfig.empty()
    private var preserveTags: Boolean = false
    private var bufferPool: ByteArrayPool? = null

    /**
     * 复用通用 Mapper 配置（字段转换器、缺字段策略、深度限制等）。
     *
     * @param config 通用 Mapper 配置
     * @return 当前构建器
     */
    public fun base(config: DocumentMapperConfig): CborConfigBuilder {
        this.base = config
        return this
    }

    /**
     * 是否保留 CBOR tag。
     *
     * @param preserve 是否保留 tag，默认 `false`
     * @return 当前构建器
     */
    public fun preserveTags(preserve: Boolean): CborConfigBuilder {
        this.preserveTags = preserve
        return this
    }

    /**
     * 设置可选的输出缓冲区池。
     *
     * @param pool 缓冲区池；`null` 表示不池化（默认）
     * @return 当前构建器
     */
    public fun bufferPool(pool: ByteArrayPool?): CborConfigBuilder {
        this.bufferPool = pool
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): CborConfig = CborConfig(base, preserveTags, bufferPool)
}
