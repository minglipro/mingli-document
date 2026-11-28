package com.mingliqiye.document.format.properties

/**
 * Properties 数组下标标记（起始 + 结束字符串）。
 *
 * 对齐 上游实现 `JavaPropsSchema.indexMarker()` / `Markers`：读取时识别
 * `start + index + end` 形式的路径段（默认 `[1]`），写出时在启用
 * [PropertiesWriteFeature.WRITE_INDEX_USING_MARKERS] 后使用同样标记。
 *
 * 传入 `null`（或调用 `withoutIndexMarker()`）可关闭下标标记；标记起止不能为空。
 */
public class PropertiesIndexMarker private constructor(
    /** 起始标记，例如 `[`。 */
    public val start: String,
    /** 结束标记，例如 `]`。 */
    public val end: String,
) {

    init {
        require(start.isNotEmpty()) { "index marker start must not be empty" }
        require(end.isNotEmpty()) { "index marker end must not be empty" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is PropertiesIndexMarker) {
            return false
        }
        return start == other.start && end == other.end
    }

    override fun hashCode(): Int = 31 * start.hashCode() + end.hashCode()

    override fun toString(): String = "PropertiesIndexMarker(start='$start', end='$end')"

    public companion object {

        /**
         * 默认下标标记：`[` 与 `]`（上游实现 默认）。
         */
        @JvmField
        public val DEFAULT: PropertiesIndexMarker = PropertiesIndexMarker("[", "]")

        /**
         * 创建下标标记。
         *
         * @param start 起始标记，不能为空
         * @param end 结束标记，不能为空
         * @return 下标标记
         * @throws IllegalArgumentException 标记为空时抛出
         */
        @JvmStatic
        public fun create(start: String, end: String): PropertiesIndexMarker = PropertiesIndexMarker(start, end)
    }
}
