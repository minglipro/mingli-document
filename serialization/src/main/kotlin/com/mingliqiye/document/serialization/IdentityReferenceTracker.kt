package com.mingliqiye.document.serialization

import java.util.IdentityHashMap

/**
 * 序列化期间按对象身份追踪首次出现的 JSON Pointer。
 *
 * 只在 `writeReferences` 开启时使用；由 [DocumentMapper.toNode] 为每次序列化创建，
 * 不跨调用共享。Bean、Map、Collection 与数组在展开前登记；标量、String、Number、
 * Boolean、Enum 与二进制值不会登记，因此也不会被替换成 `$ref`。
 */
internal class IdentityReferenceTracker {

    private val firstPointers = IdentityHashMap<Any, String>()

    /**
     * 返回对象首次出现时的 JSON Pointer；未登记时返回 `null`。
     */
    fun pointerOf(value: Any): String? = firstPointers[value]

    /**
     * 登记对象首次出现的位置。
     *
     * 同一对象重复登记时保留第一次的路径，保证后续引用都指向首次出现处。
     */
    fun register(value: Any, pointer: String) {
        if (!firstPointers.containsKey(value)) {
            firstPointers[value] = pointer
        }
    }
}
