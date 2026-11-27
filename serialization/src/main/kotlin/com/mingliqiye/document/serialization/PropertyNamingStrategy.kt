package com.mingliqiye.document.serialization

/**
 * 字段命名策略。
 *
 * 用于把 Java/Kotlin 属性名转换为文档字段名。通过
 * [DocumentMapperConfigBuilder.namingStrategy] 配置后，
 * [DocumentProperty.documentName] 在属性未标注
 * [com.mingliqiye.document.annotation.DocumentName] 时按该策略转换；
 * 显式注解始终优先。
 *
 * 单词边界规则：驼峰大小写边界、连续大写缩写的末尾（`URLValue` 拆为
 * `URL` + `Value`）、数字与后续大写字母之间，以及 `_`、`-`、空格和 `.`。
 */
public enum class PropertyNamingStrategy {

    /** 保持属性名原样（默认）。 */
    AS_IS,

    /** 转换为 snake_case，例如 `userName` 转为 `user_name`。 */
    SNAKE_CASE,

    /** 转换为 kebab-case，例如 `userName` 转为 `user-name`。 */
    KEBAB_CASE,

    /** 转换为 UpperCamel/PascalCase，例如 `user_name` 转为 `UserName`。 */
    UPPER_CAMEL,

    /** 转换为 lowerCamelCase，例如 `user_name` 转为 `userName`。 */
    LOWER_CAMEL,
    ;

    /**
     * 转换属性名。
     *
     * @param name Java/Kotlin 属性名
     * @return 转换后的文档字段名；空字符串与 [AS_IS] 策略均原样返回
     */
    public fun translate(name: String): String = when (this) {
        AS_IS -> name
        SNAKE_CASE -> splitIntoWords(name).joinToString("_") { it.lowercase() }
        KEBAB_CASE -> splitIntoWords(name).joinToString("-") { it.lowercase() }
        UPPER_CAMEL -> splitIntoWords(name).toUpperCamel()
        LOWER_CAMEL -> splitIntoWords(name).toLowerCamel()
    }
}

/**
 * 按驼峰、缩写与显式分隔符拆分属性名。
 */
private fun splitIntoWords(name: String): List<String> {
    val words = ArrayList<String>()
    val current = StringBuilder()

    fun flush() {
        if (current.isNotEmpty()) {
            words.add(current.toString())
            current.setLength(0)
        }
    }

    for (index in name.indices) {
        val char = name[index]
        if (char == '_' || char == '-' || char == ' ' || char == '.') {
            flush()
            continue
        }
        if (char.isUpperCase() && current.isNotEmpty()) {
            val previous = name[index - 1]
            val next = name.getOrNull(index + 1)
            val lowerOrDigitBefore = !previous.isUpperCase()
            val acronymEnd = previous.isUpperCase() && next != null && next.isLowerCase()
            if (lowerOrDigitBefore || acronymEnd) {
                flush()
            }
        }
        current.append(char)
    }
    flush()
    return words
}

private fun List<String>.toUpperCamel(): String = joinToString("") { word ->
    word.lowercase().replaceFirstChar { it.uppercaseChar() }
}

private fun List<String>.toLowerCamel(): String {
    val upperCamel = toUpperCamel()
    if (upperCamel.isEmpty()) {
        return upperCamel
    }
    return upperCamel[0].lowercaseChar() + upperCamel.substring(1)
}
