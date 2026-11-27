package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [JsonSerializer] / [JsonDeserializer] / [JsonConverter] 契约测试。
 *
 * 覆盖方向、`getType()` 和 `context.property` 的可用性。
 */
class JsonConverterContractTest {

    @Test
    fun converterExposesTypeAndBothDirections() {
        val converter = LabelConverter()

        assertTrue(JsonSerializer::class.java.isAssignableFrom(JsonConverter::class.java))
        assertTrue(JsonDeserializer::class.java.isAssignableFrom(JsonConverter::class.java))
        assertEquals(Label::class.java, converter.getType().getRawType())
        assertEquals(Label::class.java, converter.getType().type)
    }

    @Test
    fun beanPropertyContextIsAvailableInBothDirections() {
        val converter = LabelConverter()
        val mapper = DocumentMapperApi.builder().addConverter(converter).build()
        val holder = LabelHolder().apply {
            label = Label("v")
            note = "plain"
        }

        val node = mapper.toNode(holder).asObject()
        assertEquals("label_text=v", node["label_text"]?.asString())
        assertEquals("plain", node["note"]?.asString())

        val serializationContext = assertNotNull(converter.serializeContext)
        assertSame(mapper, serializationContext.mapper)
        assertEquals("$.label", serializationContext.path)
        val serializationProperty = assertNotNull(serializationContext.property)
        assertEquals(LabelHolder::class.java, serializationProperty.declaringClass)
        assertEquals("label", serializationProperty.name)
        assertEquals("label_text", serializationProperty.documentName)
        assertEquals(Label::class.java, serializationProperty.type)
        assertEquals(Label::class.java, serializationProperty.getRawType())

        val restored = mapper.fromNode(node, LabelHolder::class.java)
        assertEquals("v", restored.label?.text)
        assertEquals("plain", restored.note)

        val deserializationContext = assertNotNull(converter.deserializeContext)
        assertSame(mapper, deserializationContext.mapper)
        assertEquals("$.label", deserializationContext.path)
        val deserializationProperty = assertNotNull(deserializationContext.property)
        assertEquals("label", deserializationProperty.name)
        assertEquals("label_text", deserializationProperty.documentName)
        assertEquals(Label::class.java, deserializationProperty.getRawType())
    }

    @Test
    fun rootValueHasNoPropertyContext() {
        val converter = LabelConverter()
        val mapper = DocumentMapperApi.builder().addConverter(converter).build()

        val node = mapper.toNode(Label("root"))
        assertEquals("?=root", node.asString())

        val serializationContext = assertNotNull(converter.serializeContext)
        assertNull(serializationContext.property)
        assertEquals("$", serializationContext.path)

        val restored = mapper.fromNode(node, Label::class.java)
        assertEquals("root", restored.text)

        val deserializationContext = assertNotNull(converter.deserializeContext)
        assertNull(deserializationContext.property)
        assertEquals("$", deserializationContext.path)
    }

    @Test
    fun deserializerMayReturnNullForUnconvertibleNode() {
        val mapper = DocumentMapperApi.builder().addDeserializer(NullReturningDeserializer()).build()

        assertNull(mapper.fromNode(StringNode.of("anything"), Label::class.java))
    }
}

/**
 * 测试用转换目标类型。
 */
private class Label(val text: String)

/**
 * 测试用 Java Bean，字段上带 [DocumentName]。
 */
private class LabelHolder {
    @field:DocumentName("label_text")
    var label: Label? = null

    var note: String? = null
}

/**
 * 记录上下文并把字段名写入文本的双向转换器。
 */
private class LabelConverter : JsonConverter<Label> {

    var serializeContext: SerializationContext? = null
    var deserializeContext: DeserializationContext? = null

    override fun getType(): DocumentTypeReference<Label> = DocumentTypeReference.of()

    override fun serialize(value: Label, context: SerializationContext): Node {
        serializeContext = context
        val name = context.property?.documentName ?: "?"
        return StringNode.of("$name=${value.text}")
    }

    override fun deserialize(node: Node, context: DeserializationContext): Label {
        deserializeContext = context
        val name = context.property?.documentName ?: "?"
        return Label(node.asString().removePrefix("$name="))
    }
}

/**
 * 无法还原时返回 `null` 的反序列化器。
 */
private class NullReturningDeserializer : JsonDeserializer<Label> {
    override fun getType(): DocumentTypeReference<Label> = DocumentTypeReference.of()

    override fun deserialize(node: Node, context: DeserializationContext): Label? = null
}
