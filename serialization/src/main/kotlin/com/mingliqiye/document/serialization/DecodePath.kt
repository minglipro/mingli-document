package com.mingliqiye.document.serialization

/**
 * Token 解码期间复用的懒路径构建器。
 *
 * 只在错误或需要回退 Node 时才生成字符串，避免每个字段都分配路径字符串。
 */
internal class DecodePath(root: String) {

    private val builder = StringBuilder(root)

    fun mark(): Int = builder.length

    fun field(name: String) {
        builder.append('.').append(name)
    }

    fun index(index: Int) {
        builder.append('[').append(index).append(']')
    }

    fun literal(text: String) {
        builder.append(text)
    }

    fun truncate(mark: Int) {
        builder.setLength(mark)
    }

    override fun toString(): String = builder.toString()
}
