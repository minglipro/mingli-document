package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CsvDepthTest {

    @Test
    fun maxNodeDepthRejectsRows() {
        val mapper = CsvMapper(DocumentMapperConfig.builder().maxNodeDepth(1).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("name\nvalue", Map::class.java)
        }

        val two = CsvMapper(DocumentMapperConfig.builder().maxNodeDepth(2).build())
        val error = assertFailsWith<DocumentSerializationException> {
            two.parse("name\nvalue", Map::class.java)
        }
        assertTrue(error.message.orEmpty().contains("maxNodeDepth 2"))
    }

    @Test
    fun defaultMaxDepthAndConfigBuilder() {
        assertEquals(512, CsvConfig.DEFAULT_MAX_DEPTH)
        assertEquals(CsvConfig.DEFAULT_MAX_DEPTH, CsvConfig.builder().build().maxDepth)
        assertEquals(8, CsvConfig.builder().maxDepth(8).build().maxDepth)
        CsvReader("name\nvalue\n").read()
    }

    @Test
    fun fixedRowDepthHonorsMaxDepth() {
        assertFailsWith<CsvParseException> {
            CsvReader("name\nvalue\n", maxDepth = 2).read()
        }
        CsvReader("name\nvalue\n", maxDepth = 3).read()

        val mapper = CsvMapper(CsvConfig.builder().maxDepth(2).build())
        assertFailsWith<CsvParseException> {
            mapper.parse("name\nvalue\n", com.mingliqiye.document.core.DocumentTypeReference.listType(Map::class.java))
        }
    }

    @Test
    fun jsonCellNestingHonorsMaxDepth() {
        val shallow = "v\n\"{ \"\"a\"\": 1 }\"\n"
        val deeper = "v\n\"{ \"\"a\"\": { \"\"b\"\": 1 } }\"\n"

        CsvReader(shallow, nestedMode = CsvNestedMode.JSON, maxDepth = 4).read()
        assertFailsWith<CsvParseException> {
            CsvReader(deeper, nestedMode = CsvNestedMode.JSON, maxDepth = 4).read()
        }
        CsvReader(deeper, nestedMode = CsvNestedMode.JSON, maxDepth = 5).read()
    }

    @Test
    fun columnsModePathDepthHonorsMaxDepth() {
        val csv = "a.b.c\n1\n"
        assertFailsWith<CsvParseException> {
            CsvReader(csv, nestedMode = CsvNestedMode.COLUMNS, maxDepth = 4).read()
        }
        CsvReader(csv, nestedMode = CsvNestedMode.COLUMNS, maxDepth = 5).read()
    }

    @Test
    fun schemaNestedObjectDepthHonorsMaxDepth() {
        val address = CsvSchema.builder("address").string("city").build()
        val schema = CsvSchema.builder("root").objectField("address", address).build()
        val csv = "address.city\n杭州\n"

        CsvSchemaReader(csv, schema, maxDepth = 4).read()
        assertFailsWith<CsvParseException> {
            CsvSchemaReader(csv, schema, maxDepth = 3).read()
        }
        assertFailsWith<CsvParseException> {
            CsvMapper(CsvConfig.builder().schema(schema).maxDepth(3).build())
                .parse(csv, com.mingliqiye.document.core.DocumentTypeReference.listType(Map::class.java))
        }
    }

    @Test
    fun veryDeepJsonCellFailsWithoutStackOverflow() {
        val csv = "v\n" + nestedJsonArray(5000) + "\n"
        assertFailsWith<CsvParseException> {
            CsvReader(csv, nestedMode = CsvNestedMode.JSON).read()
        }
        val mapper = CsvMapper(CsvConfig.builder().nestedMode(CsvNestedMode.JSON).build())
        assertFailsWith<CsvParseException> {
            mapper.parse(csv, Map::class.java)
        }
    }

    @Test
    fun streamPathHonorsMaxDepth() {
        val mapper = CsvMapper(CsvConfig.builder().nestedMode(CsvNestedMode.JSON).maxDepth(4).build())
        val csv = "v\n\"{ \"\"a\"\": { \"\"b\"\": 1 } }\"\n".toByteArray()
        assertFailsWith<CsvParseException> {
            mapper.parseStream(csv.inputStream(), Map::class.java) {}
        }
    }

    @Test
    fun jsonCellWriteDepthIsLimited() {
        val row = arr(obj("v" to nestedArrayNode(600)))
        assertFailsWith<CsvException> {
            row.toCsvString(nestedMode = CsvNestedMode.JSON, maxDepth = 64)
        }
        row.toCsvString(nestedMode = CsvNestedMode.JSON, maxDepth = 0)
    }

    @Test
    fun explicitZeroDisablesLimit() {
        val csv = "v\n" + nestedJsonArray(600) + "\n"
        CsvReader(csv, nestedMode = CsvNestedMode.JSON, maxDepth = 0).read()
        assertFailsWith<CsvParseException> {
            CsvReader(csv, nestedMode = CsvNestedMode.JSON).read()
        }
    }

    @Test
    fun maxDepthValidation() {
        assertFailsWith<IllegalArgumentException> { CsvConfig.builder().maxDepth(0) }
        assertFailsWith<IllegalArgumentException> { CsvConfig.builder().maxDepth(-1) }
    }

    private fun nestedJsonArray(depth: Int): String = "[".repeat(depth) + "1" + "]".repeat(depth)

    private fun nestedArrayNode(depth: Int): Node {
        var node: Node = NumberNode.of(1)
        repeat(depth) { node = ArrayNode.of(node) }
        return node
    }
}
