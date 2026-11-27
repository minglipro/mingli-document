package com.mingliqiye.document.format.properties

/**
 * Properties key path 中的一段：对象字段或数组下标。
 */
internal data class PropertiesPathSegment(
    val key: String? = null,
    val index: Int = -1,
) {

    val isIndex: Boolean
        get() = index >= 0

    companion object {

        fun key(key: String): PropertiesPathSegment = PropertiesPathSegment(key = key)

        fun index(index: Int): PropertiesPathSegment = PropertiesPathSegment(index = index)
    }
}
