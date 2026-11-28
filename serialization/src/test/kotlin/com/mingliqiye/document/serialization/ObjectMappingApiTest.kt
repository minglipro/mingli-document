package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 对象映射便捷 API 测试：树 API、值 API、Reader/Writer 包装。
 *
 * 全部基于 [JsonMapper]，覆盖各类来源、泛型目标、null 与异常路径；二进制格式另用
 * 本地 [BinaryObjectMapper] 验证 format-agnostic 行为。
 */
class ObjectMappingApiTest {

    private val mapper = JsonMapper()

    // ------------------------------------------------------------------
    // 树 API
    // ------------------------------------------------------------------

    @Test
    fun readTreeParsesObjectAndArrayRoots() {
        val objectNode = mapper.readTree("""{"name":"小于","age":18,"tags":["a","b"]}""").asObject()
        assertEquals("小于", objectNode["name"]?.asString())
        assertEquals(18, objectNode["age"]?.asNumber()?.toInt())
        assertEquals(2, objectNode["tags"]?.asArray()?.size)

        val arrayNode = mapper.readTree("""[1,true,null,"x"]""").asArray()
        assertEquals(NodeType.ARRAY, arrayNode.type)
        assertEquals(4, arrayNode.size)
        assertEquals(1, arrayNode[0].asNumber().toInt())
        assertTrue(arrayNode[1].asBoolean())
        assertSame(NullNode.INSTANCE, arrayNode[2])
        assertEquals("x", arrayNode[3].asString())
    }

    @Test
    fun readTreeSupportsEverySource() {
        val json = """{"name":"来源","age":3}"""
        val file = Files.createTempFile("object-mapping", ".json").toFile()
        try {
            file.writeText(json)

            assertEquals("来源", mapper.readTree(json).asObject()["name"]?.asString())
            assertEquals("来源", mapper.readTree(json.toByteArray()).asObject()["name"]?.asString())
            assertEquals("来源", mapper.readTree(file).asObject()["name"]?.asString())
            assertEquals("来源", mapper.readTree(file.toPath()).asObject()["name"]?.asString())
            assertEquals(
                "来源",
                mapper.readTree(ByteArrayInputStream(json.toByteArray())).asObject()["name"]?.asString(),
            )
            assertEquals("来源", mapper.readTree(StringReader(json)).asObject()["name"]?.asString())
        } finally {
            file.delete()
        }
    }

    @Test
    fun readTreeMatchesParseToNodeAndKeepsNullNode() {
        val json = """{"a":[1,2],"b":{"c":true}}"""

        assertEquals(mapper.parse(json, Node::class.java), mapper.readTree(json))
        assertSame(NullNode.INSTANCE, mapper.readTree("null"))
    }

    @Test
    fun readTreeReportsMalformedInput() {
        assertFailsWith<JsonParseException> { mapper.readTree("""{"a":}""") }
        assertFailsWith<JsonParseException> { mapper.readTree("[1,2") }
    }

    @Test
    fun writeTreeWritesNodeAsTextAndBytes() {
        val node: Node = ObjectNode.of(
            "a" to NumberNode.of(1),
            "b" to ArrayNode.of(StringNode.of("x"), NullNode.INSTANCE),
        )

        assertEquals(mapper.format(node), mapper.writeTree(node))
        assertEquals("""{"a":1,"b":["x",null]}""", mapper.writeTree(node))
        assertContentEquals(mapper.formatBytes(node), mapper.writeTreeAsBytes(node))
        assertEquals(node, mapper.readTree(mapper.writeTree(node)))
        assertEquals(node, mapper.readTree(mapper.writeTreeAsBytes(node)))
    }

    @Test
    fun valueToTreeMatchesToNode() {
        val user = User().apply {
            name = "小于"
            age = 18
            tags = listOf("a", "b")
        }

        assertEquals(mapper.toNode(user), mapper.valueToTree(user))
        assertSame(NullNode.INSTANCE, mapper.valueToTree(null))
        assertSame(NullNode.INSTANCE, mapper.toNode(null))
    }

    @Test
    fun treeToValueConvertsBeanGenericAndNodeTargets() {
        val userNode = mapper.readTree("""{"name":"小于","age":18,"tags":["a"]}""")

        val user = mapper.treeToValue(userNode, User::class.java)
        assertEquals("小于", user.name)
        assertEquals(18, user.age)
        assertEquals(listOf("a"), user.tags)

        val numbers = mapper.treeToValue(
            mapper.readTree("[1,2,3]"),
            DocumentTypeReference.of<List<Int>>(),
        )
        assertEquals(listOf(1, 2, 3), numbers)

        val sameTree = mapper.treeToValue(userNode, Node::class.java)
        assertEquals(userNode, sameTree)
    }

    @Test
    fun treeToValueReturnsNullForNullTree() {
        val result: User? = mapper.treeToValue(mapper.readTree("null"), User::class.java)

        assertEquals(null, result)
    }

    // ------------------------------------------------------------------
    // 值 API
    // ------------------------------------------------------------------

    @Test
    fun readValueSupportsEverySourceAndGenericTargets() {
        val json = """{"name":"值","age":7}"""
        val file = Files.createTempFile("object-mapping", ".json").toFile()
        try {
            file.writeText(json)

            assertEquals("值", mapper.readValue(json, User::class.java).name)
            assertEquals("值", mapper.readValue(json.toByteArray(), User::class.java).name)
            assertEquals("值", mapper.readValue(file, User::class.java).name)
            assertEquals("值", mapper.readValue(file.toPath(), User::class.java).name)
            assertEquals(
                "值",
                mapper.readValue(ByteArrayInputStream(json.toByteArray()), User::class.java).name,
            )
            assertEquals("值", mapper.readValue(StringReader(json), User::class.java).name)

            assertEquals(
                listOf("a", "b"),
                mapper.readValue("""["a","b"]""", DocumentTypeReference.of<List<String>>()),
            )
            assertEquals(
                listOf(1, 2),
                mapper.readValue(
                    ByteArrayInputStream("[1,2]".toByteArray()),
                    DocumentTypeReference.of<List<Int>>(),
                ),
            )
            assertEquals(
                mapOf("k" to 3),
                mapper.readValue(StringReader("""{"k":3}"""), DocumentTypeReference.of<Map<String, Int>>()),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun readValueReturnsNullForNullDocument() {
        val result: User? = mapper.readValue("null", User::class.java)

        assertEquals(null, result)
    }

    @Test
    fun readValueWrapsMappingFailures() {
        assertFailsWith<DocumentSerializationException> {
            mapper.readValue("""{"age":"not-a-number"}""", User::class.java)
        }
    }

    @Test
    fun writeValueAsStringAndBytesMatchFormat() {
        val user = User().apply {
            name = "写出"
            age = 21
        }

        assertEquals(mapper.format(user), mapper.writeValueAsString(user))
        assertContentEquals(mapper.formatBytes(user), mapper.writeValueAsBytes(user))
        assertEquals("null", mapper.writeValueAsString(null))
        assertEquals("""{"a":1}""", mapper.writeValueAsString(ObjectNode.of("a" to NumberNode.of(1))))
        assertContentEquals(
            mapper.formatBytes(ObjectNode.of("a" to NumberNode.of(1))),
            mapper.writeValueAsBytes(ObjectNode.of("a" to NumberNode.of(1))),
        )
    }

    @Test
    fun updateValueMergesOverridesIntoExistingBean() {
        val user = User().apply {
            name = "原名"
            age = 1
            tags = listOf("keep")
        }

        val merged = mapper.updateValue(user, mapOf("age" to 2))
        assertSame(user, merged)
        assertEquals("原名", user.name)
        assertEquals(2, user.age)
        assertEquals(listOf("keep"), user.tags)

        mapper.updateValue(user, ObjectNode.of("name" to StringNode.of("改名")))
        assertEquals("改名", user.name)
        assertEquals(2, user.age)
    }

    @Test
    fun updateValueRejectsNullOverrides() {
        val user = User().apply { name = "a" }

        assertFailsWith<DocumentSerializationException> {
            mapper.updateValue(user, null)
        }
        assertEquals("a", user.name)
    }

    @Test
    fun convertValueAliasesConvert() {
        val user = User().apply {
            name = "convert"
            age = 5
        }
        val node = mapper.toNode(user)

        assertEquals(user.name, mapper.convertValue(node, User::class.java).name)
        assertEquals(mapper.convert(node, User::class.java).name, mapper.convertValue(node, User::class.java).name)
        assertEquals(
            mapper.convert(node, DocumentTypeReference.of<User>()).age,
            mapper.convertValue(node, DocumentTypeReference.of<User>()).age,
        )
        assertEquals(listOf(1, 2, 3), mapper.convertValue(listOf(1, 2, 3), DocumentTypeReference.of<List<Int>>()))
        assertEquals(NodeType.OBJECT, mapper.convertValue(user, Node::class.java).type)
    }

    // ------------------------------------------------------------------
    // Reader / Writer
    // ------------------------------------------------------------------

    @Test
    fun readerForReadsEverySource() {
        val reader = mapper.readerFor(User::class.java)
        val genericReader = mapper.readerFor(DocumentTypeReference.of<List<Int>>())
        val json = """{"name":"reader","age":9}"""
        val file = Files.createTempFile("object-mapping", ".json").toFile()
        try {
            file.writeText(json)

            assertEquals("reader", reader.readValue(json).name)
            assertEquals("reader", reader.readValue(json.toByteArray()).name)
            assertEquals("reader", reader.readValue(file).name)
            assertEquals("reader", reader.readValue(file.toPath()).name)
            assertEquals("reader", reader.readValue(ByteArrayInputStream(json.toByteArray())).name)
            assertEquals("reader", reader.readValue(StringReader(json)).name)

            assertEquals(listOf(4, 5), genericReader.readValue("[4,5]"))
            assertEquals(listOf(4, 5), genericReader.readValue("[4,5]".toByteArray()))
            assertEquals(listOf(4, 5), genericReader.readValue(StringReader("[4,5]")))
        } finally {
            file.delete()
        }
    }

    @Test
    fun readerForBuildsTypedReader() {
        val listReader = mapper.readerFor(DocumentTypeReference.of<List<String>>())

        assertEquals(listOf("a", "b"), listReader.readValue("""["a","b"]"""))
        assertEquals(listOf("a", "b"), listReader.readValue("""["a","b"]""".toByteArray()))
    }

    @Test
    fun writerWritesEveryTarget() {
        val writer = mapper.writer()
        val user = User().apply {
            name = "writer"
            age = 11
        }
        val file = Files.createTempFile("object-mapping", ".json").toFile()
        try {
            assertEquals(mapper.format(user), writer.writeValueAsString(user))
            assertContentEquals(mapper.formatBytes(user), writer.writeValueAsBytes(user))
            assertEquals(
                "writer",
                mapper.readValue(writer.writeValueAsString(user), User::class.java).name,
            )

            writer.writeValue(user, file)
            assertEquals("writer", mapper.readValue(file, User::class.java).name)
            writer.writeValue(user, file.toPath())
            assertEquals("writer", mapper.readValue(file.toPath(), User::class.java).name)

            val output = ByteArrayOutputStream()
            writer.writeValue(user, output)
            assertEquals("writer", mapper.readValue(output.toByteArray(), User::class.java).name)

            val stringWriter = StringWriter()
            writer.writeValue(user, stringWriter)
            assertEquals("writer", mapper.readValue(StringReader(stringWriter.toString()), User::class.java).name)

            assertContentEquals(
                mapper.formatBytes(user),
                writer.writeValueAsBytes(mapper.readValue(mapper.format(user), Node::class.java)),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun prettyWriterUsesPrettyOutput() {
        val writer = mapper.writerWithDefaultPrettyPrinter()
        val user = User().apply {
            name = "pretty"
            age = 12
            tags = listOf("a", "b")
        }
        val file = Files.createTempFile("object-mapping", ".json").toFile()
        try {
            val prettyText = writer.writeValueAsString(user)
            assertEquals(mapper.formatPretty(user), prettyText)
            assertTrue(prettyText.contains("\n"))
            assertTrue(prettyText.contains("  "))

            val prettyBytes = writer.writeValueAsBytes(user)
            val decoded = String(prettyBytes, Charsets.UTF_8)
            assertTrue(decoded.contains("\n"))
            assertEquals("pretty", mapper.readValue(prettyBytes, User::class.java).name)

            writer.writeValue(user, file)
            assertEquals("pretty", mapper.readValue(file, User::class.java).name)
            writer.writeValue(user, file.toPath())
            assertEquals("pretty", mapper.readValue(file.toPath(), User::class.java).name)

            val output = ByteArrayOutputStream()
            writer.writeValue(user, output)
            assertTrue(String(output.toByteArray(), Charsets.UTF_8).contains("\n"))

            val stringWriter = StringWriter()
            writer.writeValue(user, stringWriter)
            assertEquals(prettyText, stringWriter.toString())

            assertEquals(false, mapper.writer().pretty)
            assertTrue(writer.pretty)
        } finally {
            file.delete()
        }
    }

    @Test
    fun apiImplExposesReaderAndWriterJustLikeDocumentMapper() {
        val api: DocumentMapperApi = mapper

        val reader = api.readerFor(User::class.java)
        assertEquals("api", reader.readValue("""{"name":"api","age":1}""").name)
        assertEquals(
            listOf(1),
            api.readerFor(DocumentTypeReference.of<List<Int>>()).readValue("[1]"),
        )

        val node = api.readTree("""{"name":"api"}""")
        assertEquals("api", node.asObject()["name"]?.asString())
        assertEquals("api", api.treeToValue(node, User::class.java).name)
        assertEquals("""{"name":"api"}""", api.writeTree(node))
        assertContentEquals("""{"name":"api"}""".toByteArray(), api.writeTreeAsBytes(node))
        assertEquals("""{"name":"api"}""", api.writeValueAsString(mapOf("name" to "api")))
        assertFalse(api.writer().pretty)
    }

    // ------------------------------------------------------------------
    // format-agnostic 行为：二进制 Mapper
    // ------------------------------------------------------------------

    @Test
    fun binaryMapperSupportsByteTreeApisAndRejectsTextApis() {
        val binary = BinaryObjectMapper()
        val node = binary.readTree(byteArrayOf(1, 2, 3))

        assertEquals(NodeType.BINARY, node.type)
        assertContentEquals(byteArrayOf(1, 2, 3), node.asBinary())
        assertContentEquals(byteArrayOf(1, 2, 3), binary.writeTreeAsBytes(node))
        assertContentEquals(byteArrayOf(1, 2, 3), binary.writer().writeValueAsBytes(node))
        assertFailsWith<DocumentSerializationException> { binary.readTree("text") }
        assertFailsWith<DocumentSerializationException> { binary.writeTree(node) }
        assertFailsWith<DocumentSerializationException> { binary.writerWithDefaultPrettyPrinter().writeValueAsBytes(node) }
    }

    /**
     * 测试用二进制 Mapper：字节 <-> [BinaryNode]。
     */
    private class BinaryObjectMapper :
        BinaryDocumentMapper(
            "binary-object-mapping-test",
            { source -> BinaryNode.of(source) },
            { node, _ -> node.asBinary() },
            emptyMap(),
            emptyMap(),
            null,
            null,
            DocumentMapperConfig.empty(),
        )

    @Test
    fun nullAndEmptyDocumentBehaviors() {
        assertSame(NullNode.INSTANCE, mapper.readTree("null"))
        assertEquals("null", mapper.writeTree(NullNode.INSTANCE))
        assertEquals(NodeType.OBJECT, mapper.readTree("{}").type)
        assertEquals(0, mapper.readTree("{}").asObject().size)
        assertEquals(0, mapper.readTree("[]").asArray().size)
        assertNotEquals(mapper.readTree("[]"), mapper.readTree("{}"))
    }
}
