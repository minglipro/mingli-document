package com.mingliqiye.document.format.toml

import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.NodeTokenReader
import kotlin.test.Test
import kotlin.test.assertEquals

class TomlTokenReaderTest {

    private val source = """
        a = 1
        b = "x"
        arr = [1.0, 2.5]
        [table]
        c = 1979-05-27T07:32:00Z
    """.trimIndent()

    @Test
    fun nativeTokensMatchNodeTokenReader() {
        val native = TomlTokenReader(source)
        val expected = NodeTokenReader(TomlReader(source).read())
        assertEquals(readAll(expected), readAll(native))
    }

    @Test
    fun emptyDocument() {
        assertEquals(
            listOf("START_OBJECT:null:null", "END_OBJECT:null:null"),
            readAll(TomlTokenReader("")),
        )
    }

    private fun readAll(reader: DocumentTokenReader): List<String> {
        val result = ArrayList<String>()
        while (true) {
            val token = reader.nextToken() ?: break
            result.add("$token:${reader.textValue}:${reader.booleanValue}")
        }
        return result
    }
}
