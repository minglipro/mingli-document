package com.mingliqiye.document.registry

import com.mingliqiye.document.core.ObjectNode

/**
 * Schema 兼容性检查钩子。
 *
 * registry 在“subject 已存在最新版本，且待注册内容与最新版本不同”时调用该钩子；
 * 返回 [SchemaCompatibilityResult] 决定是否允许注册新版本。内置策略见
 * [SchemaCompatibilityPolicy]，也可以实现该接口接入自定义规则：
 *
 * ```kotlin
 * val registry = InMemoryDocumentSchemaRegistry { previous, candidate ->
 *     if (candidate.containsKey("x-unsafe")) {
 *         SchemaCompatibilityResult.incompatible("x-unsafe is forbidden")
 *     } else {
 *         SchemaCompatibilityResult.compatible()
 *     }
 * }
 * ```
 *
 * 参数均为规范化 JSON Schema 节点（`DocumentSchemaCodec.encode` 的结果）。
 */
public fun interface SchemaCompatibilityChecker {

    /**
     * 检查候选 Schema 与 subject 最新版本是否兼容。
     *
     * @param previous 最新版本的规范化 Schema 节点
     * @param candidate 待注册 Schema 的规范化节点
     * @return 兼容性结果
     */
    public fun check(previous: ObjectNode, candidate: ObjectNode): SchemaCompatibilityResult
}
