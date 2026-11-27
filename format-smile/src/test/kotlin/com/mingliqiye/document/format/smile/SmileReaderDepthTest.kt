package com.mingliqiye.document.format.smile

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.smile.exception.SmileParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SmileReaderDepthTest {

    @Test
    fun maxDepthRejectsNestedDocument() {
        val node = ArrayNode.of(ArrayNode.of(ArrayNode.of(ArrayNode.of(StringNode.of("deep")))))
        val bytes = SmileWriter().write(node)

        assertFailsWith<SmileParseException> {
            SmileReader(bytes, maxDepth = 3).read()
        }
        SmileReader(bytes, maxDepth = 4).read()
    }
}
