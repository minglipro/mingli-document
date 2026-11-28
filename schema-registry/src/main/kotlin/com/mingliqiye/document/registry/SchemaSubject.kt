package com.mingliqiye.document.registry

import com.mingliqiye.document.registry.exception.SchemaRegistryException

/**
 * Schema Registry 中的 subject（逻辑 Schema 名）。
 *
 * subject 是版本演进的逻辑单位：同一 subject 下的 Schema 按版本自增，
 * 跨 subject 的相同 Schema 内容共享全局 id。
 *
 * @property value subject 名，不能为空白
 */
public data class SchemaSubject(public val value: String) {

    init {
        if (value.isBlank()) {
            throw SchemaRegistryException("Schema subject must not be blank")
        }
    }

    public companion object {

        /**
         * 创建 subject。
         *
         * @param value subject 名
         */
        @JvmStatic
        public fun of(value: String): SchemaSubject = SchemaSubject(value)
    }
}
