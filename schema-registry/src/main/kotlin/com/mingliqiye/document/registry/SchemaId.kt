package com.mingliqiye.document.registry

import com.mingliqiye.document.registry.exception.SchemaRegistryException

/**
 * Schema 全局 id。
 *
 * id 由 registry 分配，从 `1` 开始自增；相同 Schema 内容（规范化节点相等）
 * 在不同 subject 下复用同一个 id。
 *
 * @property value id，必须为正数
 */
public data class SchemaId(public val value: Long) {

    init {
        if (value < 1L) {
            throw SchemaRegistryException("Schema id must be positive: $value")
        }
    }

    public companion object {

        /**
         * 创建 id。
         *
         * @param value id
         */
        @JvmStatic
        public fun of(value: Long): SchemaId = SchemaId(value)
    }
}
