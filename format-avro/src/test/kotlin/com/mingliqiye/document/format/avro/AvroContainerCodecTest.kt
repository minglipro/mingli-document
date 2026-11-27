package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.avro.exception.AvroException
import com.mingliqiye.document.format.avro.exception.AvroParseException
import org.apache.avro.Schema
import org.apache.avro.file.CodecFactory
import org.apache.avro.file.DataFileConstants
import org.apache.avro.file.DataFileReader
import org.apache.avro.file.DataFileWriter
import org.apache.avro.file.SeekableByteArrayInput
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Avro OCF codec 与 appendTo 测试。
 */
class AvroContainerCodecTest {

    @Test
    fun nullCodecRoundTrip() {
        val mapper = AvroMapper(userSchema)
        val nodes = listOf(userNode(1L, "a"), userNode(2L, "b"))

        val bytes = mapper.formatContainerBytes(nodes, AvroCodec.NULL)

        assertEquals("null", containerCodec(bytes))
        assertEquals(nodes, mapper.parseContainerToNodes(bytes))
    }

    @Test
    fun deflateCodecRoundTrip() {
        val mapper = AvroMapper(userSchema)
        val nodes = (1..200).map { userNode(it.toLong(), "compressible-" + "x".repeat(120)) }

        val nullBytes = mapper.formatContainerBytes(nodes, AvroCodec.NULL)
        val deflateBytes = mapper.formatContainerBytes(nodes, AvroCodec.DEFLATE)

        assertEquals("deflate", containerCodec(deflateBytes))
        assertTrue(deflateBytes.size < nullBytes.size)
        assertEquals(nodes, mapper.parseContainerToNodes(deflateBytes))
        assertEquals(200, readAllRecordsWithAvro(deflateBytes).size)
    }

    @Test
    fun snappyCodecRoundTrip() {
        val mapper = AvroMapper(userSchema)
        val nodes = listOf(userNode(1L, "a"), userNode(2L, "b"))

        val bytes = mapper.formatContainerBytes(nodes, AvroCodec.SNAPPY)

        assertEquals("snappy", containerCodec(bytes))
        assertEquals(nodes, mapper.parseContainerToNodes(bytes))
        assertEquals(2, readAllRecordsWithAvro(bytes).size)
    }

    @Test
    fun defaultContainerApiUnchanged() {
        val mapper = AvroMapper(userSchema)
        val nodes = listOf(userNode(1L, "a"), userNode(2L, "b"))

        val bytes = mapper.formatContainerBytes(nodes)

        assertEquals("null", containerCodec(bytes))
        assertEquals(nodes, mapper.parseContainerToNodes(bytes))
        assertEquals(2, mapper.parseContainer(bytes, UserBean::class.java).size)
    }

    @Test
    fun emptyContainerWithCodecRoundTrips() {
        val mapper = AvroMapper(userSchema)

        val bytes = mapper.formatContainerBytes(emptyList(), AvroCodec.DEFLATE)

        assertEquals("deflate", containerCodec(bytes))
        assertEquals(0, mapper.parseContainerToNodes(bytes).size)
    }

    @Test
    fun codecNameMapping() {
        assertEquals(AvroCodec.NULL, AvroCodec.fromCodecName("null"))
        assertEquals(AvroCodec.DEFLATE, AvroCodec.fromCodecName(" deflate "))
        assertEquals(AvroCodec.SNAPPY, AvroCodec.fromCodecName("snappy"))
        assertNull(AvroCodec.fromCodecName("bzip2"))
    }

    @Test
    fun appendToFileKeepsExistingRecords() {
        val mapper = AvroMapper(userSchema)
        val file = tempFile()
        try {
            mapper.appendContainer(file, listOf(userNode(1L, "a")))
            val firstBytes = file.readBytes()
            assertEquals(listOf(1L), containerIds(firstBytes))

            mapper.appendContainer(file, listOf(userNode(2L, "b"), userNode(3L, "c")), AvroCodec.NULL)
            val secondBytes = file.readBytes()
            assertTrue(secondBytes.size > firstBytes.size)
            assertContentEquals(firstBytes, secondBytes.copyOf(firstBytes.size))
            assertEquals(listOf(1L, 2L, 3L), containerIds(secondBytes))

            mapper.appendContainer(file.toPath(), listOf(userNode(4L, "d")))
            assertEquals(listOf(1L, 2L, 3L, 4L), containerIds(file.readBytes()))
        } finally {
            file.delete()
        }
    }

    @Test
    fun appendToDeflateFileInfersExistingCodec() {
        val mapper = AvroMapper(userSchema)
        val file = tempFile()
        try {
            mapper.appendContainer(file, listOf(userNode(1L, "a")), AvroCodec.DEFLATE)
            // 不传 codec 时沿用已有容器的 deflate
            mapper.appendContainer(file, listOf(userNode(2L, "b")))

            val bytes = file.readBytes()
            assertEquals("deflate", containerCodec(bytes))
            assertEquals(listOf(1L, 2L), containerIds(bytes))
        } finally {
            file.delete()
        }
    }

    @Test
    fun appendToBytesPreservesExistingContainer() {
        val mapper = AvroMapper(userSchema)
        val existing = mapper.formatContainerBytes(listOf(userNode(1L, "a")), AvroCodec.DEFLATE)

        val appended = mapper.appendContainerBytes(existing, listOf(userNode(2L, "b"), userNode(3L, "c")))

        assertContentEquals(existing, appended.copyOf(existing.size))
        assertEquals("deflate", containerCodec(appended))
        assertEquals(listOf(1L, 2L, 3L), containerIds(appended))
        // 新数据块沿用头部 sync 标记时 DataFileReader 才能完整读完
        assertEquals(3, readAllRecordsWithAvro(appended).size)
    }

    @Test
    fun appendEmptyBatchLeavesContainerUnchanged() {
        val mapper = AvroMapper(userSchema)
        val existing = mapper.formatContainerBytes(listOf(userNode(1L, "a")), AvroCodec.NULL)

        val appended = mapper.appendContainerBytes(existing, emptyList())

        assertContentEquals(existing, appended)
        assertEquals(listOf(1L), containerIds(appended))

        val file = tempFile()
        try {
            mapper.appendContainer(file, listOf(userNode(1L, "a")))
            val before = file.readBytes()
            mapper.appendContainer(file, emptyList())
            assertContentEquals(before, file.readBytes())
        } finally {
            file.delete()
        }
    }

    @Test
    fun codecMismatchOnAppendThrowsAvroException() {
        val mapper = AvroMapper(userSchema)
        val existing = mapper.formatContainerBytes(listOf(userNode(1L, "a")), AvroCodec.DEFLATE)

        val bytesError = assertFailsWith<AvroException> {
            mapper.appendContainerBytes(existing, listOf(userNode(2L, "b")), AvroCodec.NULL)
        }
        assertTrue(bytesError.message!!.contains("codec"))

        val file = tempFile()
        try {
            mapper.appendContainer(file, listOf(userNode(1L, "a")), AvroCodec.DEFLATE)
            val fileError = assertFailsWith<AvroException> {
                mapper.appendContainer(file, listOf(userNode(2L, "b")), AvroCodec.SNAPPY)
            }
            assertTrue(fileError.message!!.contains("codec"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun schemaMismatchOnAppendThrowsAvroException() {
        val existing = AvroMapper(userSchema).formatContainerBytes(listOf(userNode(1L, "a")))
        val otherMapper = AvroMapper(userSchemaWithoutNick)

        val error = assertFailsWith<AvroException> {
            otherMapper.appendContainerBytes(existing, listOf(ObjectNode.of("id" to NumberNode.of(2L))))
        }

        assertTrue(error.message!!.contains("schema"))
    }

    @Test
    fun unsupportedContainerCodecThrowsAvroParseException() {
        val output = ByteArrayOutputStream()
        DataFileWriter<GenericRecord>(GenericDatumWriter<GenericRecord>(userSchema)).use { writer ->
            writer.setCodec(CodecFactory.bzip2Codec())
            writer.create(userSchema, output)
            val record = GenericData.Record(userSchema)
            record.put("id", 1L)
            record.put("name", "a")
            record.put("nick", null)
            writer.append(record)
        }

        val mapper = AvroMapper(userSchema)
        val error = assertFailsWith<AvroParseException> { mapper.parseContainerToNodes(output.toByteArray()) }

        assertTrue(error.message!!.contains("bzip2"))
    }

    private fun containerIds(bytes: ByteArray): List<Long> = AvroMapper(userSchema)
        .parseContainerToNodes(bytes)
        .map { it.asObject()["id"]!!.asNumber().toLong() }

    private fun containerCodec(bytes: ByteArray): String? {
        DataFileReader<GenericRecord>(
            SeekableByteArrayInput(bytes),
            GenericDatumReader<GenericRecord>(),
        ).use { reader ->
            return reader.getMetaString(DataFileConstants.CODEC)
        }
    }

    private fun readAllRecordsWithAvro(bytes: ByteArray): List<GenericRecord> {
        val records = ArrayList<GenericRecord>()
        DataFileReader<GenericRecord>(
            SeekableByteArrayInput(bytes),
            GenericDatumReader<GenericRecord>(),
        ).use { reader ->
            while (reader.hasNext()) {
                records.add(reader.next())
            }
        }
        return records
    }

    private fun userNode(id: Long, name: String): ObjectNode = ObjectNode.of(
        "id" to NumberNode.of(id),
        "name" to StringNode.of(name),
        "nick" to NullNode.INSTANCE,
    )

    private fun tempFile(): File = Files.createTempFile("format-avro-codec", ".ocf").toFile()

    private fun schema(json: String): Schema = Schema.Parser().parse(json)

    private val userSchema: Schema = schema(
        """
        {"type":"record","name":"User","namespace":"com.example","fields":[
          {"name":"id","type":"long"},
          {"name":"name","type":"string"},
          {"name":"nick","type":["null","string"],"default":null}
        ]}
        """.trimIndent(),
    )

    private val userSchemaWithoutNick: Schema = schema(
        """
        {"type":"record","name":"User","namespace":"com.example","fields":[
          {"name":"id","type":"long"},
          {"name":"name","type":"string"}
        ]}
        """.trimIndent(),
    )

    private class UserBean {
        var id: Long = 0
        var name: String? = null
        var nick: String? = null
    }
}
