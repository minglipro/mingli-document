package com.mingliqiye.document.format.csv

/**
 * CSV 嵌套值的读写方式。
 *
 * 默认（[CsvConfig.nestedMode] 为 `null`）不支持嵌套：嵌套对象/数组会按原有规则报错，
 * 保持与旧版本一致的行为。通过 [CsvConfigBuilder.nestedMode] 启用其中一种方式，或通过
 * [CsvSchemaBuilder.objectField] / [CsvSchemaBuilder.arrayField] 声明嵌套子 Schema。
 *
 * 两种方式的表达能力不同：
 * - [JSON]：嵌套对象/数组编码为单个 JSON 文本单元格，可表达任意深度、数组与混合结构；
 * - [COLUMNS]：嵌套对象展开为点分列名（如 `address.city`），数组没有列形式，不支持。
 */
public enum class CsvNestedMode {

    /** 嵌套对象/数组编码为 JSON 文本单元格。 */
    JSON,

    /** 嵌套对象展开为点分列名，数组不支持。 */
    COLUMNS,
}
