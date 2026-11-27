package com.mingliqiye.document.format.xml

/**
 * XML 命名空间名称的 Node 表示模式。
 *
 * - [PREFIX]：保留 `prefix:local` 前缀限定名，`xmlns` 声明作为属性字段保留（默认，兼容原有行为）。
 * - [CLARK]：使用 `{uri}local` Clark 名称表示，URI 内嵌在字段名中；
 *   读取时不保留 `xmlns` 声明字段，写出时自动分配前缀并生成声明。
 */
public enum class XmlNamespaceMode {

    /** 前缀限定名模式（`prefix:local` + `xmlns` 属性字段）。 */
    PREFIX,

    /** Clark 名称模式（`{uri}local`，写出时自动生成前缀与 `xmlns` 声明）。 */
    CLARK,
}
