package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * [NodeTokenReader] 测试。
 */
class NodeTokenReaderTest {

    @Test
    fun readsNodeAsTokenStream() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "data" to BinaryNode.of(byteArrayOf(1, 2, 3)),
        )
        val reader = NodeTokenReader(node)

        assertEquals(DocumentTokenType.START_OBJECT, reader.nextToken())
        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("name", reader.textValue)
        assertEquals(DocumentTokenType.STRING, reader.nextToken())
        assertEquals("小于", reader.textValue)
        assertEquals(DocumentTokenType.FIELD_NAME, reader.nextToken())
        assertEquals("data", reader.textValue)
        assertEquals(DocumentTokenType.BINARY, reader.nextToken())
        assertContentEquals(byteArrayOf(1, 2, 3), reader.binaryValue)
        assertEquals(DocumentTokenType.END_OBJECT, reader.nextToken())
        assertEquals(null, reader.nextToken())
    }
}
