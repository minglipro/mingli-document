package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.csv.exception.CsvParseException
import com.mingliqiye.document.schema.SchemaType
import com.mingliqiye.document.serialization.DocumentMapperConfig
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * CSV 流式逐行解析测试。
 *
 * 覆盖逐条回调语义、表头/类型推断/Schema/嵌套复用、空输入、畸形输入、超大行、
 * 编码错误、关闭语义、`maxRows`、`maxInputSize` 与回调异常。
 */
class CsvStreamingTest {

    // ---------------------------------------------------------------------
    // 基础逐行语义
    // ---------------------------------------------------------------------

    @Test
    fun parseStreamReaderDeliversRowsIncrementally() {
        val rowCount = 2_000
        val csv = buildString {
            append("name,age\n")
            for (index in 1..rowCount) {
                append("n$index,$index\n")
            }
        }
        val reader = RecordingReader(csv)
        val mapper = CsvMapper()
        val received = ArrayList<Person>()
        var consumedAtFirstCallback = -1L

        mapper.parseStream(reader, Person::class.java) { person ->
            if (received.isEmpty()) {
                consumedAtFirstCallback = reader.consumed
            }
            received.add(person)
        }

        assertEquals(rowCount, received.size)
        assertEquals("n1", received[0].name)
        assertEquals(1, received[0].age)
        assertEquals("n$rowCount", received[rowCount - 1].name)
        assertEquals(rowCount, received[rowCount - 1].age)
        // 首次回调时远未读完整个输入：证明逐行回调、内存不随总行数线性增长。
        assertTrue(consumedAtFirstCallback in 1..(csv.length / 2))
        assertFalse(reader.closed)
    }

    @Test
    fun parseStreamInputStreamAndTypeReferenceWork() {
        val input = RecordingInputStream("name,age\n小于😀,18\n张三,20\n")
        val names = ArrayList<String>()

        CsvMapper().parseStream(
            input,
            DocumentTypeReference.of(Person::class.java),
        ) { person -> names.add(person.name) }

        assertEquals(listOf("小于😀", "张三"), names)
        assertFalse(input.closed)
    }

    @Test
    fun parseStreamReusesHeaderAndTypeInference() {
        val csv = "name,age,score,active\n小于,18,99.5,true\n张三,20,88,false\n"
        val received = ArrayList<TypedPerson>()

        CsvMapper().parseStream(StringReader(csv), TypedPerson::class.java) { received.add(it) }

        assertEquals(2, received.size)
        assertEquals("小于", received[0].name)
        assertEquals(18, received[0].age)
        assertEquals(99.5, received[0].score)
        assertTrue(received[0].active)
        assertFalse(received[1].active)
    }

    @Test
    fun parseStreamParsesQuotedMultilineCellsPerRecord() {
        val csv = "name,note\n\"a\",\"line1\nline2\"\n\"b\",\"x,y\"\n"
        val received = ArrayList<NotedPerson>()

        CsvMapper().parseStream(StringReader(csv), NotedPerson::class.java) { received.add(it) }

        assertEquals(2, received.size)
        assertEquals("a", received[0].name)
        assertEquals("line1\nline2", received[0].note)
        assertEquals("x,y", received[1].note)
    }

    @Test
    fun parseStreamEmptyAndHeaderOnlyInputsProduceNoCallbacks() {
        val received = ArrayList<Person>()

        CsvMapper().parseStream(StringReader(""), Person::class.java) { received.add(it) }
        CsvMapper().parseStream(StringReader("name,age\n"), Person::class.java) { received.add(it) }

        assertTrue(received.isEmpty())
    }

    @Test
    fun parseStreamHugeRowWorks() {
        val longName = "x".repeat(200_000)
        val received = ArrayList<Person>()

        CsvMapper().parseStream(
            StringReader("name,age\n$longName,1\n"),
            Person::class.java,
        ) { received.add(it) }

        assertEquals(1, received.size)
        assertEquals(longName, received[0].name)
    }

    // ---------------------------------------------------------------------
    // 无表头 / 分隔符
    // ---------------------------------------------------------------------

    @Test
    fun parseStreamWithoutHeaderUsesPositionalColumnNames() {
        val mapper = CsvMapper(CsvConfig.builder().header(false).build())
        val nodes = ArrayList<Node>()

        mapper.parseStream(StringReader("a,1\nb,2\n"), Node::class.java) { nodes.add(it) }

        assertEquals(2, nodes.size)
        assertEquals(listOf("column1", "column2"), nodes[0].asObject().keys.toList())
        assertEquals("a", nodes[0].asObject()["column1"]!!.asString())
        assertEquals("1", nodes[0].asObject()["column2"]!!.asNumber().raw)
    }

    @Test
    fun parseStreamHonorsCustomDelimiter() {
        val mapper = CsvMapper(CsvConfig.builder().delimiter(';').build())
        val received = ArrayList<Person>()

        mapper.parseStream(StringReader("name;age\n小于;18\n"), Person::class.java) { received.add(it) }

        assertEquals(1, received.size)
        assertEquals("小于", received[0].name)
        assertEquals(18, received[0].age)
    }

    // ---------------------------------------------------------------------
    // Schema 复用
    // ---------------------------------------------------------------------

    @Test
    fun parseStreamReusesSchemaTypes() {
        val schema = CsvSchema.builder("person")
            .field("name", SchemaType.STRING)
            .field("age", SchemaType.INT)
            .field("score", SchemaType.DOUBLE)
            .build()
        val mapper = CsvMapper(schema)
        val received = ArrayList<TypedPerson>()

        mapper.parseStream(
            StringReader("name,age,score\n小于,18,99.5\n"),
            TypedPerson::class.java,
        ) { received.add(it) }

        assertEquals(1, received.size)
        assertEquals("小于", received[0].name)
        assertEquals(18, received[0].age)
        assertEquals(99.5, received[0].score)
    }

    @Test
    fun parseStreamReusesSchemaWithoutHeaderByPosition() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .int("age")
            .header(false)
            .build()
        val mapper = CsvMapper(schema)
        val received = ArrayList<Person>()

        mapper.parseStream(StringReader("小于,18\n张三,20\n"), Person::class.java) { received.add(it) }

        assertEquals(2, received.size)
        assertEquals("小于", received[0].name)
        assertEquals(20, received[1].age)
    }

    @Test
    fun parseStreamReusesNestedSchemaColumns() {
        val geo = CsvSchema.builder("geo")
            .int("lat")
            .build()
        val address = CsvSchema.builder("address")
            .string("city")
            .objectField("geo", geo)
            .build()
        val schema = CsvSchema.builder("person")
            .string("name")
            .objectField("address", address)
            .build()
        val nodes = ArrayList<Node>()

        CsvMapper(schema).parseStream(
            StringReader("name,address.city,address.geo.lat\n小于,杭州,30\n"),
            Node::class.java,
        ) { nodes.add(it) }

        assertEquals(1, nodes.size)
        assertNodesEqual(
            obj(
                "name" to str("小于"),
                "address" to obj(
                    "city" to str("杭州"),
                    "geo" to obj("lat" to num(30)),
                ),
            ),
            nodes[0],
        )
    }

    @Test
    fun parseStreamSchemaRequiredColumnMissingFails() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .int("age")
            .build()

        val error = assertFailsWith<CsvParseException> {
            CsvMapper(schema).parseStream(StringReader("name\n小于\n"), Person::class.java) { }
        }

        assertTrue(error.message.orEmpty().contains("Missing required CSV column 'age'"))
    }

    @Test
    fun parseStreamSchemaUnknownColumnFailsWhenConfigured() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .failOnUnknownColumns(true)
            .build()

        assertFailsWith<CsvParseException> {
            CsvMapper(schema).parseStream(StringReader("name,extra\n小于,x\n"), Node::class.java) { }
        }
    }

    // ---------------------------------------------------------------------
    // 嵌套单元格
    // ---------------------------------------------------------------------

    @Test
    fun parseStreamSupportsNestedJsonCells() {
        val mapper = CsvMapper(CsvConfig.builder().nestedMode(CsvNestedMode.JSON).build())
        val received = ArrayList<PersonWithAddress>()
        val csv = "name,address\n小于,\"{\"\"city\"\":\"\"杭州\"\"}\"\n"

        mapper.parseStream(StringReader(csv), PersonWithAddress::class.java) { received.add(it) }

        assertEquals(1, received.size)
        assertEquals("小于", received[0].name)
        assertEquals("杭州", received[0].address?.city)
    }

    @Test
    fun parseStreamSupportsPointedNestedColumns() {
        val mapper = CsvMapper(CsvConfig.builder().nestedMode(CsvNestedMode.COLUMNS).build())
        val received = ArrayList<PersonWithAddress>()

        mapper.parseStream(
            StringReader("name,address.city\n小于,杭州\n"),
            PersonWithAddress::class.java,
        ) { received.add(it) }

        assertEquals(1, received.size)
        assertEquals("杭州", received[0].address?.city)
    }

    // ---------------------------------------------------------------------
    // 异常 / 限制 / 关闭语义
    // ---------------------------------------------------------------------

    @Test
    fun parseStreamMalformedCsvThrowsCsvParseException() {
        assertFailsWith<CsvParseException> {
            CsvMapper().parseStream(StringReader("name,age\n\"unterminated,1\n"), Person::class.java) { }
        }
        assertFailsWith<CsvParseException> {
            CsvMapper().parseStream(StringReader("name,age\n\"a\"x,1\n"), Person::class.java) { }
        }
    }

    @Test
    fun parseStreamRejectsInvalidUtf8InputStream() {
        val bytes = "name\n".toByteArray(Charsets.UTF_8) +
            byteArrayOf(0xC3.toByte()) +
            "\n".toByteArray(Charsets.UTF_8)

        val error = assertFailsWith<CsvParseException> {
            CsvMapper().parseStream(RecordingInputStream(bytes), Person::class.java) { }
        }

        assertTrue(error.message.orEmpty().contains("UTF-8"))
    }

    @Test
    fun parseStreamConsumerExceptionPropagatesWithoutClosingStream() {
        val boom = IllegalStateException("consumer boom")
        val reader = RecordingReader("name,age\n小于,18\n张三,20\n")
        var callbacks = 0

        val error = assertFailsWith<IllegalStateException> {
            CsvMapper().parseStream(reader, Person::class.java) {
                callbacks++
                throw boom
            }
        }

        assertSame(boom, error)
        assertEquals(1, callbacks)
        assertFalse(reader.closed)
    }

    @Test
    fun parseStreamRejectsInvalidMaxRows() {
        assertFailsWith<IllegalArgumentException> {
            CsvMapper().parseStream(StringReader("name,age\n"), Person::class.java, -2) { }
        }
    }

    @Test
    fun parseStreamMaxRowsStopsReadingEarly() {
        val rowCount = 2_000
        val csv = buildString {
            append("name,age\n")
            for (index in 1..rowCount) {
                append("n$index,$index\n")
            }
        }
        val reader = RecordingReader(csv)
        val received = ArrayList<Person>()

        CsvMapper().parseStream(reader, Person::class.java, maxRows = 10) { received.add(it) }

        assertEquals(10, received.size)
        assertEquals("n1", received[0].name)
        assertEquals("n10", received[9].name)
        assertTrue(reader.consumed < csv.length / 2)
    }

    @Test
    fun parseStreamMaxRowsZeroProducesNoCallbacks() {
        val received = ArrayList<Person>()

        CsvMapper().parseStream(StringReader("name,age\n小于,18\n"), Person::class.java, 0) {
            received.add(it)
        }

        assertTrue(received.isEmpty())
    }

    @Test
    fun parseStreamRespectsMaxInputSizeCumulativeLimit() {
        val mapper = CsvMapper(DocumentMapperConfig.builder().maxInputSize(16_384).build())
        val csv = buildString {
            append("name,age\n")
            for (index in 1..2_000) {
                append("name-$index,$index\n")
            }
        }
        val reader = RecordingReader(csv)
        var callbacks = 0

        val error = assertFailsWith<com.mingliqiye.document.serialization.exception.DocumentSerializationException> {
            mapper.parseStream(reader, Person::class.java) { callbacks++ }
        }

        assertTrue(error.message.orEmpty().contains("maxInputSize"))
        assertTrue(callbacks > 0)
        assertTrue(reader.consumed < csv.length)
    }

    // ---------------------------------------------------------------------
    // 测试辅助
    // ---------------------------------------------------------------------

    private class Person {
        var name: String = ""
        var age: Int = 0
    }

    private class TypedPerson {
        var name: String = ""
        var age: Int = 0
        var score: Double = 0.0
        var active: Boolean = false
    }

    private class NotedPerson {
        var name: String = ""
        var note: String = ""
    }

    private class PersonWithAddress {
        var name: String = ""
        var address: Address? = null
    }

    private class Address {
        var city: String = ""
    }

    /** 记录已读字符数/关闭状态的 Reader。 */
    private class RecordingReader(source: String) : Reader() {
        private val delegate = StringReader(source)

        var consumed: Long = 0L
            private set

        var closed: Boolean = false
            private set

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            val read = delegate.read(buffer, offset, length)
            if (read > 0) {
                consumed += read
            }
            return read
        }

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) {
                consumed++
            }
            return value
        }

        override fun close() {
            closed = true
        }
    }

    /** 记录已读字节数/关闭状态的 InputStream。 */
    private class RecordingInputStream(private val source: ByteArray) : InputStream() {
        private var position = 0

        var consumed: Long = 0L
            private set

        var closed: Boolean = false
            private set

        constructor(text: String) : this(text.toByteArray(Charsets.UTF_8))

        override fun read(): Int {
            if (position >= source.size) {
                return -1
            }
            consumed++
            return source[position++].toInt() and 0xFF
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= source.size) {
                return -1
            }
            val size = minOf(length, source.size - position)
            System.arraycopy(source, position, buffer, offset, size)
            position += size
            consumed += size
            return size
        }

        override fun close() {
            closed = true
        }
    }
}
