package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DocumentMapperConfig] 字段级配置测试。
 */
class DocumentMapperConfigTest {

    @Test
    fun fieldConverterFormatsAndParsesOnlyThatField() {
        val converter = ConfigLabelConverter()
        val config = DocumentMapperConfig.builder()
            .field(ConfigHolder::class.java, "label", converter)
            .build()
        val mapper = JsonMapper(config)

        val holder = ConfigHolder().apply {
            label = "v"
            other = "plain"
        }
        val node = mapper.toNode(holder).asObject()
        assertEquals("cfg:v", node["label"]?.asString())
        assertEquals("plain", node["other"]?.asString())

        val restored = mapper.parse(
            """{"label":"cfg:v","other":"plain"}""",
            ConfigHolder::class.java,
        )
        assertEquals("v", restored.label)
        assertEquals("plain", restored.other)
        assertEquals(ConfigHolder::class.java, converter.property?.declaringClass)
        assertEquals("label", converter.property?.name)
    }

    @Test
    fun builderAcceptsConfig() {
        val converter = ConfigLabelConverter()
        val config = DocumentMapperConfig.builder()
            .field(ConfigHolder::class.java, "label", converter)
            .build()
        val mapper = JsonMapper.builder().config(config).build()

        val node = mapper.toNode(ConfigHolder().apply { label = "v" }).asObject()
        assertEquals("cfg:v", node["label"]?.asString())
    }

    private class ConfigHolder {
        var label: String? = null
        var other: String? = null
    }

    private class ConfigLabelConverter : JsonConverter<String> {

        var property: DocumentProperty? = null

        override fun getType(): DocumentTypeReference<String> = DocumentTypeReference.of()

        override fun serialize(value: String, context: SerializationContext): Node = StringNode.of("cfg:$value")

        override fun deserialize(node: Node, context: DeserializationContext): String {
            property = context.property
            return node.asString().removePrefix("cfg:")
        }
    }
}
