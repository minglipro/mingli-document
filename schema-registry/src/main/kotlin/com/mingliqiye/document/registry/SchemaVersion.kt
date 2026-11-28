package com.mingliqiye.document.registry

import com.mingliqiye.document.registry.exception.SchemaRegistryException

/**
 * Schema 版本号。
 *
 * 版本以 subject 为单位从 `1` 开始自增；不同 subject 的版本号互不影响。
 *
 * @property value 版本号，必须为正数
 */
public data class SchemaVersion(public val value: Int) {

    init {
        if (value < 1) {
            throw SchemaRegistryException("Schema version must be positive: $value")
        }
    }

    public companion object {

        /**
         * 创建版本号。
         *
         * @param value 版本号
         */
        @JvmStatic
        public fun of(value: Int): SchemaVersion = SchemaVersion(value)
    }
}
