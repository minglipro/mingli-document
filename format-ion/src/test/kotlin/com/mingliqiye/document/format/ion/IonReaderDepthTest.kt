package com.mingliqiye.document.format.ion

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.format.ion.exception.IonParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class IonReaderDepthTest {

    @Test
    fun textAndBinaryDepthLimits() {
        val text = "[[[[1]]]]"
        assertFailsWith<IonParseException> { IonReader(text, maxDepth = 3).read() }
        IonReader(text, maxDepth = 4).read()

        val node = ArrayNode.of(ArrayNode.of(ArrayNode.of(ArrayNode.of(NumberNode.of(1)))))
        val binary = IonBinaryWriter().write(node)
        assertFailsWith<IonParseException> { IonBinaryReader(binary, maxDepth = 3).read() }
        IonBinaryReader(binary, maxDepth = 4).read()
    }
}
