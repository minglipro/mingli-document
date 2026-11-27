package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [DocumentMapperApi] 契约测试。
 *
 * 覆盖 builder 构建、树与实体往返、自定义转换器注册，以及 reified 便捷扩展。
 */
class DocumentMapperApiContractTest {

    @Test
    fun builderConfigurationReturnsSameBuilderInstance() {
        val builder = DocumentMapperApi.builder()

        assertSame(builder, builder.codec(JsonCodec))
        assertSame(builder, builder.codec(null))
        assertSame(builder, builder.addSerializer(TokenSerializer()))
        assertSame(builder, builder.addDeserializer(TokenDeserializer()))
        assertSame(builder, builder.addConverter(TokenConverter()))
    }

    @Test
    fun builderBuildsMapperWithConfiguredCodec() {
        val mapper = DocumentMapperApi.builder().codec(JsonCodec).build()

        assertEquals("""{"a":1}""", mapper.format(mapOf("a" to 1)))
    }

    @Test
    fun builderRegistersBidirectionalConverter() {
        val mapper = DocumentMapperApi.builder().addConverter(TokenConverter()).build()

        assertEquals("#x", mapper.toNode(Token("x")).asString())
        assertEquals("x", mapper.fromNode(StringNode.of("#x"), Token::class.java).value)
    }

    @Test
    fun serializerAndDeserializerRegistrationsAreIndependent() {
        val serializerOnly = DocumentMapperApi.builder().addSerializer(TokenSerializer()).build()
        assertEquals("#t", serializerOnly.toNode(Token("t")).asString())
        assertFailsWith<DocumentSerializationException> {
            serializerOnly.fromNode(StringNode.of("#t"), Token::class.java)
        }

        val deserializerOnly = DocumentMapperApi.builder().addDeserializer(TokenDeserializer()).build()
        assertEquals("u", deserializerOnly.fromNode(StringNode.of("#u"), Token::class.java).value)
        val beanNode = deserializerOnly.toNode(Token("u")).asObject()
        assertEquals("u", beanNode["value"]?.asString())
    }

    @Test
    fun toNodeMapsScalarsCollectionsAndBeans() {
        val mapper = DocumentMapperApi.builder().build()

        assertSame(NullNode.INSTANCE, mapper.toNode(null))
        assertEquals(NodeType.STRING, mapper.toNode("text").type)
        assertEquals("text", mapper.toNode("text").asString())
        assertEquals(42, mapper.toNode(42).asNumber().toInt())
        assertTrue(mapper.toNode(true).asBoolean())
        assertEquals("ACTIVE", mapper.toNode(ContractStatus.ACTIVE).asString())

        val arrayNode = mapper.toNode(listOf(1, "two")).asArray()
        assertEquals(2, arrayNode.size)
        assertEquals(1, arrayNode[0].asNumber().toInt())
        assertEquals("two", arrayNode[1].asString())

        val objectNode = mapper.toNode(mapOf("key" to "value")).asObject()
        assertEquals("value", objectNode["key"]?.asString())

        val beanNode = mapper.toNode(
            AccountBean().apply {
                id = 5
                name = "name"
            },
        ).asObject()
        assertEquals(5, beanNode["id"]?.asNumber()?.toInt())
        assertEquals("name", beanNode["name"]?.asString())

        val existingNode: Node = ObjectNode.of("a" to NumberNode.of(1))
        assertSame(existingNode, mapper.toNode(existingNode))
    }

    @Test
    fun fromNodeBuildsBeansAndGenericContainers() {
        val mapper = DocumentMapperApi.builder().build()
        val beanNode = ObjectNode.of(
            "id" to NumberNode.of(9),
            "name" to StringNode.of("小于"),
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "scores" to ObjectNode.of("math" to NumberNode.of(100)),
        )

        val bean = mapper.fromNode(beanNode, AccountBean::class.java)
        assertEquals(9, bean.id)
        assertEquals("小于", bean.name)
        assertEquals(listOf("a", "b"), bean.tags)
        assertEquals(mapOf("math" to 100), bean.scores)
        assertSame(beanNode, mapper.fromNode(beanNode, Node::class.java))

        val list = mapper.fromNode(
            ArrayNode.of(NumberNode.of(1), NumberNode.of(2)),
            DocumentTypeReference.of<List<Int>>(),
        )
        assertEquals(listOf(1, 2), list)

        val map = mapper.fromNode(
            ObjectNode.of("one" to NumberNode.of(1)),
            DocumentTypeReference.of<Map<String, Int>>(),
        )
        assertEquals(mapOf("one" to 1), map)
    }

    @Test
    fun toNodeFromNodeRoundTripKeepsBeanValues() {
        val mapper = DocumentMapperApi.builder().build()
        val source = AccountBean().apply {
            id = 11
            name = "round"
            tags = listOf("x", "y")
            scores = mapOf("a" to 1, "b" to 2)
        }

        val restored = mapper.fromNode(mapper.toNode(source), AccountBean::class.java)
        assertEquals(source.id, restored.id)
        assertEquals(source.name, restored.name)
        assertEquals(source.tags, restored.tags)
        assertEquals(source.scores, restored.scores)
    }

    @Test
    fun codecBackedTextApisUseConfiguredCodec() {
        val mapper = DocumentMapperApi.builder().codec(JsonCodec).build()
        val source = AccountBean().apply {
            id = 3
            name = "json"
            tags = listOf("a")
            scores = mapOf("m" to 1)
        }

        val formatted = mapper.format(source)
        assertTrue(formatted.startsWith("{"))
        assertTrue(formatted.endsWith("}"))
        assertFalse(formatted.contains("\n"))

        val restored = mapper.parse(formatted, AccountBean::class.java)
        assertEquals(3, restored.id)
        assertEquals("json", restored.name)
        assertEquals(listOf("a"), restored.tags)
        assertEquals(mapOf("m" to 1), restored.scores)

        val pretty = mapper.formatPretty(source)
        assertTrue(pretty.contains("\n"))

        val list: List<Int> = mapper.parse("[1,2,3]", DocumentTypeReference.of<List<Int>>())
        assertEquals(listOf(1, 2, 3), list)
    }

    @Test
    fun textApisFailWithoutCodec() {
        val mapper = DocumentMapperApi.builder().build()
        val bean = AccountBean()

        assertFailsWith<DocumentSerializationException> { mapper.format(bean) }
        assertFailsWith<DocumentSerializationException> { mapper.formatPretty(bean) }
        assertFailsWith<DocumentSerializationException> { mapper.parse("{}", AccountBean::class.java) }
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("[]", DocumentTypeReference.of<List<Int>>())
        }
    }

    @Test
    fun convertUsesDocumentTreeAsIntermediate() {
        val mapper = DocumentMapperApi.builder().build()
        val source = AccountBean().apply {
            id = 1
            name = "a"
            tags = listOf("x")
            scores = mapOf("k" to 2)
        }
        val node = mapper.toNode(source)

        val converted = mapper.convert(node, AccountBean::class.java)
        assertEquals(source.id, converted.id)
        assertEquals(source.name, converted.name)
        assertEquals(source.tags, converted.tags)

        assertEquals(node, mapper.convert(source, Node::class.java))
        val typed: AccountBean = mapper.convert(source, DocumentTypeReference.of<AccountBean>())
        assertEquals(source.id, typed.id)
    }

    @Test
    fun reifiedHelpersDelegateToDocumentTypeReference() {
        val mapper = DocumentMapperApi.builder().codec(JsonCodec).build()
        val node = ObjectNode.of("id" to NumberNode.of(7), "name" to StringNode.of("seven"))

        with(DocumentMapperApi) {
            val bean: AccountBean = mapper.fromNode(node)
            assertEquals(7, bean.id)
            assertEquals("seven", bean.name)

            val parsed: AccountBean = mapper.parse("""{"id":8,"name":"eight"}""")
            assertEquals("eight", parsed.name)

            val list: List<Int> = mapper.convert(listOf(1, 2))
            assertEquals(listOf(1, 2), list)
        }
    }
}

/**
 * 测试用 Java Bean。
 */
private class AccountBean {
    var id: Int = 0
    var name: String? = null
    var tags: List<String>? = null
    var scores: Map<String, Int>? = null
}

/**
 * 测试用枚举。
 */
private enum class ContractStatus {
    ACTIVE,
    DISABLED,
}

/**
 * 测试用自定义类型。
 */
private class Token(val value: String)

private class TokenSerializer : JsonSerializer<Token> {
    override fun getType(): DocumentTypeReference<Token> = DocumentTypeReference.of()

    override fun serialize(value: Token, context: SerializationContext): Node = StringNode.of("#${value.value}")
}

private class TokenDeserializer : JsonDeserializer<Token> {
    override fun getType(): DocumentTypeReference<Token> = DocumentTypeReference.of()

    override fun deserialize(node: Node, context: DeserializationContext): Token = Token(node.asString().removePrefix("#"))
}

private class TokenConverter : JsonConverter<Token> {
    override fun getType(): DocumentTypeReference<Token> = DocumentTypeReference.of()

    override fun serialize(value: Token, context: SerializationContext): Node = StringNode.of("#${value.value}")

    override fun deserialize(node: Node, context: DeserializationContext): Token = Token(node.asString().removePrefix("#"))
}
