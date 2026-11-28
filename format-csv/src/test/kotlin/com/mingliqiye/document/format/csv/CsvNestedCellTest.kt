package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 无 Schema 场景下的嵌套单元格读写测试（JSON 文本单元格 / 点分列名）。
 */
class CsvNestedCellTest {

    @Test
    fun jsonCellsReadAsNestedNodes() {
        val csv = """
            name,address,tags
            小于,"{""city"":""杭州"",""zip"":310000}","[1,2,3]"
        """.trimIndent() + "\n"

        val rows = CsvReader(csv, nestedMode = CsvNestedMode.JSON).read().asArray()

        assertEquals(1, rows.size)
        val row = rows[0].asObject()
        assertEquals("小于", row["name"]?.asString())
        val address = row["address"]?.asObject()
        assertEquals("杭州", address?.get("city")?.asString())
        assertEquals(310000, address?.get("zip")?.asNumber()?.toInt())
        val tags = row["tags"]?.asArray()
        assertEquals(3, tags?.size)
        assertEquals(1, tags?.get(0)?.asNumber()?.toInt())
        assertEquals(3, tags?.get(2)?.asNumber()?.toInt())
    }

    @Test
    fun jsonCellsWriteAsJsonTextAndRoundTrip() {
        val row = obj(
            "name" to str("小于"),
            "address" to obj("city" to str("杭州"), "zip" to num(310000)),
            "tags" to arr(str("a"), str("b,c"), num(1), BooleanNode.of(true)),
        )

        val csv = arr(row).toCsvString(nestedMode = CsvNestedMode.JSON)
        assertTrue(csv.contains("\"\"city\"\""))
        assertNodesEqual(arr(row), CsvReader(csv, nestedMode = CsvNestedMode.JSON).read())
    }

    @Test
    fun jsonCellsEscapedCorrectlyAndRoundTrip() {
        val tricky = "a,\"b\"\n\t\\值"
        val row = obj("nested" to obj("text" to str(tricky)))

        val csv = arr(row).toCsvString(nestedMode = CsvNestedMode.JSON)
        val restored = CsvReader(csv, nestedMode = CsvNestedMode.JSON).read().asArray()[0].asObject()

        assertEquals(tricky, restored["nested"]?.asObject()?.get("text")?.asString())
    }

    @Test
    fun invalidJsonCellsAreRejected() {
        assertFailsWith<CsvParseException> {
            CsvReader("value\n{\"a\":}\n", nestedMode = CsvNestedMode.JSON).read()
        }
        assertFailsWith<CsvParseException> {
            CsvReader("value\n\"[1,2\"\n", nestedMode = CsvNestedMode.JSON).read()
        }
        assertFailsWith<CsvParseException> {
            CsvReader("value\n{\"a\":1}trailing\n", nestedMode = CsvNestedMode.JSON).read()
        }
    }

    @Test
    fun jsonCellsAreOnlyParsedWhenEnabled() {
        val row = CsvReader("value\n{\"a\":1}\n").read().asArray()[0].asObject()

        assertEquals("{\"a\":1}", row["value"]?.asString())
    }

    @Test
    fun nestedBeansRoundTripThroughJsonCells() {
        val mapper = CsvMapper(CsvConfig.builder().nestedMode(CsvNestedMode.JSON).build())
        val bean = NestedBean().apply {
            name = "小于"
            address = Address().apply { city = "杭州" }
            tags = listOf("a", "b")
        }

        val csv = mapper.format(listOf(bean))
        val restored = mapper.parse(csv, DocumentTypeReference.listType(NestedBean::class.java))

        assertEquals(1, restored.size)
        assertEquals("小于", restored[0].name)
        assertEquals("杭州", restored[0].address?.city)
        assertEquals(listOf("a", "b"), restored[0].tags)
    }

    @Test
    fun dottedHeadersReadAsNestedObjects() {
        val csv = "name,address.city,address.geo.lat,address.geo.lon\n小于,杭州,30.1,120.2\n"

        val row = CsvReader(csv, nestedMode = CsvNestedMode.COLUMNS).read().asArray()[0].asObject()

        assertEquals("小于", row["name"]?.asString())
        val address = row["address"]?.asObject()
        assertEquals("杭州", address?.get("city")?.asString())
        val geo = address?.get("geo")?.asObject()
        assertEquals(30.1, geo?.get("lat")?.asNumber()?.toDouble())
        assertEquals(120.2, geo?.get("lon")?.asNumber()?.toDouble())
    }

    @Test
    fun nestedObjectsWriteAsDottedColumns() {
        val row = obj(
            "name" to str("小于"),
            "address" to obj(
                "city" to str("杭州"),
                "geo" to obj("lat" to num(30), "lon" to num(120)),
            ),
        )

        val lines = arr(row).toCsvString(nestedMode = CsvNestedMode.COLUMNS).trim().lines()

        assertEquals("name,address.city,address.geo.lat,address.geo.lon", lines[0])
        assertEquals("小于,杭州,30,120", lines[1])
    }

    @Test
    fun dottedColumnsRoundTrip() {
        val row = obj(
            "name" to str("小于"),
            "address" to obj("city" to str("杭州"), "geo" to obj("lat" to num(30))),
        )

        val csv = arr(row).toCsvString(nestedMode = CsvNestedMode.COLUMNS)
        assertNodesEqual(arr(row), CsvReader(csv, nestedMode = CsvNestedMode.COLUMNS).read())
    }

    @Test
    fun dottedColumnsFromMixedRowsKeepUnion() {
        val rows = arr(
            obj("a" to obj("b" to num(1))),
            obj("a" to obj("c" to num(2))),
        )

        val csv = rows.toCsvString(nestedMode = CsvNestedMode.COLUMNS)
        assertEquals("a.b,a.c", csv.trim().lines()[0])

        val restored = CsvReader(csv, nestedMode = CsvNestedMode.COLUMNS).read().asArray()
        assertEquals(1, restored[0].asObject()["a"]?.asObject()?.get("b")?.asNumber()?.toInt())
        assertEquals(2, restored[1].asObject()["a"]?.asObject()?.get("c")?.asNumber()?.toInt())
    }

    @Test
    fun conflictingDottedColumnsAreRejected() {
        assertFailsWith<CsvParseException> {
            CsvReader("a,a.b\n1,2\n", nestedMode = CsvNestedMode.COLUMNS).read()
        }
        assertFailsWith<CsvParseException> {
            CsvReader("a..b\n1\n", nestedMode = CsvNestedMode.COLUMNS).read()
        }
        assertFailsWith<CsvException> {
            arr(obj("a" to str("x"), "a.b" to str("y"))).toCsvString(nestedMode = CsvNestedMode.COLUMNS)
        }
        assertFailsWith<CsvException> {
            arr(
                obj("a" to obj("b" to num(1))),
                obj("a" to num(2)),
            ).toCsvString(nestedMode = CsvNestedMode.COLUMNS)
        }
    }

    @Test
    fun nestedArraysAreRejectedInColumnsMode() {
        assertFailsWith<CsvException> {
            arr(obj("a" to arr(num(1)))).toCsvString(nestedMode = CsvNestedMode.COLUMNS)
        }
    }

    @Test
    fun nestedBeansRoundTripThroughDottedColumns() {
        val mapper = CsvMapper(CsvConfig.builder().nestedMode(CsvNestedMode.COLUMNS).build())
        val bean = DottedBean().apply {
            name = "小于"
            address = Address().apply { city = "杭州" }
        }

        val csv = mapper.format(listOf(bean))
        assertEquals(setOf("name", "address.city"), csv.trim().lines()[0].split(",").toSet())

        val restored = mapper.parse(csv, DocumentTypeReference.listType(DottedBean::class.java))
        assertEquals("小于", restored[0].name)
        assertEquals("杭州", restored[0].address?.city)
    }

    private class NestedBean {
        var name: String = ""
        var address: Address? = null
        var tags: List<String> = emptyList()
    }

    private class DottedBean {
        var name: String = ""
        var address: Address? = null
    }

    private class Address {
        var city: String = ""
    }
}
