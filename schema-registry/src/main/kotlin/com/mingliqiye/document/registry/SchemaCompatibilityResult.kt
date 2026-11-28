package com.mingliqiye.document.registry

import com.mingliqiye.document.registry.exception.SchemaRegistryException
import java.util.Collections

/**
 * 兼容性检查结果。
 *
 * 通过 [com.mingliqiye.document.registry.SchemaCompatibilityChecker] 返回：
 * [compatible] 为 `true` 时 [violations] 必为空；为 `false` 时 [violations] 列出全部不兼容项。
 */
public class SchemaCompatibilityResult private constructor(
    public val compatible: Boolean,
    violations: List<String>,
) {

    /** 不兼容项文本（不可变副本）。 */
    public val violations: List<String> = Collections.unmodifiableList(ArrayList(violations))

    override fun toString(): String = if (compatible) {
        "SchemaCompatibilityResult(compatible=true)"
    } else {
        "SchemaCompatibilityResult(compatible=false, violations=$violations)"
    }

    public companion object {

        /** 兼容结果。 */
        @JvmStatic
        public fun compatible(): SchemaCompatibilityResult = SchemaCompatibilityResult(true, emptyList())

        /**
         * 不兼容结果。
         *
         * @param violations 不兼容项，不能为空
         */
        @JvmStatic
        public fun incompatible(violations: List<String>): SchemaCompatibilityResult {
            if (violations.isEmpty()) {
                throw SchemaRegistryException("Incompatible compatibility result requires at least one violation")
            }
            return SchemaCompatibilityResult(false, violations)
        }

        /**
         * 不兼容结果。
         *
         * @param violations 不兼容项，不能为空
         */
        @JvmStatic
        public fun incompatible(vararg violations: String): SchemaCompatibilityResult = incompatible(violations.toList())
    }
}
