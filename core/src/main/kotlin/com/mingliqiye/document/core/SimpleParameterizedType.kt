package com.mingliqiye.document.core

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 简单参数化类型实现。
 */
internal class SimpleParameterizedType(
    private val raw: Type,
    private val arguments: Array<Type>,
    private val owner: Type? = null,
) : ParameterizedType {

    override fun getActualTypeArguments(): Array<Type> = arguments.clone()

    override fun getRawType(): Type = raw

    override fun getOwnerType(): Type? = owner

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ParameterizedType) {
            return false
        }
        return raw == other.rawType &&
            owner == other.ownerType &&
            arguments.contentEquals(other.actualTypeArguments)
    }

    override fun hashCode(): Int {
        var result = raw.hashCode()
        result = 31 * result + arguments.contentHashCode()
        result = 31 * result + (owner?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = if (owner == null) {
        "$raw<${arguments.joinToString(",")}>"
    } else {
        "$owner.$raw<${arguments.joinToString(",")}>"
    }
}
