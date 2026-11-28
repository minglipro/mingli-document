package com.mingliqiye.document.codegen

/**
 * 生成代码使用的 Kotlin 名称校验。
 *
 * 只接受普通标识符与点分包名，不支持反引号标识符；注解参数非法时由 KSP 处理器报错。
 */
internal object DocumentCodegenNames {

    private val identifierPattern = Regex("[A-Za-z_][A-Za-z0-9_]*")

    private val packagePattern = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")

    /**
     * 判断是否为合法 Kotlin 简单标识符。
     *
     * @param value 待校验名称
     * @return 是否合法
     */
    fun isValidIdentifier(value: String): Boolean = identifierPattern.matches(value)

    /**
     * 判断是否为合法 Kotlin 包名。
     *
     * @param value 待校验包名
     * @return 是否合法
     */
    fun isValidPackage(value: String): Boolean = packagePattern.matches(value)
}
