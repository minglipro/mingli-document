package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 二进制 API 与 ByteArray Bean 映射测试。
 */
class BinaryDocumentMapperTest {

    @Test
    fun binaryMapperSupportsBytesRoundTrip() {
        val mapper = BinaryTestMapper()
        val bytes = byteArrayOf(1, 2, 3, 4)

        assertContentEquals(bytes, mapper.formatBytes(bytes))
        assertContentEquals(bytes, mapper.parse(bytes, ByteArray::class.java))
    }

    @Test
    fun textMapperRejectsBinaryApi() {
        val mapper = JsonMapper()

        assertFailsWith<DocumentSerializationException> {
            mapper.parse(byteArrayOf(1), ByteArray::class.java)
        }
        assertFailsWith<DocumentSerializationException> {
            mapper.formatBytes("value")
        }
    }

    @Test
    fun byteArrayBeanUsesBase64OnJson() {
        val mapper = JsonMapper()
        val holder = ByteArrayHolder().apply { data = byteArrayOf(1, 2, 3) }

        val text = mapper.format(holder)
        assertTrue(text.contains("data"))
        val restored = mapper.parse(text, ByteArrayHolder::class.java)
        assertContentEquals(byteArrayOf(1, 2, 3), restored.data)
    }

    private class ByteArrayHolder {
        var data: ByteArray? = null
    }

    private class BinaryTestMapper :
        DocumentMapper(
            "binary-test",
            { throw DocumentSerializationException("text is not supported") },
            { _, _ -> throw DocumentSerializationException("text is not supported") },
            emptyMap(),
            emptyMap(),
            null,
            null,
            DocumentMapperConfig.empty(),
            { source -> BinaryNode.of(source) },
            { node, _ -> node.asBinary() },
        )
}
