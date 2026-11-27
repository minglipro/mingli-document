package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.avro.exception.AvroException
import com.mingliqiye.document.format.avro.exception.AvroParseException
import com.mingliqiye.document.schema.SchemaType
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Avro wire 编解码测试。
 */
class AvroWireTest {

    @Test
    fun nodeRoundTripCoversScalarsCollectionsAndNull() {
        val mapper = AvroMapper(userSchema)
        val node = sampleUserNode()

        val bytes = mapper.formatBytes(node)
        assertTrue(bytes.isNotEmpty())
        val restored = mapper.parse(bytes, Node::class.java)

        assertEquals(node, restored)
        assertEquals("小于", restored.asObject()["name"]!!.asString())
        assertTrue(restored.asObject()["nick"]!!.isNull)
    }

    @Test
    fun formatBytesAcceptsMap() {
        val mapper = AvroMapper(userSchema)
        val bytes = mapper.formatBytes(
            mapOf(
                "id" to 42L,
                "name" to "小于",
                "active" to true,
                "score" to 2.5,
                "ratio" to 1.5f,
                "tags" to listOf("a", "b"),
                "props" to mapOf("count" to 2L),
                "data" to byteArrayOf(1, 2, -1),
                "nick" to null,
            ),
        )

        assertEquals(sampleUserNode(), mapper.parse(bytes, Node::class.java))
    }

    @Test
    fun beanRoundTrip() {
        val mapper = AvroMapper(userWithAddressSchema)
        val bean = sampleUserBean()

        val restored = mapper.parse(mapper.formatBytes(bean), UserBean::class.java)

        assertEquals(42L, restored.id)
        assertEquals("小于", restored.name)
        assertTrue(restored.active)
        assertEquals(2.5, restored.score)
        assertEquals(1.5f, restored.ratio)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals(mapOf("count" to 2L), restored.props)
        assertContentEquals(byteArrayOf(1, 2, -1), restored.data)
        assertNull(restored.nick)
        assertEquals("杭州", restored.address?.city)
        assertEquals(310000, restored.address?.zip)
    }

    @Test
    fun nestedRecordRoundTrip() {
        val mapper = AvroMapper(userWithAddressSchema)
        val fields = sampleUserFields()
        fields["address"] = ObjectNode.of(
            "city" to StringNode.of("杭州"),
            "zip" to NumberNode.of(310000),
        )
        val node = ObjectNode.from(fields)

        val restored = mapper.parse(mapper.formatBytes(node), Node::class.java)

        assertEquals(node, restored)
        val address = restored.asObject()["address"]!!.asObject()
        assertEquals("杭州", address["city"]!!.asString())
        assertEquals(310000, address["zip"]!!.asNumber().toInt())
    }

    @Test
    fun arrayOfRecordsRoundTrip() {
        val teamSchema = schema(
            """
            {"type":"record","name":"Team","fields":[
              {"name":"members","type":{"type":"array","items":
                {"type":"record","name":"Member","fields":[
                  {"name":"name","type":"string"},
                  {"name":"age","type":"int"}
                ]}}}
            ]}
            """.trimIndent(),
        )
        val mapper = AvroMapper(teamSchema)
        val node = ObjectNode.of(
            "members" to ArrayNode.of(
                ObjectNode.of("name" to StringNode.of("a"), "age" to NumberNode.of(18)),
                ObjectNode.of("name" to StringNode.of("b"), "age" to NumberNode.of(20)),
            ),
        )

        val restored = mapper.parse(mapper.formatBytes(node), Node::class.java)

        assertEquals(node, restored)
        val members = restored.asObject()["members"]!!.asArray()
        assertEquals(2, members.size)
        assertEquals("b", members[1].asObject()["name"]!!.asString())
    }

    @Test
    fun enumFixedAndUnionNullRoundTrip() {
        val mapper = AvroMapper(eventSchema)
        val node = ObjectNode.of(
            "status" to StringNode.of("DONE"),
            "hash" to BinaryNode.of(byteArrayOf(1, 2, 3, 4)),
            "payload" to BinaryNode.of(byteArrayOf(9, 8)),
        )

        assertEquals(node, mapper.parse(mapper.formatBytes(node), Node::class.java))

        val nullPayload = ObjectNode.of(
            "status" to StringNode.of("NEW"),
            "hash" to BinaryNode.of(byteArrayOf(4, 3, 2, 1)),
            "payload" to NullNode.INSTANCE,
        )
        val restored = mapper.parse(mapper.formatBytes(nullPayload), Node::class.java)
        assertEquals(nullPayload, restored)
        assertTrue(restored.asObject()["payload"]!!.isNull)
    }

    @Test
    fun invalidEnumSymbolAndFixedSizeAreRejected() {
        val mapper = AvroMapper(eventSchema)

        assertFailsWith<AvroException> {
            mapper.formatBytes(
                ObjectNode.of(
                    "status" to StringNode.of("UNKNOWN"),
                    "hash" to BinaryNode.of(byteArrayOf(1, 2, 3, 4)),
                    "payload" to NullNode.INSTANCE,
                ),
            )
        }
        assertFailsWith<AvroException> {
            mapper.formatBytes(
                ObjectNode.of(
                    "status" to StringNode.of("NEW"),
                    "hash" to BinaryNode.of(byteArrayOf(1, 2)),
                    "payload" to NullNode.INSTANCE,
                ),
            )
        }
    }

    @Test
    fun missingAndUnknownFieldsAreRejected() {
        val mapper = AvroMapper(userSchema)

        assertFailsWith<AvroException> {
            mapper.formatBytes(ObjectNode.of("id" to NumberNode.of(1L)))
        }

        val fields = sampleUserFields()
        fields["extra"] = StringNode.of("x")
        assertFailsWith<AvroException> { mapper.formatBytes(ObjectNode.from(fields)) }
    }

    @Test
    fun invalidBytesThrowAvroParseException() {
        val mapper = AvroMapper(userSchema)
        val valid = mapper.formatBytes(sampleUserNode())

        assertFailsWith<AvroParseException> { mapper.parse(ByteArray(0), Node::class.java) }
        assertFailsWith<AvroParseException> { mapper.parse(valid.copyOf(valid.size - 1), Node::class.java) }
        assertFailsWith<AvroParseException> { mapper.parse(valid + byteArrayOf(0), Node::class.java) }
    }

    @Test
    fun schemaMismatchThrowsAvroParseException() {
        val intSchema = schema("""{"type":"record","name":"Msg","fields":[{"name":"value","type":"int"}]}""")
        val stringSchema = schema("""{"type":"record","name":"Msg","fields":[{"name":"value","type":"string"}]}""")
        val bytes = AvroMapper(intSchema).formatBytes(ObjectNode.of("value" to NumberNode.of(7)))

        assertFailsWith<AvroParseException> { AvroMapper(stringSchema).parse(bytes, Node::class.java) }
    }

    @Test
    fun textApisAreNotSupported() {
        val mapper = AvroMapper(userSchema)

        assertFailsWith<DocumentSerializationException> { mapper.format(mapOf("id" to 1L)) }
        assertFailsWith<DocumentSerializationException> { mapper.formatPretty(mapOf("id" to 1L)) }
        assertFailsWith<DocumentSerializationException> { mapper.parse("{}", Node::class.java) }
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("{}", DocumentTypeReference.of<Map<String, Any?>>())
        }
    }

    @Test
    fun parseAcceptsDocumentTypeReference() {
        val mapper = AvroMapper(userSchema)
        val bytes = mapper.formatBytes(sampleUserNode())

        assertEquals(sampleUserNode(), mapper.parse(bytes, DocumentTypeReference.of<Node>()))
        assertEquals(42L, mapper.parse(bytes, DocumentTypeReference.of<UserBean>()).id)
    }

    @Test
    fun mapperRequiresRecordSchema() {
        assertFailsWith<IllegalArgumentException> { AvroMapper(Schema.create(Schema.Type.STRING)) }
        assertFailsWith<IllegalArgumentException> { AvroMapper(schema("""{"type":"array","items":"int"}""")) }
        assertFailsWith<IllegalArgumentException> {
            AvroSchema.fromAvroSchema(Schema.create(Schema.Type.STRING))
        }
    }

    @Test
    fun builderCreatesMapper() {
        val mapper = AvroMapper.builder(userSchema).build()

        assertEquals("avro", mapper.formatName)
        assertEquals(userSchema, mapper.schema)
        assertEquals(sampleUserNode(), mapper.parse(mapper.formatBytes(sampleUserNode()), Node::class.java))
    }

    @Test
    fun mapperAcceptsAvroSchema() {
        val avroSchema = AvroSchemaBuilder("SimpleUser")
            .namespace("com.example")
            .field("id", SchemaType.LONG)
            .field("name", SchemaType.STRING)
            .field("nick", SchemaType.STRING, required = false, nullable = true)
            .build()
        val mapper = AvroMapper(avroSchema)
        val bean = SimpleUserBean().apply {
            id = 3L
            name = "n"
            nick = null
        }

        val restored = mapper.parse(mapper.formatBytes(bean), SimpleUserBean::class.java)

        assertEquals(3L, restored.id)
        assertEquals("n", restored.name)
        assertNull(restored.nick)
        assertEquals("avro", AvroMapper(avroSchema.toAvroSchema()).formatName)
    }

    @Test
    fun avroSchemaConvertsToApacheAvroSchema() {
        val avroSchema = AvroSchemaBuilder("User")
            .namespace("com.example")
            .field("id", SchemaType.LONG)
            .field("name", SchemaType.STRING)
            .field("tags", SchemaType.ARRAY)
            .field("props", SchemaType.MAP)
            .field("address", SchemaType.OBJECT)
            .field("data", SchemaType.BYTES)
            .field("amount", SchemaType.DECIMAL)
            .field("nick", SchemaType.STRING, required = false, nullable = true, description = "昵称")
            .build()

        val schema = avroSchema.toAvroSchema()

        assertEquals(Schema.Type.RECORD, schema.type)
        assertEquals("User", schema.name)
        assertEquals("com.example", schema.namespace)
        assertEquals(
            listOf("id", "name", "tags", "props", "address", "data", "amount", "nick"),
            schema.fields.map { it.name() },
        )
        assertEquals(Schema.Type.LONG, schema.getField("id").schema().type)
        assertEquals(Schema.Type.STRING, schema.getField("name").schema().type)
        assertEquals(Schema.Type.ARRAY, schema.getField("tags").schema().type)
        assertEquals(Schema.Type.STRING, schema.getField("tags").schema().elementType.type)
        assertEquals(Schema.Type.MAP, schema.getField("props").schema().type)
        assertEquals("User_address", schema.getField("address").schema().name)
        assertEquals(Schema.Type.BYTES, schema.getField("data").schema().type)
        assertEquals(Schema.Type.BYTES, schema.getField("amount").schema().type)
        val nick = schema.getField("nick")
        assertEquals(Schema.Type.UNION, nick.schema().type)
        assertEquals(listOf(Schema.Type.NULL, Schema.Type.STRING), nick.schema().types.map { it.type })
        assertTrue(nick.hasDefaultValue())
        assertEquals("昵称", nick.doc())
        assertEquals(org.apache.avro.JsonProperties.NULL_VALUE, nick.defaultVal())
    }

    @Test
    fun fromAvroSchemaMapsRecordFields() {
        val converted = AvroSchema.fromAvroSchema(userWithAddressSchema)

        assertEquals("User", converted.name)
        assertEquals("com.example", converted.namespace)
        assertEquals("long", converted.avroTypeName("id"))
        assertEquals("string", converted.avroTypeName("name"))
        assertEquals("double", converted.avroTypeName("ratio"))
        assertEquals("array", converted.avroTypeName("tags"))
        assertEquals("map", converted.avroTypeName("props"))
        assertEquals(SchemaType.BYTES, converted.field("data")?.type)
        assertEquals(SchemaType.OBJECT, converted.field("address")?.type)
        assertTrue(converted.field("address")?.nullable == true)
        assertTrue(converted.field("address")?.required == false)
        assertTrue(converted.field("nick")?.nullable == true)
        assertEquals(10, converted.fields.size)
    }

    @Test
    fun fromAvroSchemaNormalizesComplexTypes() {
        val converted = AvroSchema.fromAvroSchema(eventSchema)

        assertEquals(SchemaType.STRING, converted.field("status")?.type)
        assertEquals(SchemaType.BYTES, converted.field("hash")?.type)
        assertEquals(SchemaType.BYTES, converted.field("payload")?.type)
        assertTrue(converted.field("payload")?.nullable == true)

        val decimalSchema = schema(
            """
            {"type":"record","name":"Money","fields":[
              {"name":"amount","type":{"type":"bytes","logicalType":"decimal","precision":10,"scale":2}}
            ]}
            """.trimIndent(),
        )
        assertEquals(SchemaType.DECIMAL, AvroSchema.fromAvroSchema(decimalSchema).field("amount")?.type)
    }

    @Test
    fun bytesAreStandardAvroCompatible() {
        val mapper = AvroMapper(userSchema)
        val bytes = mapper.formatBytes(sampleUserNode())

        val record = GenericDatumReader<Any?>(userSchema).read(
            null,
            DecoderFactory.get().binaryDecoder(bytes, null),
        ) as GenericRecord
        assertEquals(42L, record.get("id"))
        assertEquals("小于", record.get("name").toString())
        assertEquals(true, record.get("active"))
        assertEquals(2.5, record.get("score"))
        assertEquals(1.5f, record.get("ratio"))
        assertEquals(listOf("a", "b"), (record.get("tags") as List<*>).map { it.toString() })
        val props = record.get("props") as Map<*, *>
        assertEquals(1, props.size)
        assertEquals("count", props.entries.single().key.toString())
        assertEquals(2L, props.entries.single().value)
        assertContentEquals(byteArrayOf(1, 2, -1), byteBufferBytes(record.get("data")))
        assertNull(record.get("nick"))

        val output = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().binaryEncoder(output, null)
        GenericDatumWriter<Any?>(userSchema).write(record, encoder)
        encoder.flush()
        assertEquals(sampleUserNode(), mapper.parse(output.toByteArray(), Node::class.java))
    }

    @Test
    fun nonNullUnionPicksMatchingBranch() {
        val unionSchema = schema(
            """{"type":"record","name":"Value","fields":[{"name":"value","type":["int","string"]}]}""",
        )
        val mapper = AvroMapper(unionSchema)
        val intNode = ObjectNode.of("value" to NumberNode.of(7))
        val stringNode = ObjectNode.of("value" to StringNode.of("x"))

        assertEquals(intNode, mapper.parse(mapper.formatBytes(intNode), Node::class.java))
        assertEquals(stringNode, mapper.parse(mapper.formatBytes(stringNode), Node::class.java))
    }

    private fun byteBufferBytes(value: Any?): ByteArray {
        val buffer = (value as ByteBuffer).duplicate()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private fun sampleUserFields(): LinkedHashMap<String, Node> = linkedMapOf(
        "id" to NumberNode.of(42L),
        "name" to StringNode.of("小于"),
        "active" to BooleanNode.of(true),
        "score" to NumberNode.of(2.5),
        "ratio" to NumberNode.of(1.5f),
        "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
        "props" to ObjectNode.of("count" to NumberNode.of(2L)),
        "data" to BinaryNode.of(byteArrayOf(1, 2, -1)),
        "nick" to NullNode.INSTANCE,
    )

    private fun sampleUserNode(): ObjectNode = ObjectNode.from(sampleUserFields())

    private fun sampleUserBean(): UserBean = UserBean().apply {
        id = 42L
        name = "小于"
        active = true
        score = 2.5
        ratio = 1.5f
        tags = listOf("a", "b")
        props = mapOf("count" to 2L)
        data = byteArrayOf(1, 2, -1)
        nick = null
        address = AddressBean().apply {
            city = "杭州"
            zip = 310000
        }
    }

    private fun schema(json: String): Schema = Schema.Parser().parse(json)

    private val userSchema: Schema = schema(
        """
        {"type":"record","name":"User","namespace":"com.example","fields":[
          {"name":"id","type":"long"},
          {"name":"name","type":"string"},
          {"name":"active","type":"boolean"},
          {"name":"score","type":"double"},
          {"name":"ratio","type":"float"},
          {"name":"tags","type":{"type":"array","items":"string"}},
          {"name":"props","type":{"type":"map","values":"long"}},
          {"name":"data","type":"bytes"},
          {"name":"nick","type":["null","string"],"default":null}
        ]}
        """.trimIndent(),
    )

    private val userWithAddressSchema: Schema = schema(
        """
        {"type":"record","name":"User","namespace":"com.example","fields":[
          {"name":"id","type":"long"},
          {"name":"name","type":"string"},
          {"name":"active","type":"boolean"},
          {"name":"score","type":"double"},
          {"name":"ratio","type":"float"},
          {"name":"tags","type":{"type":"array","items":"string"}},
          {"name":"props","type":{"type":"map","values":"long"}},
          {"name":"data","type":"bytes"},
          {"name":"nick","type":["null","string"],"default":null},
          {"name":"address","type":["null",{"type":"record","name":"Address","fields":[
            {"name":"city","type":"string"},
            {"name":"zip","type":"int"}
          ]}],"default":null}
        ]}
        """.trimIndent(),
    )

    private val eventSchema: Schema = schema(
        """
        {"type":"record","name":"Event","fields":[
          {"name":"status","type":{"type":"enum","name":"Status","symbols":["NEW","DONE"]}},
          {"name":"hash","type":{"type":"fixed","name":"Hash","size":4}},
          {"name":"payload","type":["null","bytes"],"default":null}
        ]}
        """.trimIndent(),
    )

    private class UserBean {
        var id: Long = 0
        var name: String? = null
        var active: Boolean = false
        var score: Double = 0.0
        var ratio: Float = 0f
        var tags: List<String> = emptyList()
        var props: Map<String, Long> = emptyMap()
        var data: ByteArray? = null
        var nick: String? = null
        var address: AddressBean? = null
    }

    private class AddressBean {
        var city: String? = null
        var zip: Int = 0
    }

    private class SimpleUserBean {
        var id: Long = 0
        var name: String? = null
        var nick: String? = null
    }
}
