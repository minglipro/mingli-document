package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.format.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Token 直连 Bean 解码测试。
 */
class TokenDecodeTest {

    @Test
    fun decodesBeanFromTokenStream() {
        val reader = ListTokenReader(
            listOf(
                DocumentToken.startObject(),
                DocumentToken.fieldName("name"),
                DocumentToken.string("小于"),
                DocumentToken.fieldName("age"),
                DocumentToken.number("18"),
                DocumentToken.fieldName("active"),
                DocumentToken.boolean(true),
                DocumentToken.fieldName("tags"),
                DocumentToken.startArray(),
                DocumentToken.string("a"),
                DocumentToken.string("b"),
                DocumentToken.endArray(),
                DocumentToken.fieldName("address"),
                DocumentToken.startObject(),
                DocumentToken.fieldName("city"),
                DocumentToken.string("杭州"),
                DocumentToken.endObject(),
                DocumentToken.endObject(),
            ),
        )

        val value = JsonMapper().fromTokens(reader, Person::class.java)

        assertEquals("小于", value.name)
        assertEquals(18, value.age)
        assertEquals(true, value.active)
        assertEquals(listOf("a", "b"), value.tags)
        assertEquals("杭州", value.address?.city)
    }

    @Test
    fun decodesListFromTokenStream() {
        val reader = ListTokenReader(
            listOf(
                DocumentToken.startArray(),
                DocumentToken.string("a"),
                DocumentToken.string("b"),
                DocumentToken.endArray(),
            ),
        )

        val value = JsonMapper().fromTokens(reader, DocumentTypeReference.listType(String::class.java))

        assertEquals(listOf("a", "b"), value)
    }

    private class Person {
        var name: String? = null
        var age: Int = 0
        var active: Boolean = false
        var tags: List<String> = emptyList()
        var address: Address? = null
    }

    private class Address {
        var city: String? = null
    }

    private class ListTokenReader(
        private val tokens: List<DocumentToken>,
    ) : DocumentTokenReader {

        private var index = -1
        private var current: DocumentToken? = null

        override val currentToken: DocumentTokenType?
            get() = current?.type

        override val textValue: String?
            get() = current?.text

        override val booleanValue: Boolean?
            get() = current?.booleanValue

        override val binaryValue: ByteArray?
            get() = current?.binaryValue

        override fun nextToken(): DocumentTokenType? {
            index++
            if (index >= tokens.size) {
                current = null
                return null
            }
            current = tokens[index]
            return current?.type
        }
    }
}
