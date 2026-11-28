package com.mingliqiye.document.format.json

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.json.exception.JsonParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * JSON 流式读写测试。
 *
 * 覆盖 JSON Lines / NDJSON 与顶层数组流式解析/写出：逐条回调语义、空输入、
 * 畸形行、巨大行、编码错误、关闭语义、maxInputSize、回调异常与内存不随总行数线性增长。
 */
class JsonStreamingTest {

    // ---------------------------------------------------------------------
    // JSON Lines：解析
    // ---------------------------------------------------------------------

    @Test
    fun parseLinesReaderDeliversBeansIncrementally() {
        val count = 2_000
        val text = buildString {
            for (index in 1..count) {
                append("""{"name":"n$index","age":$index}""").append('\n')
            }
        }
        val reader = RecordingReader(text)
        val mapper = JsonMapper()
        val received = ArrayList<Person>()
        var consumedAtFirstCallback = -1L

        mapper.parseLines(reader, Person::class.java) { person ->
            if (received.isEmpty()) {
                consumedAtFirstCallback = reader.consumed
            }
            received.add(person!!)
        }

        assertEquals(count, received.size)
        assertEquals("n1", received[0].name)
        assertEquals(1, received[0].age)
        assertEquals("n$count", received[count - 1].name)
        assertEquals(count, received[count - 1].age)
        // 首次回调时远未读完整个输入：证明逐条回调、内存不随总行数线性增长。
        assertTrue(consumedAtFirstCallback in 1..(text.length / 2))
        assertFalse(reader.closed)
    }

    @Test
    fun parseLinesInputStreamAndTypeReferenceWork() {
        val input = RecordingInputStream(
            """{"name":"小于😀","age":18}""" + "\n" + """{"name":"张三","age":20}""" + "\n",
        )
        val mapper = JsonMapper()
        val names = ArrayList<String>()
        val type = DocumentTypeReference.of(Person::class.java)

        mapper.parseLines(input, type) { person -> names.add(person!!.name) }

        assertEquals(listOf("小于😀", "张三"), names)
        assertFalse(input.closed)
    }

    @Test
    fun parseLinesSkipsBlankLinesAndHandlesBomAndCrlf() {
        val text = "\uFEFF{\"name\":\"a\",\"age\":1}\r\n\r\n   \r\n{\"name\":\"b\",\"age\":2}\r\n"
        val people = ArrayList<Person>()

        JsonMapper().parseLines(StringReader(text), Person::class.java) { people.add(it!!) }

        assertEquals(2, people.size)
        assertEquals("a", people[0].name)
        assertEquals("b", people[1].name)
    }

    @Test
    fun parseLinesEmptyInputProducesNoCallbacks() {
        val people = ArrayList<Person>()

        JsonMapper().parseLines(RecordingReader(""), Person::class.java) { people.add(it!!) }
        JsonMapper().parseLines(RecordingReader("\n  \n\t\n"), Person::class.java) { people.add(it!!) }

        assertTrue(people.isEmpty())
    }

    @Test
    fun parseLinesMalformedLineReportsLineNumber() {
        val text = """{"name":"a","age":1}""" + "\n" + """{"name":"b","age":2}""" + "\n" + """{"name":"c","age":}"""

        val error = assertFailsWith<JsonParseException> {
            JsonMapper().parseLines(StringReader(text), Person::class.java) { }
        }

        assertTrue(error.message.orEmpty().contains("line 3"))
    }

    @Test
    fun parseLinesTypeMismatchWrappedWithLineNumber() {
        val error = assertFailsWith<DocumentSerializationException> {
            JsonMapper().parseLines(
                StringReader("""{"name":"a","age":"x"}"""),
                Person::class.java,
            ) { }
        }

        assertTrue(error.message.orEmpty().contains("line 1"))
    }

    @Test
    fun parseLinesConsumerExceptionPropagatesWithoutClosingStream() {
        val boom = IllegalStateException("consumer boom")
        val reader = RecordingReader("""{"name":"a"}""" + "\n" + """{"name":"b"}""" + "\n")
        var callbacks = 0

        val error = assertFailsWith<IllegalStateException> {
            JsonMapper().parseLines(reader, Person::class.java) {
                callbacks++
                throw boom
            }
        }

        assertSame(boom, error)
        assertEquals(1, callbacks)
        assertFalse(reader.closed)
    }

    @Test
    fun parseLinesHandlesVeryLongLine() {
        val longName = "x".repeat(200_000)
        val text = """{"name":"$longName"}"""

        val people = ArrayList<Person>()
        JsonMapper().parseLines(StringReader(text), Person::class.java) { people.add(it!!) }

        assertEquals(1, people.size)
        assertEquals(longName.length, people[0].name.length)
        assertEquals(longName, people[0].name)
    }

    @Test
    fun parseLinesRejectsInvalidUtf8InputStream() {
        val mapper = JsonMapper()
        val prefix = """{"name":"a""".toByteArray(Charsets.UTF_8)
        val invalid = byteArrayOf(0xC3.toByte())
        val suffix = """"}""".toByteArray(Charsets.UTF_8)
        val input = RecordingInputStream(prefix + invalid + suffix)

        val error = assertFailsWith<JsonParseException> {
            mapper.parseLines(input, Person::class.java) { }
        }

        assertTrue(error.message.orEmpty().contains("UTF-8"))
    }

    @Test
    fun parseLinesRespectsMaxInputSizeCumulativeLimit() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().maxInputSize(16_384).build())
        val text = buildString {
            for (index in 1..1_000) {
                append("""{"name":"name-$index","age":$index}""").append('\n')
            }
        }
        val reader = RecordingReader(text)
        var callbacks = 0

        val error = assertFailsWith<DocumentSerializationException> {
            mapper.parseLines(reader, Person::class.java) { callbacks++ }
        }

        assertTrue(error.message.orEmpty().contains("maxInputSize"))
        assertTrue(callbacks > 0)
        assertTrue(reader.consumed < text.length)
    }

    // ---------------------------------------------------------------------
    // JSON Lines：写出
    // ---------------------------------------------------------------------

    @Test
    fun formatLinesOutputStreamWritesOneCompactJsonPerLine() {
        val prettyMapper = JsonMapper(JsonConfig.builder().pretty(true).build())
        val compactMapper = JsonMapper()
        val values = listOf<Any?>(
            mapOf("name" to "a\nb"),
            42,
            null,
            listOf(1, 2),
            "text",
        )
        val output = RecordingOutputStream()

        prettyMapper.formatLines(values, output)

        val lines = String(output.toByteArray(), Charsets.UTF_8).split('\n')
        assertEquals(values.size, lines.size - 1)
        assertEquals("", lines.last())
        for (index in values.indices) {
            assertEquals(compactMapper.format(values[index]), lines[index])
            // 每个值独占一行：紧凑格式不含未转义换行。
            assertFalse(lines[index].contains('\n'))
        }
        assertFalse(output.closed)
    }

    @Test
    fun formatLinesWriterMatchesOutputStream() {
        val values = listOf(mapOf("a" to 1), "x", emptyList<Int>())
        val writer = StringWriter()
        val output = RecordingOutputStream()
        val mapper = JsonMapper()

        mapper.formatLines(values, writer)
        mapper.formatLines(values, output)

        assertEquals(writer.toString(), String(output.toByteArray(), Charsets.UTF_8))
        assertEquals("{\"a\":1}\n\"x\"\n[]\n", writer.toString())
    }

    @Test
    fun formatLinesEmptyIterableWritesNothing() {
        val output = RecordingOutputStream()

        JsonMapper().formatLines(emptyList<Any?>(), output)

        assertEquals(0, output.toByteArray().size)
    }

    @Test
    fun formatLinesRoundTripsThroughParseLines() {
        val people = listOf(
            Person().apply {
                name = "小于"
                age = 18
            },
            Person().apply {
                name = "张三"
                age = 20
            },
            Person().apply {
                name = "line\nbreak"
                age = 0
            },
        )
        val output = RecordingOutputStream()

        JsonMapper().formatLines(people, output)
        val restored = ArrayList<Person>()
        JsonMapper().parseLines(
            RecordingInputStream(output.toByteArray()),
            Person::class.java,
        ) { restored.add(it!!) }

        assertEquals(people.map { it.name }, restored.map { it.name })
        assertEquals(people.map { it.age }, restored.map { it.age })
    }

    // ---------------------------------------------------------------------
    // 顶层数组：解析
    // ---------------------------------------------------------------------

    @Test
    fun parseArrayReaderDeliversElementsIncrementally() {
        val count = 2_000
        val text = "[" + (1..count).joinToString(",") { """{"name":"n$it","age":$it}""" } + "]"
        val reader = RecordingReader(text)
        val received = ArrayList<Person>()
        var consumedAtFirstCallback = -1L

        JsonMapper().parseArray(reader, Person::class.java) { person ->
            if (received.isEmpty()) {
                consumedAtFirstCallback = reader.consumed
            }
            received.add(person!!)
        }

        assertEquals(count, received.size)
        assertEquals("n1", received[0].name)
        assertEquals(count, received[count - 1].age)
        assertTrue(consumedAtFirstCallback in 1..(text.length / 2))
        assertFalse(reader.closed)
    }

    @Test
    fun parseArrayInputStreamAndTypeReferenceWork() {
        val input = RecordingInputStream("""[{"name":"小于😀","age":18},{"name":"张三","age":20}]""")
        val names = ArrayList<String>()

        JsonMapper().parseArray(input, DocumentTypeReference.of(Person::class.java)) { person ->
            names.add(person!!.name)
        }

        assertEquals(listOf("小于😀", "张三"), names)
        assertFalse(input.closed)
    }

    @Test
    fun parseArraySupportsNestedValuesAndComments() {
        val json = """
            [ // 行注释
              {"a":"x,y}{","b":[1,{"c":true}]}, /* 块注释 } ] */
              "str\"quote", true, null, -1.5e2, [ ]
            ]
        """.trimIndent()
        val mapper = JsonMapper()
        val nodes = ArrayList<Node?>()

        mapper.parseArray(StringReader(json), Node::class.java) { nodes.add(it) }

        assertEquals(6, nodes.size)
        assertEquals(mapper.parse("""{"a":"x,y}{","b":[1,{"c":true}]}""", Node::class.java), nodes[0])
        assertEquals(mapper.parse(""""str\"quote"""", Node::class.java), nodes[1])
        assertEquals(mapper.parse("true", Node::class.java), nodes[2])
        assertNull(nodes[3])
        assertEquals(mapper.parse("-1.5e2", Node::class.java), nodes[4])
        assertEquals(mapper.parse("[]", Node::class.java), nodes[5])
    }

    @Test
    fun parseArrayStrictModeRejectsComments() {
        val mapper = JsonMapper(JsonConfig.builder().allowComments(false).build())

        assertFailsWith<JsonParseException> {
            mapper.parseArray(StringReader("[1, // c\n2]"), Node::class.java) { }
        }
    }

    @Test
    fun parseArrayEmptyArrayProducesNoCallbacks() {
        val mapper = JsonMapper()
        var callbacks = 0

        mapper.parseArray(StringReader("[ ]"), Node::class.java) { callbacks++ }
        mapper.parseArray(StringReader("/* empty */ [\n]"), Node::class.java) { callbacks++ }

        assertEquals(0, callbacks)
    }

    @Test
    fun parseArrayMalformedInputsThrowParseException() {
        val mapper = JsonMapper()

        val emptyInput = assertFailsWith<JsonParseException> {
            mapper.parseArray(StringReader(""), Node::class.java) { }
        }
        assertTrue(emptyInput.message.orEmpty().contains("Unexpected end of input"))

        val notArray = assertFailsWith<JsonParseException> {
            mapper.parseArray(StringReader("{}"), Node::class.java) { }
        }
        assertTrue(notArray.message.orEmpty().contains("Expected '[' at top level"))

        val trailingComma = assertFailsWith<JsonParseException> {
            mapper.parseArray(StringReader("[1,]"), Node::class.java) { }
        }
        assertTrue(trailingComma.message.orEmpty().contains("Trailing comma"))

        val missingComma = assertFailsWith<JsonParseException> {
            mapper.parseArray(StringReader("[1 2]"), Node::class.java) { }
        }
        assertTrue(missingComma.message.orEmpty().contains("Expected ',' or ']'"))

        val unterminated = assertFailsWith<JsonParseException> {
            mapper.parseArray(StringReader("[1,2"), Node::class.java) { }
        }
        assertTrue(unterminated.message.orEmpty().contains("Unexpected end of input"))

        val trailingContent = assertFailsWith<JsonParseException> {
            mapper.parseArray(StringReader("[1] x"), Node::class.java) { }
        }
        assertTrue(trailingContent.message.orEmpty().contains("Unexpected trailing content"))
    }

    @Test
    fun parseArrayMalformedElementReportsLineNumber() {
        val error = assertFailsWith<JsonParseException> {
            JsonMapper().parseArray(StringReader("[{\"a\":1},\n{\"b\":}]"), Node::class.java) { }
        }

        assertTrue(error.message.orEmpty().contains("line 2"))
    }

    @Test
    fun parseArrayRejectsInvalidUtf8InputStream() {
        val bytes = """[{"name":"a""".toByteArray(Charsets.UTF_8) +
            byteArrayOf(0xC3.toByte()) +
            """"}]""".toByteArray(Charsets.UTF_8)

        val error = assertFailsWith<JsonParseException> {
            JsonMapper().parseArray(RecordingInputStream(bytes), Person::class.java) { }
        }

        assertTrue(error.message.orEmpty().contains("UTF-8"))
    }

    @Test
    fun parseArrayConsumerExceptionPropagatesWithoutClosingStream() {
        val boom = IllegalArgumentException("array boom")
        val reader = RecordingReader("[1,2,3]")
        var callbacks = 0

        val error = assertFailsWith<IllegalArgumentException> {
            JsonMapper().parseArray(reader, Node::class.java) {
                callbacks++
                throw boom
            }
        }

        assertSame(boom, error)
        assertEquals(1, callbacks)
        assertFalse(reader.closed)
    }

    @Test
    fun parseArrayHonorsMaxDepth() {
        val mapper = JsonMapper(JsonConfig.builder().maxDepth(2).build())

        mapper.parseArray(StringReader("""[{"a":{"b":1}}]"""), Node::class.java) { }

        assertFailsWith<JsonParseException> {
            mapper.parseArray(StringReader("""[{"a":{"b":{"c":1}}}]"""), Node::class.java) { }
        }
    }

    @Test
    fun parseArrayRespectsMaxInputSizeCumulativeLimit() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().maxInputSize(16_384).build())
        val text = "[" + (1..2_000).joinToString(",") { """{"name":"name-$it"}""" } + "]"
        var callbacks = 0

        val error = assertFailsWith<DocumentSerializationException> {
            mapper.parseArray(RecordingReader(text), Person::class.java) { callbacks++ }
        }

        assertTrue(error.message.orEmpty().contains("maxInputSize"))
        assertTrue(callbacks > 0)
    }

    // ---------------------------------------------------------------------
    // 顶层数组：写出
    // ---------------------------------------------------------------------

    @Test
    fun formatArrayOutputStreamMatchesFormat() {
        val values = listOf<Any?>(mapOf("a" to 1), "x", 3, null, listOf(1, 2))
        val expected = JsonMapper().format(values).toByteArray(Charsets.UTF_8)
        val output = RecordingOutputStream()

        JsonMapper().formatArray(values, output)

        assertContentEquals(expected, output.toByteArray())
        assertFalse(output.closed)
    }

    @Test
    fun formatArrayWriterMatchesOutputStreamAndPrettyConfigStaysCompact() {
        val values = listOf(1, "a\nb", listOf(true, false))
        val prettyMapper = JsonMapper(JsonConfig.builder().pretty(true).build())
        val writer = StringWriter()
        val output = RecordingOutputStream()

        prettyMapper.formatArray(values, writer)
        prettyMapper.formatArray(values, output)

        assertEquals(writer.toString(), String(output.toByteArray(), Charsets.UTF_8))
        assertEquals("[1,\"a\\nb\",[true,false]]", writer.toString())
    }

    @Test
    fun formatArrayEmptyIterableWritesEmptyArray() {
        val output = RecordingOutputStream()

        JsonMapper().formatArray(emptyList<Any?>(), output)

        assertEquals("[]", String(output.toByteArray(), Charsets.UTF_8))
    }

    @Test
    fun formatArrayRoundTripsThroughParseArray() {
        val values = listOf<Any?>(mapOf("name" to "小于"), mapOf("name" to "a,b}{"), mapOf("name" to "line\nbreak"))
        val output = RecordingOutputStream()

        JsonMapper().formatArray(values, output)
        val restored = ArrayList<Node>()
        JsonMapper().parseArray(
            RecordingInputStream(output.toByteArray()),
            Node::class.java,
        ) { restored.add(it!!) }

        val mapper = JsonMapper()
        assertEquals(values.map { mapper.toNode(it) }, restored)
    }

    // ---------------------------------------------------------------------
    // 测试辅助
    // ---------------------------------------------------------------------

    private class Person {
        var name: String = ""
        var age: Int = 0
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

    /** 记录关闭状态的 OutputStream。 */
    private class RecordingOutputStream : OutputStream() {
        private val delegate = ByteArrayOutputStream()

        var closed: Boolean = false
            private set

        override fun write(value: Int) {
            delegate.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            delegate.write(buffer, offset, length)
        }

        fun toByteArray(): ByteArray = delegate.toByteArray()

        override fun close() {
            closed = true
        }
    }
}
