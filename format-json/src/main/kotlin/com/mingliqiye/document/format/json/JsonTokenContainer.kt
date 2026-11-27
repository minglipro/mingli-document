package com.mingliqiye.document.format.json

/**
 * JSON Token 读取器中的容器状态。
 */
internal class JsonTokenContainer(
    val array: Boolean,
) {

    var phase: Int = 0
}
