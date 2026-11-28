package com.mingliqiye.document.format.json

/**
 * JSON 浮点数文本格式化。
 *
 * 使用 JDK 自带的 `Double.toString` 生成可往返的最短十进制表示，避免依赖
 * 第三方库的内部包；非有限值由写出器在调用前拒绝。
 */
internal object JsonDoubleFormatter {

    fun format(value: Double): String = java.lang.Double.toString(value)
}
