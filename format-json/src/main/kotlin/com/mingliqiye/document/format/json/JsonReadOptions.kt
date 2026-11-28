package com.mingliqiye.document.format.json

/**
 * JSON 读取特性解析结果。
 *
 * 从 [JsonReadFeature] 集合预计算布尔开关，供 [JsonParser]、[JsonTokenReader] 与
 * [JsonArrayStreamParser] 的解析热路径直接读取，避免每次字符判断都做集合查找。
 *
 * [JsonReadFeature.ALLOW_JAVA_COMMENTS] 与 [JsonReadFeature.STRICT_DUPLICATE_DETECTION]
 * 不在此类中：前者由 [JsonConfig.allowComments] 表达，后者由
 * [JsonConfig.duplicateKeyPolicy] 表达。
 *
 * @param features 显式开启的读取特性集合
 */
internal class JsonReadOptions(features: Set<JsonReadFeature>) {

    /** 原始特性集合，供需要转发给委托解析器的场景使用。 */
    val featureSet: Set<JsonReadFeature> = features

    /** 是否允许 Java/C 风格注释（与历史选项联动）。 */
    val allowJavaComments: Boolean = JsonReadFeature.ALLOW_JAVA_COMMENTS in features

    /** 是否允许 YAML 风格 `#` 行注释。 */
    val allowYamlComments: Boolean = JsonReadFeature.ALLOW_YAML_COMMENTS in features

    /** 是否允许单引号字符串/字段名。 */
    val allowSingleQuotes: Boolean = JsonReadFeature.ALLOW_SINGLE_QUOTES in features

    /** 是否允许未加引号的字段名。 */
    val allowUnquotedFieldNames: Boolean = JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES in features

    /** 是否允许字符串中出现未转义控制字符。 */
    val allowUnescapedControlChars: Boolean = JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS in features

    /** 是否允许任意字符的反斜杠转义。 */
    val allowBackslashEscapingAnyCharacter: Boolean = JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER in features

    /** 是否允许数字前导 0。 */
    val allowLeadingZerosForNumbers: Boolean = JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS in features

    /** 是否允许数字前导正号。 */
    val allowLeadingPlusSignForNumbers: Boolean = JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS in features

    /** 是否允许省略整数部分的小数。 */
    val allowLeadingDecimalPointForNumbers: Boolean = JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS in features

    /** 是否允许省略小数部分的小数。 */
    val allowTrailingDecimalPointForNumbers: Boolean = JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS in features

    /** 是否允许 `NaN` / `Infinity` / `-Infinity` 非数字字面量。 */
    val allowNonNumericNumbers: Boolean = JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS in features

    /** 是否允许数组缺失值。 */
    val allowMissingValues: Boolean = JsonReadFeature.ALLOW_MISSING_VALUES in features

    /** 是否允许尾逗号。 */
    val allowTrailingComma: Boolean = JsonReadFeature.ALLOW_TRAILING_COMMA in features

    /** 是否严格检测重复 key（等价于 [DuplicateKeyPolicy.FAIL]）。 */
    val strictDuplicateDetection: Boolean = JsonReadFeature.STRICT_DUPLICATE_DETECTION in features

    public companion object {

        /** 全部读取特性关闭。 */
        @JvmField
        val EMPTY: JsonReadOptions = JsonReadOptions(emptySet())

        /**
         * 解析出已匹配的非数字字面量。
         *
         * 接受与 上游实现 2.15.3 一致的写法：`NaN`、`Infinity`、`-Infinity`、
         * `+Infinity`、`-INF`、`+INF`；返回规范化后的 `raw` 文本
         * （`NaN` / `Infinity` / `-Infinity`）。
         *
         * @param source 源文本
         * @param position 当前偏移量
         * @return 匹配到的规范文本与长度；未匹配返回 `null`
         */
        fun matchNonNumeric(source: String, position: Int): Pair<String, Int>? {
            val candidates = arrayOf(
                "NaN" to "NaN",
                "Infinity" to "Infinity",
                "+Infinity" to "Infinity",
                "-Infinity" to "-Infinity",
                "+INF" to "Infinity",
                "-INF" to "-Infinity",
            )
            for ((literal, normalized) in candidates) {
                if (source.startsWith(literal, position)) {
                    return normalized to literal.length
                }
            }
            return null
        }
    }
}
