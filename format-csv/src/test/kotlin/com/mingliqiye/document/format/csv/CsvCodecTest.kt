package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * CSV 读写测试。
 */
class CsvCodecTest {

    @Test
    fun headerRoundTrip() {
        val node = ArrayNode.from(
            listOf(
                ObjectNode.from(
                    linkedMapOf(
                        "name" to StringNode.of("小于"),
                        "age" to NumberNode.of(18),
                        "active" to BooleanNode.of(true),
                    ),
                ),
                ObjectNode.from(
                    linkedMapOf(
                        "name" to StringNode.of("张三"),
                        "age" to NumberNode.of(20),
                        "active" to BooleanNode.of(false),
                    ),
                ),
            ),
        )

        val csv = node.toCsvString()
        val lines = csv.trim().lines()
        assertEquals("name,age,active", lines[0])
        assertEquals("小于,18,true", lines[1])
        assertEquals("张三,20,false", lines[2])

        val restored = CsvReader(csv).read().asArray()
        assertEquals(2, restored.size)
        assertEquals("小于", restored[0].asObject()["name"]?.asString())
        assertEquals(18, restored[0].asObject()["age"]?.asNumber()?.toInt())
        assertEquals(true, restored[0].asObject()["active"]?.asBoolean())
        assertEquals("张三", restored[1].asObject()["name"]?.asString())
    }

    @Test
    fun customDelimiterRoundTrip() {
        val node = ArrayNode.from(
            listOf(
                ObjectNode.from(
                    linkedMapOf(
                        "name" to StringNode.of("小于"),
                        "age" to NumberNode.of(18),
                    ),
                ),
            ),
        )

        val csv = node.toCsvString(delimiter = ';')
        assertEquals("name;age\n小于;18\n", csv)

        val restored = CsvReader(csv, delimiter = ';').read().asArray()
        assertEquals("小于", restored[0].asObject()["name"]?.asString())
        assertEquals(18, restored[0].asObject()["age"]?.asNumber()?.toInt())
    }

    @Test
    fun quotesAndCommasAreEscaped() {
        val value = "a,b\"c"
        val node = ArrayNode.from(listOf(ObjectNode.from(mapOf("text" to StringNode.of(value)))))

        val csv = node.toCsvString()
        assertTrue(csv.contains("\"a,b\"\"c\""))

        val restored = CsvReader(csv).read().asArray()
        assertEquals(value, restored[0].asObject()["text"]?.asString())
    }

    @Test
    fun newlineInCellSurvivesRoundTrip() {
        val value = "line1\nline2"
        val node = ArrayNode.from(listOf(ObjectNode.from(mapOf("text" to StringNode.of(value)))))

        val restored = CsvReader(node.toCsvString()).read().asArray()
        assertEquals(value, restored[0].asObject()["text"]?.asString())
    }

    @Test
    fun numbersAndBooleansAreInferred() {
        val rows = CsvReader("value\n18\n-3.14\ntrue\nfalse\nabc\n").read().asArray()

        assertEquals(5, rows.size)
        assertEquals(18, rows[0].asObject()["value"]?.asNumber()?.toInt())
        assertEquals("-3.14", rows[1].asObject()["value"]?.asNumber()?.raw)
        assertEquals(true, rows[2].asObject()["value"]?.asBoolean())
        assertEquals(false, rows[3].asObject()["value"]?.asBoolean())
        assertEquals("abc", rows[4].asObject()["value"]?.asString())
    }

    @Test
    fun emptySourceReturnsEmptyArray() {
        val empty = CsvReader("").read()
        assertTrue(empty.isArray)
        assertEquals(0, empty.asArray().size)

        assertEquals(0, "".toCsvNode().asArray().size)
        assertEquals(0, "name,age\n".toCsvNode().asArray().size)
    }

    @Test
    fun withoutHeaderColumnsAreNamedByIndex() {
        val rows = CsvReader("1,2\n3,4\n", header = false).read().asArray()

        assertEquals(2, rows.size)
        assertEquals(1, rows[0].asObject()["column1"]?.asNumber()?.toInt())
        assertEquals(2, rows[0].asObject()["column2"]?.asNumber()?.toInt())
        assertEquals(3, rows[1].asObject()["column1"]?.asNumber()?.toInt())

        val csv = ArrayNode.from(rows.asList()).toCsvString(header = false)
        assertEquals("1,2\n3,4\n", csv)
    }

    @Test
    fun headerIsUnionOfAllRowKeys() {
        val node = ArrayNode.from(
            listOf(
                ObjectNode.from(linkedMapOf("a" to NumberNode.of(1), "b" to NumberNode.of(2))),
                ObjectNode.from(linkedMapOf("b" to NumberNode.of(3), "c" to NumberNode.of(4))),
            ),
        )

        val lines = node.toCsvString().trim().lines()
        assertEquals("a,b,c", lines[0])
        assertEquals("1,2,", lines[1])
        assertEquals(",3,4", lines[2])
    }

    @Test
    fun nullValueIsWrittenAsEmptyCell() {
        val node = ArrayNode.from(
            listOf(
                ObjectNode.from(
                    linkedMapOf(
                        "name" to StringNode.of("小于"),
                        "nickname" to NullNode.INSTANCE,
                    ),
                ),
            ),
        )

        val csv = node.toCsvString()
        assertEquals("name,nickname\n小于,", csv.trim())
        assertEquals("", CsvReader(csv).read().asArray()[0].asObject()["nickname"]?.asString())
    }

    @Test
    fun nestedCellValuesAreRejected() {
        val nestedObject = ArrayNode.from(
            listOf(
                ObjectNode.from(mapOf("address" to ObjectNode.from(mapOf("city" to StringNode.of("杭州"))))),
            ),
        )
        assertFailsWith<CsvException> {
            nestedObject.toCsvString()
        }

        val nestedArray = ObjectNode.from(mapOf("tags" to ArrayNode.of(StringNode.of("a"))))
        assertFailsWith<CsvException> {
            nestedArray.toCsvString()
        }
    }

    @Test
    fun invalidRootNodesAreRejected() {
        assertFailsWith<CsvException> {
            StringNode.of("scalar").toCsvString()
        }
        assertFailsWith<CsvException> {
            ArrayNode.from(listOf(StringNode.of("row"))).toCsvString()
        }
    }

    @Test
    fun duplicateHeadersAreRejected() {
        assertFailsWith<CsvParseException> {
            CsvReader("name,name\na,b\n").read()
        }
    }
}
