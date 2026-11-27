package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.yaml.exception.YamlParseException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 多文档 YAML：`---` / `...` 边界、空文档、`YamlMapper` 多文档 API 与往返。
 */
class YamlMultiDocumentTest {

    @Test
    fun readAllReturnsDocumentsInOrder() {
        val nodes = YamlMultiDocumentReader(
            """
            a: 1
            ---
            b: 2
            ---
            c: 3
            """.trimIndent(),
        ).readAll()

        assertEquals(3, nodes.size)
        assertEquals(1, nodes[0].asObject()["a"]?.asNumber()?.toInt())
        assertEquals(2, nodes[1].asObject()["b"]?.asNumber()?.toInt())
        assertEquals(3, nodes[2].asObject()["c"]?.asNumber()?.toInt())
    }

    @Test
    fun readAllSupportsScalarAndSequenceDocuments() {
        val nodes = YamlMultiDocumentReader("--- 1\n--- [1, 2]\n--- text\n").readAll()

        assertEquals(3, nodes.size)
        assertEquals(1, nodes[0].asNumber().toInt())
        assertEquals(2, nodes[1].asArray().size)
        assertEquals("text", nodes[2].asString())
    }

    @Test
    fun emptyDocumentsBecomeNullNodes() {
        val nodes = YamlMultiDocumentReader("---\n---\n").readAll()

        assertEquals(2, nodes.size)
        assertTrue(nodes[0].isNull)
        assertTrue(nodes[1].isNull)
    }

    @Test
    fun explicitEmptyDocumentIsNullNode() {
        val nodes = YamlMultiDocumentReader("---\n").readAll()

        assertEquals(1, nodes.size)
        assertTrue(nodes[0].isNull)
    }

    @Test
    fun trailingEmptyDocumentIsPreserved() {
        val nodes = YamlMultiDocumentReader("a: 1\n---\n").readAll()

        assertEquals(2, nodes.size)
        assertEquals(1, nodes[0].asObject()["a"]?.asNumber()?.toInt())
        assertTrue(nodes[1].isNull)
    }

    @Test
    fun documentEndMarkerSeparatesDocuments() {
        val nodes = YamlMultiDocumentReader("---\na: 1\n...\n---\nb: 2\n...\n").readAll()

        assertEquals(2, nodes.size)
        assertEquals(1, nodes[0].asObject()["a"]?.asNumber()?.toInt())
        assertEquals(2, nodes[1].asObject()["b"]?.asNumber()?.toInt())
    }

    @Test
    fun blankInputHasNoDocuments() {
        assertTrue(YamlMultiDocumentReader("").readAll().isEmpty())
        assertTrue(YamlMultiDocumentReader("  \n# only comment\n").readAll().isEmpty())
    }

    @Test
    fun parseAllCompanionParsesString() {
        val nodes = YamlMultiDocumentReader.parseAll("a: 1\n---\nb: 2\n")

        assertEquals(2, nodes.size)
        assertEquals(1, nodes[0].asObject()["a"]?.asNumber()?.toInt())
    }

    @Test
    fun stringExtensionParsesAllDocuments() {
        val nodes = "a: 1\n---\nb: 2\n".toYamlNodes()

        assertEquals(2, nodes.size)
        assertEquals(2, nodes[1].asObject()["b"]?.asNumber()?.toInt())
    }

    @Test
    fun singleDocumentReaderRejectsMultipleDocuments() {
        assertFailsWith<YamlParseException> {
            YamlReader("a: 1\n---\nb: 2\n").read()
        }
    }

    @Test
    fun mapperParseAllToNodes() {
        val mapper = YamlMapper()

        val nodes = mapper.parseAllToNodes("a: 1\n---\nb: 2\n")

        assertEquals(2, nodes.size)
        assertEquals(1, nodes[0].asObject()["a"]?.asNumber()?.toInt())
        assertEquals(2, nodes[1].asObject()["b"]?.asNumber()?.toInt())
    }

    @Test
    fun mapperParseAllByClass() {
        val mapper = YamlMapper()

        val people = mapper.parseAll("name: A\n---\nname: B\n", Person::class.java)

        assertEquals(listOf("A", "B"), people.map { it.name })
    }

    @Test
    fun mapperParseAllByReifiedType() {
        val mapper = YamlMapper()

        val people = mapper.parseAll<Person>("name: A\n---\nname: B\n")

        assertEquals(listOf("A", "B"), people.map { it.name })
    }

    @Test
    fun mapperFormatAllRoundTrip() {
        val mapper = YamlMapper()

        val text = mapper.formatAll(listOf(linkedMapOf("a" to 1), linkedMapOf("b" to "x")))
        assertTrue(text.contains("---"))

        val nodes = mapper.parseAllToNodes(text)
        assertEquals(2, nodes.size)
        assertEquals(1, nodes[0].asObject()["a"]?.asNumber()?.toInt())
        assertEquals("x", nodes[1].asObject()["b"]?.asString())
    }

    @Test
    fun mapperFormatAllHonorsPrettyConfig() {
        val mapper = YamlMapper(YamlConfig.builder().pretty(true).build())

        val text = mapper.formatAll(listOf(linkedMapOf("a" to 1), linkedMapOf("b" to 2)))
        assertTrue(text.contains("a: 1"))
        assertTrue(text.contains("---"))

        val nodes = mapper.parseAllToNodes(text)
        assertEquals(2, nodes.size)
        assertEquals(1, nodes[0].asObject()["a"]?.asNumber()?.toInt())
        assertEquals(2, nodes[1].asObject()["b"]?.asNumber()?.toInt())
    }

    @Test
    fun mapperFormatAllRoundTripsBeans() {
        val mapper = YamlMapper()
        val people = listOf(Person("A"), Person("B"))

        val nodes = mapper.parseAllToNodes(mapper.formatAll(people))

        assertEquals(2, nodes.size)
        assertEquals("A", nodes[0].asObject()["name"]?.asString())
        assertEquals("B", nodes[1].asObject()["name"]?.asString())
        assertEquals(people.map { it.name }, mapper.parseAll<Person>(mapper.formatAll(people)).map { it.name })
    }

    @Test
    fun formatAllSupportsTrailingNullDocument() {
        val mapper = YamlMapper()

        val nodes = mapper.parseAllToNodes(mapper.formatAll(listOf(linkedMapOf("a" to 1), null)))

        assertEquals(2, nodes.size)
        assertEquals(1, nodes[0].asObject()["a"]?.asNumber()?.toInt())
        assertTrue(nodes[1].isNull)
    }

    @Test
    fun formatAllEmptyInputReturnsEmptyText() {
        val mapper = YamlMapper()

        assertEquals("", mapper.formatAll(emptyList()))
        assertTrue(mapper.parseAllToNodes("").isEmpty())
    }

    @Test
    fun multipleDocumentsWithBinaryRoundTrip() {
        val mapper = YamlMapper()
        val first = byteArrayOf(0, 1, 2, -1)
        val second = byteArrayOf(9)

        val text = mapper.formatAll(listOf(linkedMapOf("data" to first), linkedMapOf("data" to second)))
        val nodes = mapper.parseAllToNodes(text)

        assertEquals(2, nodes.size)
        assertTrue(nodes[0].asObject()["data"]!!.isBinary)
        assertContentEquals(first, nodes[0].asObject()["data"]!!.asBinary())
        assertContentEquals(second, nodes[1].asObject()["data"]!!.asBinary())
    }

    @Test
    fun codecWriteAllEmitsSeparatorsAndRoundTrips() {
        val nodes: List<Node> = listOf(
            ObjectNode.of("a" to StringNode.of("x")),
            ObjectNode.of("b" to StringNode.of("y")),
        )

        val text = YamlCodec.writeAll(nodes, pretty = true)
        assertEquals(1, Regex("(?m)^---").findAll(text).count())
        assertEquals(nodes, YamlCodec.parseAll(text))

        val compact = YamlCodec.writeAll(nodes, pretty = false)
        assertTrue(compact.contains("---"))
        assertEquals(nodes, YamlCodec.parseAll(compact))
    }

    @Test
    fun codecWriteAllSingleDocumentHasNoLeadingSeparator() {
        val nodes: List<Node> = listOf(ObjectNode.of("a" to StringNode.of("x")))

        val text = YamlCodec.writeAll(nodes, pretty = true)

        assertFalse(text.trimStart().startsWith("---"))
        assertEquals(nodes, YamlCodec.parseAll(text))
    }

    @Test
    fun invalidLaterDocumentThrowsParseException() {
        assertFailsWith<YamlParseException> {
            YamlMultiDocumentReader("a: 1\n---\nb: [1\n").readAll()
        }
    }

    @Test
    fun duplicateKeysAcrossDocumentsAreRejected() {
        assertFailsWith<YamlParseException> {
            YamlMultiDocumentReader("a: 1\n---\na: 2\na: 3\n").readAll()
        }
    }

    private class Person(
        var name: String = "",
    )
}
