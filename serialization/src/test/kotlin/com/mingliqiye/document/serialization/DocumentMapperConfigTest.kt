package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonConfig
import com.mingliqiye.document.format.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun missingFieldPolicyControlsDefaults() {
        val keep = JsonMapper(DocumentMapperConfig.builder().build())
        val kept = keep.parse("{}", Defaults::class.java)
        assertEquals("default", kept.name)
        assertEquals(7, kept.count)
        assertEquals(listOf("x"), kept.tags)

        val setNull = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.SET_NULL)
                .build(),
        )
        val nulled = setNull.parse("{}", Defaults::class.java)
        assertNull(nulled.name)
        assertEquals(7, nulled.count)
        assertNull(nulled.tags)

        val setEmpty = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.SET_EMPTY)
                .build(),
        )
        val emptied = setEmpty.parse("{}", Defaults::class.java)
        assertEquals("", emptied.name)
        assertEquals(7, emptied.count)
        assertEquals(emptyList(), emptied.tags)

        val fail = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.FAIL)
                .build(),
        )
        assertFailsWith<com.mingliqiye.document.serialization.exception.DocumentSerializationException> {
            fail.parse("{}", Defaults::class.java)
        }
    }

    @Test
    fun unknownFieldsAndNullWritingAreConfigurable() {
        val failUnknown = JsonMapper(
            DocumentMapperConfig.builder()
                .failOnUnknownFields(true)
                .build(),
        )
        assertFailsWith<com.mingliqiye.document.serialization.exception.DocumentSerializationException> {
            failUnknown.parse("""{"unknown":1}""", Defaults::class.java)
        }

        val nulls = Nully()
        val writeNulls = JsonMapper()
        assertTrue(writeNulls.format(nulls).contains("missing"))

        val ignoreNulls = JsonMapper(
            DocumentMapperConfig.builder()
                .writeNulls(false)
                .build(),
        )
        assertTrue(!ignoreNulls.format(nulls).contains("missing"))
    }

    @Test
    fun formatConfigForwardsCommonOptions() {
        val mapper = JsonMapper(
            JsonConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.SET_EMPTY)
                .writeNulls(false)
                .build(),
        )

        val defaults = mapper.parse("{}", Defaults::class.java)
        assertEquals("", defaults.name)
        assertTrue(!mapper.format(Nully()).contains("missing"))
    }

    private class Defaults {
        var name: String? = "default"
        var count: Int = 7
        var tags: List<String>? = listOf("x")
    }

    private class Nully {
        var present: String? = "x"
        var missing: String? = null
    }

    @Test
    fun unknownFieldsAreRejectedInNodePathToo() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .failOnUnknownFields(true)
                .build(),
        )
        val node = ObjectNode.from(mapOf("unknown" to NumberNode.of(1)))

        assertFailsWith<com.mingliqiye.document.serialization.exception.DocumentSerializationException> {
            mapper.fromNode(node, Defaults::class.java)
        }
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
