package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import kotlin.test.Test
import kotlin.test.assertContentEquals

class JsonUtf8StreamWriterTest {

    @Test
    fun compactBytesMatchTextEncoding() {
        val mapper = JsonMapper()
        val values = listOf(
            ObjectNode.of("name" to StringNode.of("小于"), "age" to NumberNode.of(18)),
            ArrayNode.of(StringNode.of("a"), BooleanNode.of(true), StringNode.of("b")),
            ObjectNode.of(
                "escaped" to StringNode.of("quote\" slash\\ newline\n tab\t"),
                "unicode" to StringNode.of("中文😀"),
                "binary" to BinaryNode.of(byteArrayOf(1, 2, 3, 4)),
            ),
        )

        for (value in values) {
            assertContentEquals(
                mapper.format(value).toByteArray(Charsets.UTF_8),
                mapper.formatBytes(value),
            )
        }
    }

    @Test
    fun prettyBytesStillUseTextPath() {
        val mapper = JsonMapper(JsonConfig.builder().pretty(true).build())
        val value = ObjectNode.of("name" to StringNode.of("小于"))

        assertContentEquals(
            mapper.format(value).toByteArray(Charsets.UTF_8),
            mapper.formatBytes(value),
        )
    }
}
