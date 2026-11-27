package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.cbor.exception.CborParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CborReaderDepthTest {

    @Test
    fun maxDepthRejectsNestedDocument() {
        val node = ArrayNode.of(ArrayNode.of(ArrayNode.of(ArrayNode.of(StringNode.of("deep")))))
        val bytes = node.toCborBytes()

        assertFailsWith<CborParseException> {
            CborReader(bytes, maxDepth = 3).read()
        }
        CborReader(bytes, maxDepth = 4).read()
    }
}
