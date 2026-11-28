package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.csv.exception.CsvParseException
import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentTokenType
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 上游实现 CSV 读写特性对齐测试（`CsvParser.Feature` / `CsvReadFeature` 与
 * `CsvGenerator.Feature` / `CsvWriteFeature`）。
 */
class CsvJacksonFeatureTest {

    // ---------------------------------------------------------------- 读取特性

    @Test
    fun trimSpacesFeatureTrimsUnquotedValuesOnly() {
        val csv = "name,note\n小于, hello \n"
        val defaultRow = CsvReader(csv).read().asArray()[0].asObject()
        assertEquals(" hello ", defaultRow["note"]?.asString())

        val trimmed = CsvReader(csv, readFeatures = withRead(CsvReadFeature.TRIM_SPACES))
            .read()
            .asArray()[0]
            .asObject()
        assertEquals("hello", trimmed["note"]?.asString())

        val quoted = "name,note\n小于,\" hello \"\n"
        val quotedTrimmed = CsvReader(quoted, readFeatures = withRead(CsvReadFeature.TRIM_SPACES))
            .read()
            .asArray()[0]
            .asObject()
        assertEquals(" hello ", quotedTrimmed["note"]?.asString())
    }

    @Test
    fun trimHeaderSpacesIsEnabledByDefault() {
        val csv = " name , age \n小于,18\n"
        val defaultRow = CsvReader(csv).read().asArray()[0].asObject()
        assertEquals("小于", defaultRow["name"]?.asString())
        assertEquals(18, defaultRow["age"]?.asNumber()?.toInt())

        val without = CsvReader(
            csv,
            readFeatures = CsvConfig.DEFAULT_READ_FEATURES - CsvReadFeature.TRIM_HEADER_SPACES,
        ).read().asArray()[0].asObject()
        assertTrue(without.containsKey(" name "))
        assertTrue(without.containsKey(" age "))
    }

    @Test
    fun skipEmptyLinesFeature() {
        val csv = "name,age\n\n小于,18\n"
        assertEquals(2, CsvReader(csv).read().asArray().size)
        val skipped = CsvReader(csv, readFeatures = withRead(CsvReadFeature.SKIP_EMPTY_LINES))
            .read()
            .asArray()
        assertEquals(1, skipped.size)
    }

    @Test
    fun skipEmptyRowsFeature() {
        val csv = "a,b\n,,\n1,2\n"
        assertEquals(2, CsvReader(csv).read().asArray().size)
        val skipped = CsvReader(csv, readFeatures = withRead(CsvReadFeature.SKIP_EMPTY_ROWS))
            .read()
            .asArray()
        assertEquals(1, skipped.size)
    }

    @Test
    fun allowTrailingCommaIsEnabledByDefault() {
        val rows = CsvReader("name,age,\n小于,18,\n").read().asArray()
        assertEquals(1, rows.size)
        assertEquals(setOf("name", "age"), rows[0].asObject().keys)

        val disabled = CsvReader(
            "name,age,\n小于,18,\n",
            readFeatures = CsvConfig.DEFAULT_READ_FEATURES - CsvReadFeature.ALLOW_TRAILING_COMMA,
        ).read().asArray()
        assertTrue(disabled[0].asObject().keys.contains(""))
    }

    @Test
    fun allowCommentsFeature() {
        val csv = "# comment\nname,age\n# another\n小于,18\n"
        val rows = CsvReader(csv, readFeatures = withRead(CsvReadFeature.ALLOW_COMMENTS))
            .read()
            .asArray()
        assertEquals(1, rows.size)
        assertEquals("小于", rows[0].asObject()["name"]?.asString())
        assertEquals(18, rows[0].asObject()["age"]?.asNumber()?.toInt())
    }

    @Test
    fun failOnMissingColumnsFeature() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .int("age", required = false)
            .build()
        val lenient = CsvSchemaReader.parse("name,age\n小于\n", schema).asArray()
        assertEquals(1, lenient.size)

        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse(
                "name,age\n小于\n",
                schema,
                readFeatures = withRead(CsvReadFeature.FAIL_ON_MISSING_COLUMNS),
            )
        }
    }

    @Test
    fun failOnMissingHeaderColumnsIsEnabledByDefault() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .int("age")
            .build()
        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse("name\n小于\n", schema)
        }

        val features = CsvConfig.DEFAULT_READ_FEATURES - CsvReadFeature.FAIL_ON_MISSING_HEADER_COLUMNS
        val rows = CsvSchemaReader.parse("name\n小于\n", schema, readFeatures = features).asArray()
        assertEquals("小于", rows[0].asObject()["name"]?.asString())
        assertFalse(rows[0].asObject().containsKey("age"))
    }

    @Test
    fun insertNullsForMissingColumnsFeature() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .int("age", required = false)
            .build()
        val rows = CsvSchemaReader.parse(
            "name,age\n小于\n",
            schema,
            readFeatures = withRead(CsvReadFeature.INSERT_NULLS_FOR_MISSING_COLUMNS),
        ).asArray()
        val row = rows[0].asObject()
        assertTrue(row.containsKey("age"))
        assertTrue(row["age"]!!.isNull)
    }

    @Test
    fun emptyStringAsNullFeature() {
        val csv = "name,note\n小于,\n"
        val plain = CsvReader(csv).read().asArray()[0].asObject()
        assertEquals("", plain["note"]?.asString())

        val nulled = CsvReader(csv, readFeatures = withRead(CsvReadFeature.EMPTY_STRING_AS_NULL))
            .read()
            .asArray()[0]
            .asObject()
        assertTrue(nulled["note"]!!.isNull)
    }

    @Test
    fun emptyUnquotedStringAsMissingFeature() {
        val csv = "name,note\n小于,\n"
        val row = CsvReader(csv, readFeatures = withRead(CsvReadFeature.EMPTY_UNQUOTED_STRING_AS_MISSING))
            .read()
            .asArray()[0]
            .asObject()
        assertTrue(row.containsKey("name"))
        assertFalse(row.containsKey("note"))
    }

    @Test
    fun schemaNullValueIsReadAsNull() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .string("note", required = false, nullable = true)
            .nullValue("NULL")
            .build()
        val rows = CsvSchemaReader.parse("name,note\n小于,NULL\n", schema).asArray()
        assertTrue(rows[0].asObject()["note"]!!.isNull)

        val featureRows = CsvSchemaReader.parse(
            "name,note\n小于,NULL\n",
            schema,
            readFeatures = withRead(CsvReadFeature.ONLY_UNQUOTED_NULL_VALUES_AS_NULL),
        ).asArray()
        assertTrue(featureRows[0].asObject()["note"]!!.isNull)
    }

    @Test
    fun duplicateHeaderColumnsFeature() {
        assertFailsWith<CsvParseException> {
            CsvReader("name,name\na,b\n").read()
        }
        val rows = CsvReader(
            "name,name\na,b\n",
            readFeatures = CsvConfig.DEFAULT_READ_FEATURES - CsvReadFeature.FAIL_ON_DUPLICATE_HEADER_COLUMNS,
        ).read().asArray()
        assertEquals("b", rows[0].asObject()["name"]?.asString())
    }

    @Test
    fun caseInsensitiveHeadersFeature() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .int("age")
            .build()
        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse("NAME,AGE\n小于,18\n", schema)
        }
        val rows = CsvSchemaReader.parse(
            "NAME,AGE\n小于,18\n",
            schema,
            readFeatures = withRead(CsvReadFeature.CASE_INSENSITIVE_HEADERS),
        ).asArray()
        assertEquals("小于", rows[0].asObject()["name"]?.asString())
        assertEquals(18, rows[0].asObject()["age"]?.asNumber()?.toInt())
    }

    @Test
    fun wrapAsArrayIsModelInherent() {
        assertTrue(CsvReader("a\n1\n").read().isArray)
        assertFalse(CsvReadFeature.WRAP_AS_ARRAY.enabledByDefault)
    }

    // ---------------------------------------------------------------- 写出特性

    @Test
    fun strictCheckForQuotingFeature() {
        val node = ArrayNode.of(ObjectNode.of("text" to StringNode.of("hello world")))
        val loose = node.toCsvString()
        assertEquals("text\n\"hello world\"\n", loose)

        val strict = node.toCsvString(writeFeatures = withWrite(CsvWriteFeature.STRICT_CHECK_FOR_QUOTING))
        assertEquals("text\nhello world\n", strict)
    }

    @Test
    fun alwaysQuoteStringsFeature() {
        val node = ArrayNode.of(
            ObjectNode.of(
                "a" to StringNode.of("x"),
                "b" to NumberNode.of(1),
            ),
        )
        assertEquals("a,b\nx,1\n", node.toCsvString())

        val quoted = node.toCsvString(writeFeatures = withWrite(CsvWriteFeature.ALWAYS_QUOTE_STRINGS))
        assertEquals("\"a\",\"b\"\n\"x\",1\n", quoted)
    }

    @Test
    fun alwaysQuoteEmptyStringsFeature() {
        val node = ArrayNode.of(ObjectNode.of("a" to StringNode.of("")))
        assertEquals("a\n\n", node.toCsvString())

        val quoted = node.toCsvString(writeFeatures = withWrite(CsvWriteFeature.ALWAYS_QUOTE_EMPTY_STRINGS))
        assertEquals("a\n\"\"\n", quoted)
    }

    @Test
    fun alwaysQuoteNumbersFeature() {
        val node = ArrayNode.of(ObjectNode.of("a" to NumberNode.of(12)))
        assertEquals("a\n12\n", node.toCsvString())

        val quoted = node.toCsvString(writeFeatures = withWrite(CsvWriteFeature.ALWAYS_QUOTE_NUMBERS))
        assertEquals("a\n\"12\"\n", quoted)
    }

    @Test
    fun quoteStringsWithLeadingTrailingWhitespaceFeature() {
        val node = ArrayNode.of(ObjectNode.of("v" to StringNode.of(" abc ")))
        val strict = withWrite(CsvWriteFeature.STRICT_CHECK_FOR_QUOTING)
        assertEquals("v\n abc \n", node.toCsvString(writeFeatures = strict))

        val quoted = node.toCsvString(
            writeFeatures = strict + CsvWriteFeature.QUOTE_STRINGS_WITH_LEADING_TRAILING_WHITESPACE,
        )
        assertEquals("v\n\" abc \"\n", quoted)
    }

    @Test
    fun omitMissingTailColumnsFeature() {
        val node = ArrayNode.of(
            ObjectNode.of(
                "a" to StringNode.of("x"),
                "b" to StringNode.of("y"),
            ),
            ObjectNode.of("a" to StringNode.of("z")),
        )
        assertEquals("a,b\nx,y\nz,\n", node.toCsvString())

        val omitted = node.toCsvString(writeFeatures = withWrite(CsvWriteFeature.OMIT_MISSING_TAIL_COLUMNS))
        assertEquals("a,b\nx,y\nz\n", omitted)
    }

    @Test
    fun writeLinefeedAfterLastRowFeature() {
        val node = ArrayNode.of(ObjectNode.of("a" to StringNode.of("x")))
        assertEquals("a\nx\n", node.toCsvString())

        val without = node.toCsvString(
            writeFeatures = CsvConfig.DEFAULT_WRITE_FEATURES - CsvWriteFeature.WRITE_LINEFEED_AFTER_LAST_ROW,
        )
        assertEquals("a\nx", without)
    }

    // ---------------------------------------------------------------- 配置入口

    @Test
    fun configBuilderConfiguresFeaturesAndConvenienceOptions() {
        val charset = Charset.forName("ISO-8859-1")
        val config = CsvConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .fileCharset(charset)
            .atomicFileWrite(true)
            .createParentDirectories(true)
            .maxInputSize(1024)
            .maxStringLength(16)
            .maxNodeDepth(8)
            .maxNodeCount(32)
            .beanValidator(null)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(CsvReadFeature.TRIM_HEADER_SPACES)
            .enable(CsvReadFeature.SKIP_EMPTY_LINES)
            .configure(CsvReadFeature.ALLOW_COMMENTS, true)
            .enable(CsvWriteFeature.ALWAYS_QUOTE_STRINGS)
            .configure(CsvWriteFeature.WRITE_LINEFEED_AFTER_LAST_ROW, false)
            .build()

        assertEquals(PropertyNamingStrategy.SNAKE_CASE, config.namingStrategy)
        assertEquals(charset, config.fileCharset)
        assertTrue(config.atomicFileWrite)
        assertTrue(config.createParentDirectories)
        assertEquals(1024, config.maxInputSize)
        assertEquals(16, config.maxStringLength)
        assertEquals(8, config.maxNodeDepth)
        assertEquals(32, config.maxNodeCount)
        assertTrue(config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
        assertFalse(config.isEnabled(CsvReadFeature.TRIM_HEADER_SPACES))
        assertTrue(config.isEnabled(CsvReadFeature.SKIP_EMPTY_LINES))
        assertTrue(config.isEnabled(CsvReadFeature.ALLOW_COMMENTS))
        assertTrue(config.isEnabled(CsvWriteFeature.ALWAYS_QUOTE_STRINGS))
        assertFalse(config.isEnabled(CsvWriteFeature.WRITE_LINEFEED_AFTER_LAST_ROW))
    }

    @Test
    fun defaultFeatureSetsMatchJacksonDefaults() {
        assertEquals(
            CsvReadFeature.values().filter { it.enabledByDefault }.toSet(),
            CsvConfig.DEFAULT_READ_FEATURES,
        )
        assertEquals(
            CsvWriteFeature.values().filter { it.enabledByDefault }.toSet(),
            CsvConfig.DEFAULT_WRITE_FEATURES,
        )
    }

    @Test
    fun mapperHonoursFeatureConfiguration() {
        val mapper = CsvMapper(
            CsvConfig.builder()
                .enable(CsvReadFeature.SKIP_EMPTY_LINES)
                .enable(CsvWriteFeature.ALWAYS_QUOTE_STRINGS)
                .build(),
        )
        val type = DocumentTypeReference.listType(Person::class.java)
        val people = mapper.parse("name,age\n\n小于,18\n", type)
        assertEquals(1, people.size)
        assertEquals("小于", people[0].name)

        val csv = mapper.format(people)
        assertTrue(csv.contains("\"小于\""))
    }

    @Test
    fun escapeQuoteCharWithEscapeCharFeature() {
        val schema = CsvSchema.builder("s")
            .string("v")
            .escapeChar('\\')
            .build()
        val node = ArrayNode.of(ObjectNode.of("v" to StringNode.of("a\"b")))

        assertEquals("v\n\"a\"\"b\"\n", node.toCsvString(schema))

        val escaped = node.toCsvString(
            schema,
            writeFeatures = withWrite(CsvWriteFeature.ESCAPE_QUOTE_CHAR_WITH_ESCAPE_CHAR),
        )
        assertEquals("v\n\"a\\\"b\"\n", escaped)
        val restored = CsvSchemaReader(escaped, schema).read().asArray()[0].asObject()["v"]?.asString()
        assertEquals("a\"b", restored)
    }

    @Test
    fun escapeControlCharsWithEscapeCharFeature() {
        val schema = CsvSchema.builder("s")
            .string("v")
            .escapeChar('\\')
            .build()
        val node = ArrayNode.of(ObjectNode.of("v" to StringNode.of("line1\nline2")))

        val escaped = node.toCsvString(
            schema,
            writeFeatures = withWrite(CsvWriteFeature.ESCAPE_CONTROL_CHARS_WITH_ESCAPE_CHAR),
        )
        assertEquals("v\n\"line1\\nline2\"\n", escaped)

        val restored = CsvSchemaReader(escaped, schema).read().asArray()[0].asObject()["v"]?.asString()
        assertEquals("line1\nline2", restored)
    }

    // ---------------------------------------------------------------- 各读取路径一致性

    @Test
    fun streamingParseHonoursReadFeatures() {
        val mapper = CsvMapper(
            CsvConfig.builder()
                .enable(CsvReadFeature.SKIP_EMPTY_LINES)
                .build(),
        )
        val received = ArrayList<Person>()
        "name,age\n\n小于,18\n".reader().use { reader ->
            mapper.parseStream(reader, Person::class.java) { received.add(it) }
        }
        assertEquals(1, received.size)
        assertEquals("小于", received[0].name)
        assertEquals(18, received[0].age)
    }

    @Test
    fun streamingParseHonoursSchemaSkipFirstDataRow() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .header(false)
            .skipFirstDataRow(true)
            .build()
        val mapper = CsvMapper(schema)
        val received = ArrayList<Person>()
        "junk\n小于\n".reader().use { reader ->
            mapper.parseStream(reader, Person::class.java) { received.add(it) }
        }
        assertEquals(1, received.size)
        assertEquals("小于", received[0].name)
    }

    @Test
    fun tokenReaderHonoursNullFeatures() {
        val reader = CsvTokenReader(
            "v\n\n",
            ',',
            true,
            readFeatures = withRead(CsvReadFeature.EMPTY_STRING_AS_NULL),
        )
        val tokens = ArrayList<DocumentTokenType>()
        while (true) {
            tokens.add(reader.nextToken() ?: break)
        }
        assertEquals(
            listOf(
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NULL,
                DocumentTokenType.END_OBJECT,
                DocumentTokenType.END_ARRAY,
            ),
            tokens,
        )
    }

    private fun withRead(vararg features: CsvReadFeature): Set<CsvReadFeature> = CsvConfig.DEFAULT_READ_FEATURES + features.toSet()

    private fun withWrite(vararg features: CsvWriteFeature): Set<CsvWriteFeature> = CsvConfig.DEFAULT_WRITE_FEATURES + features.toSet()

    private class Person {
        var name: String = ""
        var age: Int = 0
    }
}
