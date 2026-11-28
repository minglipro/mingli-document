package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `CsvSchema` 上游实现 格式选项测试：引号/转义/行分隔符/列分隔符/null 文本/
 * 跳过首行/注释/列重排/数组元素分隔符。
 */
class CsvSchemaOptionsTest {

    @Test
    fun customQuoteCharRoundTrip() {
        val schema = CsvSchema.builder("s")
            .string("v")
            .quoteChar('|')
            .build()
        val node = ArrayNode.of(ObjectNode.of("v" to StringNode.of("a,b")))

        val csv = node.toCsvString(schema)
        // 宽松引号检查（上游实现 默认）下，`v` 等小于 minSafeChar 的字符也会触发引号。
        assertEquals("|v|\n|a,b|\n", csv)
        assertEquals("a,b", CsvSchemaReader(csv, schema).read().asArray()[0].asObject()["v"]?.asString())
    }

    @Test
    fun quoteCharCanBeDisabled() {
        val schema = CsvSchema.builder("s")
            .string("a")
            .string("b")
            .quoteChar(null)
            .build()
        val node = ArrayNode.of(
            ObjectNode.of(
                "a" to StringNode.of("x"),
                "b" to StringNode.of("y"),
            ),
        )
        assertEquals("a,b\nx,y\n", node.toCsvString(schema))
        assertFalse(schema.quoteChar != null)
    }

    @Test
    fun schemaColumnSeparatorOverridesConfigDelimiter() {
        val schema = CsvSchema.builder("s")
            .string("a")
            .string("b")
            .columnSeparator(';')
            .build()
        val mapper = CsvMapper(CsvConfig.builder().delimiter(',').schema(schema).build())
        val csv = mapper.format(
            listOf(
                ObjectNode.of(
                    "a" to StringNode.of("x"),
                    "b" to StringNode.of("y"),
                ),
            ),
        )
        assertEquals("a;b\nx;y\n", csv)

        val node = CsvSchemaReader(csv, schema, delimiter = ',').read().asArray()[0].asObject()
        assertEquals("x", node["a"]?.asString())
        assertEquals("y", node["b"]?.asString())
    }

    @Test
    fun customLineSeparatorIsUsedForWriting() {
        val schema = CsvSchema.builder("s")
            .string("a")
            .lineSeparator("\r\n")
            .build()
        val csv = ArrayNode.of(ObjectNode.of("a" to StringNode.of("x"))).toCsvString(schema)
        assertEquals("a\r\nx\r\n", csv)
    }

    @Test
    fun nullValueIsUsedForWritingAndReading() {
        val schema = CsvSchema.builder("s")
            .field("v", SchemaType.STRING, required = false, nullable = true)
            .nullValue("NULL")
            .build()
        val node = ArrayNode.of(ObjectNode.of("v" to NullNode.INSTANCE))

        val csv = node.toCsvString(schema)
        assertEquals("v\nNULL\n", csv)
        assertTrue(CsvSchemaReader(csv, schema).read().asArray()[0].asObject()["v"]!!.isNull)
    }

    @Test
    fun skipFirstDataRowWithHeader() {
        val schema = CsvSchema.builder("s")
            .string("name")
            .skipFirstDataRow(true)
            .build()
        val rows = CsvSchemaReader.parse("name\njunk\n小于\n", schema).asArray()
        assertEquals(1, rows.size)
        assertEquals("小于", rows[0].asObject()["name"]?.asString())
    }

    @Test
    fun skipFirstDataRowWithoutHeader() {
        val schema = CsvSchema.builder("s")
            .string("name")
            .header(false)
            .skipFirstDataRow(true)
            .build()
        val rows = CsvSchemaReader.parse("junk\n小于\n", schema).asArray()
        assertEquals(1, rows.size)
        assertEquals("小于", rows[0].asObject()["name"]?.asString())
    }

    @Test
    fun schemaAllowComments() {
        val schema = CsvSchema.builder("s")
            .string("name")
            .allowComments(true)
            .build()
        val rows = CsvSchemaReader.parse("# comment\nname\n小于\n", schema).asArray()
        assertEquals(1, rows.size)
        assertEquals("小于", rows[0].asObject()["name"]?.asString())
    }

    @Test
    fun columnOrderCanBeReordered() {
        val schema = CsvSchema.builder("s")
            .string("a")
            .string("b")
            .orderColumns("b", "a")
            .build()
        assertEquals(listOf("b", "a"), schema.columnNames)

        val node = ArrayNode.of(ObjectNode.of("a" to StringNode.of("x"), "b" to StringNode.of("y")))
        assertEquals("b,a\ny,x\n", node.toCsvString(schema))
    }

    @Test
    fun sortedByReturnsReorderedSchema() {
        val schema = CsvSchema.builder("s")
            .string("a")
            .string("b")
            .build()
        val sorted = schema.sortedBy("b")
        assertEquals(listOf("b", "a"), sorted.columnNames)
        assertEquals("s", sorted.name)
        assertEquals(listOf("a", "b"), schema.columnNames)
    }

    @Test
    fun addColumnsConvenienceMethods() {
        val schema = CsvSchema.builder("s")
            .addColumn("a")
            .addColumns("b", "c")
            .build()
        assertEquals(listOf("a", "b", "c"), schema.columnNames)
        assertTrue(schema.fields.all { it.type == SchemaType.STRING })
    }

    @Test
    fun arrayElementSeparatorRoundTrip() {
        val element = CsvSchema.builder("tags")
            .string("value")
            .build()
        val schema = CsvSchema.builder("s")
            .string("name")
            .arrayField("tags", element)
            .arrayElementSeparator(";")
            .build()
        val node = ArrayNode.of(
            ObjectNode.of(
                "name" to StringNode.of("小于"),
                "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            ),
        )
        val csv = node.toCsvString(schema)
        assertEquals("name,tags\n小于,a;b\n", csv)

        val row = CsvSchemaReader(csv, schema).read().asArray()[0].asObject()
        assertEquals("小于", row["name"]?.asString())
        val tags = row["tags"]!!.asArray()
        assertEquals(2, tags.size)
        assertEquals("a", tags[0].asString())
        assertEquals("b", tags[1].asString())
    }

    @Test
    fun schemaDefaultsMatchJackson() {
        val schema = CsvSchema.builder("s").string("a").build()
        assertEquals(CsvSchema.DEFAULT_QUOTE_CHAR, schema.quoteChar)
        assertEquals(null, schema.escapeChar)
        assertEquals(CsvSchema.DEFAULT_LINE_SEPARATOR, schema.lineSeparator)
        assertEquals(null, schema.columnSeparator)
        assertEquals(null, schema.arrayElementSeparator)
        assertEquals(null, schema.nullValue)
        assertEquals("", schema.nullValueText)
        assertFalse(schema.skipFirstDataRow)
        assertFalse(schema.allowComments)
        assertTrue(schema.header)
        assertEquals(CsvSchema.DEFAULT_COLUMN_SEPARATOR, schema.effectiveColumnSeparator(CsvSchema.DEFAULT_COLUMN_SEPARATOR))
    }

    @Test
    fun schemaEscapeCharRoundTrip() {
        val schema = CsvSchema.builder("s")
            .string("v")
            .escapeChar('\\')
            .build()
        val node = ArrayNode.of(ObjectNode.of("v" to StringNode.of("a\\b")))
        val csv = node.toCsvString(schema)
        assertEquals("v\n\"a\\\\b\"\n", csv)
        assertEquals("a\\b", CsvSchemaReader(csv, schema).read().asArray()[0].asObject()["v"]?.asString())
    }

    @Test
    fun numericColumnTypeIsPreservedOnWrite() {
        val schema = CsvSchema.builder("s")
            .int("value")
            .build()
        val node = ArrayNode.of(ObjectNode.of("value" to NumberNode.of(18)))
        assertEquals("value\n18\n", node.toCsvString(schema))
    }
}
