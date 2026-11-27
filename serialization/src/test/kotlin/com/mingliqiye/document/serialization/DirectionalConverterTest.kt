package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentDeserializer
import com.mingliqiye.document.annotation.DocumentSerializer
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 单向 Serializer / Deserializer 注解测试。
 */
class DirectionalConverterTest {

    @Test
    fun documentSerializerAppliesOnlyToWrite() {
        val mapper = JsonMapper()
        val value = SerializedHolder().apply { name = "abc" }

        assertEquals("""{"name":"ABC"}""", mapper.format(value))
    }

    @Test
    fun documentDeserializerAppliesOnlyToRead() {
        val mapper = JsonMapper()
        val value = mapper.parse("""{"name":"ABC"}""", DeserializedHolder::class.java)

        assertEquals("abc", value.name)
    }

    private class SerializedHolder {
        @field:DocumentSerializer(UpperCaseSerializer::class)
        var name: String? = null
    }

    private class DeserializedHolder {
        @field:DocumentDeserializer(LowerCaseDeserializer::class)
        var name: String? = null
    }

    private class UpperCaseSerializer : DocumentBaseSerializer<String> {
        override fun getType(): DocumentTypeReference<String> = DocumentTypeReference.of(String::class.java)

        override fun serialize(value: String, context: SerializationContext): Node = StringNode.of(value.uppercase())
    }

    private class LowerCaseDeserializer : DocumentBaseDeserializer<String> {
        override fun getType(): DocumentTypeReference<String> = DocumentTypeReference.of(String::class.java)

        override fun deserialize(node: Node, context: DeserializationContext): String = node.asString().lowercase()
    }
}
