package com.mingliqiye.document.format.toml

/**
 * TOML 写出特性，对齐 上游实现 `com.fasterxml.上游库.dataformat.toml.TomlWriteFeature`。
 *
 * 上游实现 2.15 / 3.x 均只有 [FAIL_ON_NULL_WRITE] 一个公开写出特性，默认关闭。
 */
public enum class TomlWriteFeature {

    /**
     * 写出 `null` 时是否直接报错，默认关闭。
     *
     * TOML 规范没有 null：关闭（上游实现 默认）时 `null` 写成空字符串 `""`；
     * 开启后任何 `null` 值写出都抛
     * [com.mingliqiye.document.format.toml.exception.TomlException]。
     */
    FAIL_ON_NULL_WRITE,
}
