package com.mingliqiye.json.tree

interface JsonNode {
    val isObject: Boolean
        get() = false

    val isArray: Boolean
        get() = false

    val isNumber: Boolean
        get() = false

    val isString: Boolean
        get() = false

    val isNull: Boolean
        get() = false
}
