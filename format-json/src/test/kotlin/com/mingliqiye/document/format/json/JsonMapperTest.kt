package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.serialization.DeserializationContext
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.JsonConverter
import com.mingliqiye.document.serialization.SerializationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonMapperTest {

    @Test
    fun mapperInheritsDocumentMapperAndUsesJson() {
        val mapper: DocumentMapper = JsonMapper()

        assertEquals("json", mapper.formatName)
        val node = mapper.toNode(mapOf("name" to "小于"))
        assertEquals("""{"name":"小于"}""", mapper.format(node))
        assertEquals("小于", mapper.parse("""{"name":"小于"}""", Person::class.java).name)
    }

    @Test
    fun jsonConfigControlsCommentsAndPrettyOutput() {
        val strictMapper = JsonMapper(JsonConfig.builder().allowComments(false).build())
        assertFailsWith<JsonParseException> {
            strictMapper.parse("""{"name":"a"} // comment""", Person::class.java)
        }

        val prettyMapper = JsonMapper(JsonConfig.builder().pretty(true).build())
        assertTrue(prettyMapper.format(mapOf("name" to "a")).contains("\n"))
    }

    @Test
    fun builderRegistersConverter() {
        val mapper = JsonMapper.builder()
            .addConverter(MoneyConverter())
            .build()

        val node = mapper.toNode(Money(12))
        assertEquals("12 CNY", node.asString())
    }

    private class Person {
        var name: String = ""
    }

    private data class Money(val amount: Int)

    private class MoneyConverter : JsonConverter<Money> {
        override fun getType(): DocumentTypeReference<Money> = DocumentTypeReference.of()

        override fun serialize(value: Money, context: SerializationContext): Node = StringNode.of("${value.amount} CNY")

        override fun deserialize(node: Node, context: DeserializationContext): Money {
            assertTrue(node.asString().endsWith(" CNY"))
            return Money(node.asString().removeSuffix(" CNY").toInt())
        }
    }
}
