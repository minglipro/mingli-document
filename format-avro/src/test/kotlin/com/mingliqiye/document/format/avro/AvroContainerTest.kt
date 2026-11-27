package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.avro.exception.AvroException
import com.mingliqiye.document.format.avro.exception.AvroParseException
import com.mingliqiye.document.schema.SchemaType
import org.apache.avro.Schema
import org.apache.avro.file.DataFileConstants
import org.apache.avro.file.DataFileReader
import org.apache.avro.file.DataFileWriter
import org.apache.avro.file.SeekableByteArrayInput
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Avro Object Container File（OCF）与 Schema 演进测试。
 */
class AvroContainerTest {

    @Test
    fun containerRoundTripsMultipleRecords() {
        val mapper = AvroMapper(userSchema)
        val nodes = listOf(userNode(1L, "a"), userNode(2L, "b"), userNode(3L, "c"))

        val bytes = mapper.formatContainerBytes(nodes)

        assertContentEquals(DataFileConstants.MAGIC, bytes.copyOf(DataFileConstants.MAGIC.size))
        assertEquals(nodes, mapper.parseContainerToNodes(bytes))
    }

    @Test
    fun containerEmbedsWriterSchema() {
        val writer = AvroMapper(userSchema)
        val bytes = writer.formatContainerBytes(listOf(userNode(1L, "a"), userNode(2L, "b")))

        val fileReader = DataFileReader<GenericRecord>(
            SeekableByteArrayInput(bytes),
            GenericDatumReader<GenericRecord>(),
        )
        try {
            assertEquals(userSchema, fileReader.schema)
            val records = ArrayList<GenericRecord>()
            while (fileReader.hasNext()) {
                records.add(fileReader.next())
            }
            assertEquals(2, records.size)
            assertEquals(1L, records[0].get("id"))
            assertEquals("a", records[0].get("name").toString())
            assertEquals("b", records[1].get("name").toString())
        } finally {
            fileReader.close()
        }
    }

    @Test
    fun emptyContainerRoundTrips() {
        val mapper = AvroMapper(userSchema)
        val bytes = mapper.formatContainerBytes(emptyList())

        assertContentEquals(DataFileConstants.MAGIC, bytes.copyOf(DataFileConstants.MAGIC.size))
        assertEquals(0, mapper.parseContainerToNodes(bytes).size)
    }

    @Test
    fun badContainerThrowsAvroParseException() {
        val mapper = AvroMapper(userSchema)

        assertFailsWith<AvroParseException> { mapper.parseContainerToNodes(ByteArray(0)) }
        assertFailsWith<AvroParseException> { mapper.parseContainerToNodes(byteArrayOf(1, 2, 3, 4)) }

        val valid = mapper.formatContainerBytes(listOf(userNode(1L, "a")))
        assertFailsWith<AvroParseException> { mapper.parseContainerToNodes(valid.copyOf(valid.size / 2)) }

        val corruptedMagic = valid.copyOf()
        corruptedMagic[0] = 0
        assertFailsWith<AvroParseException> { mapper.parseContainerToNodes(corruptedMagic) }
    }

    @Test
    fun containerRejectsNonRecordValues() {
        val mapper = AvroMapper(userSchema)

        assertFailsWith<AvroException> { mapper.formatContainerBytes(listOf("not-a-record")) }
        assertFailsWith<AvroException> { mapper.formatContainerBytes(listOf(42L)) }
    }

    @Test
    fun parseContainerAcceptsClassAndTypeReference() {
        val mapper = AvroMapper(userSchema)
        val bytes = mapper.formatContainerBytes(
            listOf(
                UserBean().apply {
                    id = 1L
                    name = "a"
                    nick = null
                },
                UserBean().apply {
                    id = 2L
                    name = "b"
                    nick = "n"
                },
            ),
        )

        val beans = mapper.parseContainer(bytes, UserBean::class.java)
        assertEquals(listOf(1L, 2L), beans.map { it.id })
        assertEquals(listOf("a", "b"), beans.map { it.name })
        assertEquals(listOf(null, "n"), beans.map { it.nick })

        val typed = mapper.parseContainer(bytes, DocumentTypeReference.of<UserBean>())
        assertEquals(2, typed.size)
        assertEquals("n", typed[1].nick)
    }

    @Test
    fun readsStandardAvroContainerFile() {
        val output = ByteArrayOutputStream()
        DataFileWriter<GenericRecord>(GenericDatumWriter<GenericRecord>(userSchema)).use { writer ->
            writer.create(userSchema, output)
            val record = GenericData.Record(userSchema)
            record.put("id", 11L)
            record.put("name", "std")
            record.put("nick", null)
            writer.append(record)
        }

        val nodes = AvroMapper(userSchema).parseContainerToNodes(output.toByteArray())

        assertEquals(1, nodes.size)
        assertEquals(11L, nodes[0].asObject()["id"]!!.asNumber().toLong())
        assertEquals("std", nodes[0].asObject()["name"]!!.asString())
    }

    @Test
    fun readerSchemaAppliesAddedDefaultsDeletedFieldsAndPromotions() {
        val rawBytes = AvroMapper(evolvingWriterSchema).formatBytes(evolvingWriterNode(1L, "a"))
        val evolving = AvroMapper(evolvingWriterSchema, evolvingReaderSchema)

        val evolved = evolving.parse(rawBytes, Node::class.java).asObject()

        assertEquals(1L, evolved["id"]!!.asNumber().toLong())
        assertEquals("a", evolved["name"]!!.asString())
        assertEquals(7L, evolved["score"]!!.asNumber().toLong())
        assertEquals(42, evolved["age"]!!.asNumber().toInt())
        assertTrue(evolved["tag"]!!.isNull)
        assertNull(evolved["legacy"])
    }

    @Test
    fun containerAppliesReaderSchemaEvolution() {
        val writer = AvroMapper(evolvingWriterSchema)
        val bytes = writer.formatContainerBytes(
            listOf(evolvingWriterNode(1L, "a"), evolvingWriterNode(2L, "b")),
        )

        val evolving = AvroMapper(evolvingWriterSchema, evolvingReaderSchema)
        val evolved = evolving.parseContainerToNodes(bytes)
        assertEquals(2, evolved.size)
        assertEquals(listOf(1L, 2L), evolved.map { it.asObject()["id"]!!.asNumber().toLong() })
        assertEquals(listOf(42, 42), evolved.map { it.asObject()["age"]!!.asNumber().toInt() })
        assertEquals(listOf(7L, 7L), evolved.map { it.asObject()["score"]!!.asNumber().toLong() })
        assertNull(evolved[0].asObject()["legacy"])

        // 只用 reader Schema 的 mapper 也能读旧容器：writer Schema 取自容器头部
        val readerOnly = AvroMapper(evolvingReaderSchema)
        val readBack = readerOnly.parseContainerToNodes(bytes)
        assertEquals(2, readBack.size)
        assertEquals(42, readBack[1].asObject()["age"]!!.asNumber().toInt())
    }

    @Test
    fun containerEvolutionSupportsBeanTarget() {
        val bytes = AvroMapper(evolvingWriterSchema).formatContainerBytes(
            listOf(evolvingWriterNode(3L, "c")),
        )

        val beans = AvroMapper(evolvingWriterSchema, evolvingReaderSchema)
            .parseContainer(bytes, EvolvedBean::class.java)

        assertEquals(1, beans.size)
        assertEquals(3L, beans[0].id)
        assertEquals("c", beans[0].name)
        assertEquals(7L, beans[0].score)
        assertEquals(42, beans[0].age)
        assertNull(beans[0].tag)
    }

    @Test
    fun incompatibleSchemaEvolutionThrowsAvroParseException() {
        val writerNode = ObjectNode.of("value" to StringNode.of("x"))

        val rawBytes = AvroMapper(incompatibleWriterSchema).formatBytes(writerNode)
        assertFailsWith<AvroParseException> {
            AvroMapper(incompatibleWriterSchema, incompatibleReaderSchema).parse(rawBytes, Node::class.java)
        }

        val containerBytes = AvroMapper(incompatibleWriterSchema).formatContainerBytes(listOf(writerNode))
        assertFailsWith<AvroParseException> {
            AvroMapper(incompatibleWriterSchema, incompatibleReaderSchema)
                .parseContainerToNodes(containerBytes)
        }
    }

    @Test
    fun singleRootApiStillWorks() {
        val mapper = AvroMapper(userSchema)
        val node = userNode(7L, "single")

        assertEquals(node, mapper.parse(mapper.formatBytes(node), Node::class.java))
        assertEquals(7L, mapper.parse(mapper.formatBytes(node), UserBean::class.java).id)
        assertEquals("avro", mapper.formatName)
        assertEquals(userSchema, mapper.schema)
        assertEquals(userSchema, mapper.writerSchema)
        assertEquals(userSchema, mapper.readerSchema)
    }

    @Test
    fun dualSchemaMapperExposesSchemas() {
        val mapper = AvroMapper(evolvingWriterSchema, evolvingReaderSchema)

        assertEquals(evolvingWriterSchema, mapper.writerSchema)
        assertEquals(evolvingReaderSchema, mapper.readerSchema)
        assertEquals(evolvingWriterSchema, mapper.schema)

        val built = AvroMapper.builder(evolvingWriterSchema, evolvingReaderSchema).build()
        assertEquals(evolvingWriterSchema, built.writerSchema)
        assertEquals(evolvingReaderSchema, built.readerSchema)

        val dualBytes = mapper.formatBytes(evolvingWriterNode(9L, "z"))
        val evolved = mapper.parse(dualBytes, Node::class.java).asObject()
        assertEquals(9L, evolved["id"]!!.asNumber().toLong())
        assertEquals(42, evolved["age"]!!.asNumber().toInt())
    }

    @Test
    fun avroSchemaDualConstructorSupportsNullableEvolution() {
        val writerSchema = AvroSchemaBuilder("Simple")
            .field("id", SchemaType.LONG)
            .build()
        val readerSchema = AvroSchemaBuilder("Simple")
            .field("id", SchemaType.LONG)
            .field("nick", SchemaType.STRING, required = false, nullable = true)
            .build()

        val rawBytes = AvroMapper(writerSchema).formatBytes(ObjectNode.of("id" to NumberNode.of(5L)))
        val evolved = AvroMapper(writerSchema, readerSchema).parse(rawBytes, Node::class.java).asObject()

        assertEquals(5L, evolved["id"]!!.asNumber().toLong())
        assertTrue(evolved["nick"]!!.isNull)
    }

    private fun userNode(id: Long, name: String): ObjectNode = ObjectNode.of(
        "id" to NumberNode.of(id),
        "name" to StringNode.of(name),
        "nick" to NullNode.INSTANCE,
    )

    private fun evolvingWriterNode(id: Long, name: String): ObjectNode = ObjectNode.of(
        "id" to NumberNode.of(id),
        "name" to StringNode.of(name),
        "score" to NumberNode.of(7),
        "legacy" to StringNode.of("old"),
    )

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

    private val evolvingWriterSchema: Schema = schema(
        """
        {"type":"record","name":"Evo","fields":[
          {"name":"id","type":"long"},
          {"name":"name","type":"string"},
          {"name":"score","type":"int"},
          {"name":"legacy","type":"string"}
        ]}
        """.trimIndent(),
    )

    private val evolvingReaderSchema: Schema = schema(
        """
        {"type":"record","name":"Evo","fields":[
          {"name":"id","type":"long"},
          {"name":"name","type":"string"},
          {"name":"score","type":"long"},
          {"name":"age","type":"int","default":42},
          {"name":"tag","type":["null","string"],"default":null}
        ]}
        """.trimIndent(),
    )

    private val incompatibleWriterSchema: Schema = schema(
        """{"type":"record","name":"Conflict","fields":[{"name":"value","type":"string"}]}""",
    )

    private val incompatibleReaderSchema: Schema = schema(
        """{"type":"record","name":"Conflict","fields":[{"name":"value","type":"int"}]}""",
    )

    private class UserBean {
        var id: Long = 0
        var name: String? = null
        var nick: String? = null
    }

    private class EvolvedBean {
        var id: Long = 0
        var name: String? = null
        var score: Long = 0
        var age: Int = 0
        var tag: String? = null
    }
}
