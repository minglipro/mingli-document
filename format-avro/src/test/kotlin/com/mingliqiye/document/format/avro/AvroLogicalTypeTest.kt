package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.avro.exception.AvroException
import com.mingliqiye.document.format.avro.exception.AvroParseException
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericFixed
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Avro logical type 读写测试。
 */
class AvroLogicalTypeTest {

    @Test
    fun dateRoundTripsIsoString() {
        val schema = schemaOf(dateType)
        val node = valueNode(StringNode.of("2024-01-02"))

        val restored = roundTrip(schema, node)

        assertEquals(node, restored)
        assertEquals("2024-01-02", restored.asObject()["value"]!!.asString())

        val raw = rawRecord(schema, format(schema, node))
        assertEquals(LocalDate.parse("2024-01-02").toEpochDay().toInt(), raw.get("value"))

        val standard = rawRecordBytes(schema, GenericData.Record(schema).apply { put("value", 0) })
        assertEquals("1970-01-01", parse(schema, standard).asObject()["value"]!!.asString())
    }

    @Test
    fun timeMillisRoundTripsIsoString() {
        val schema = schemaOf(timeMillisType)
        val node = valueNode(StringNode.of("10:15:30.123"))

        assertEquals(node, roundTrip(schema, node))

        val expectedMillis = (LocalTime.parse("10:15:30.123").toNanoOfDay() / 1_000_000L).toInt()
        val raw = rawRecord(schema, format(schema, node))
        assertEquals(expectedMillis, raw.get("value"))

        val standard = rawRecordBytes(
            schema,
            GenericData.Record(schema).apply { put("value", LocalTime.parse("00:00:00.001").toNanoOfDay().toInt() / 1_000_000) },
        )
        assertEquals("00:00:00.001", parse(schema, standard).asObject()["value"]!!.asString())
    }

    @Test
    fun timeMicrosRoundTripsIsoString() {
        val schema = schemaOf(timeMicrosType)
        val node = valueNode(StringNode.of("10:15:30.123456"))

        assertEquals(node, roundTrip(schema, node))

        val expectedMicros = LocalTime.parse("10:15:30.123456").toNanoOfDay() / 1_000L
        val raw = rawRecord(schema, format(schema, node))
        assertEquals(expectedMicros, raw.get("value"))
    }

    @Test
    fun timestampMillisRoundTripsIsoString() {
        val schema = schemaOf(timestampMillisType)
        val node = valueNode(StringNode.of("2024-01-02T03:04:05.123Z"))

        assertEquals(node, roundTrip(schema, node))

        val expectedMillis = Instant.parse("2024-01-02T03:04:05.123Z").toEpochMilli()
        val raw = rawRecord(schema, format(schema, node))
        assertEquals(expectedMillis, raw.get("value"))

        val standard = rawRecordBytes(schema, GenericData.Record(schema).apply { put("value", 0L) })
        assertEquals(
            "1970-01-01T00:00:00.000Z",
            parse(schema, standard).asObject()["value"]!!.asString(),
        )
    }

    @Test
    fun timestampMicrosRoundTripsIsoString() {
        val schema = schemaOf(timestampMicrosType)
        val node = valueNode(StringNode.of("2024-01-02T03:04:05.123456Z"))

        assertEquals(node, roundTrip(schema, node))

        val expectedMicros = instantMicros(Instant.parse("2024-01-02T03:04:05.123456Z"))
        val raw = rawRecord(schema, format(schema, node))
        assertEquals(expectedMicros, raw.get("value"))
    }

    @Test
    fun localTimestampMillisRoundTripsIsoString() {
        val schema = schemaOf(localTimestampMillisType)
        val node = valueNode(StringNode.of("2024-01-02T03:04:05.123"))

        assertEquals(node, roundTrip(schema, node))

        val expectedMillis = LocalDateTime.parse("2024-01-02T03:04:05.123")
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
        val raw = rawRecord(schema, format(schema, node))
        assertEquals(expectedMillis, raw.get("value"))
    }

    @Test
    fun localTimestampMicrosRoundTripsIsoString() {
        val schema = schemaOf(localTimestampMicrosType)
        val node = valueNode(StringNode.of("2024-01-02T03:04:05.123456"))

        assertEquals(node, roundTrip(schema, node))

        val expectedMicros = instantMicros(
            LocalDateTime.parse("2024-01-02T03:04:05.123456").toInstant(ZoneOffset.UTC),
        )
        val raw = rawRecord(schema, format(schema, node))
        assertEquals(expectedMicros, raw.get("value"))
    }

    @Test
    fun decimalOnBytesRoundTrips() {
        val schema = schemaOf(decimalType(10, 2))
        val node = valueNode(NumberNode.of(BigDecimal("12345.67")))

        val restored = roundTrip(schema, node)

        assertEquals(node, restored)
        assertDecimal(restored.asObject()["value"]!!, "12345.67", 2)

        val raw = rawRecord(schema, format(schema, node))
        assertEquals(BigInteger("1234567"), BigInteger(bytesOf(raw.get("value"))))

        val standard = rawRecordBytes(
            schema,
            GenericData.Record(schema).apply { put("value", ByteBuffer.wrap(BigInteger("1234567").toByteArray())) },
        )
        assertDecimal(parse(schema, standard).asObject()["value"]!!, "12345.67", 2)
    }

    @Test
    fun decimalOnFixedRoundTrips() {
        val schema = schemaOf(fixedDecimalType(10, 2, 8))
        val node = valueNode(NumberNode.of(BigDecimal("-12345.67")))

        val restored = roundTrip(schema, node)

        assertDecimal(restored.asObject()["value"]!!, "-12345.67", 2)

        val raw = rawRecord(schema, format(schema, node))
        val fixed = raw.get("value") as GenericFixed
        assertEquals(8, fixed.bytes().size)
        assertEquals(BigInteger("-1234567"), BigInteger(fixed.bytes()))

        val standard = rawRecordBytes(
            schema,
            GenericData.Record(schema).apply { put("value", fixedFor(schema, BigInteger("1234567"))) },
        )
        assertDecimal(parse(schema, standard).asObject()["value"]!!, "12345.67", 2)
    }

    @Test
    fun uuidRoundTripsCanonicalString() {
        val schema = schemaOf(uuidType)
        val canonical = "123e4567-e89b-12d3-a456-426614174000"
        val node = valueNode(StringNode.of(canonical))

        assertEquals(node, roundTrip(schema, node))

        val raw = rawRecord(schema, format(schema, node))
        assertEquals(canonical, raw.get("value").toString())

        // 大写 UUID 按 canonical 小写写出
        val uppercase = valueNode(StringNode.of(canonical.uppercase()))
        assertEquals(node, roundTrip(schema, uppercase))
    }

    @Test
    fun durationRoundTripsObject() {
        val schema = schemaOf(durationType)
        val node = valueNode(
            ObjectNode.of(
                "months" to NumberNode.of(1),
                "days" to NumberNode.of(2),
                "millis" to NumberNode.of(3),
            ),
        )

        val restored = roundTrip(schema, node)

        assertEquals(node, restored)
        val raw = rawRecord(schema, format(schema, node))
        val fixed = raw.get("value") as GenericFixed
        assertEquals(byteArrayOf(1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0).toList(), fixed.bytes().toList())

        // 标准 Avro 数据（无符号 32 位）→ months/days/millis 对象
        val bytes = ByteArray(AVRO_DURATION_SIZE)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(-1).putInt(-1).putInt(-1)
        val standard = rawRecordBytes(
            schema,
            GenericData.Record(schema).apply { put("value", GenericData.Fixed(schema.fieldSchema(), bytes)) },
        )
        val duration = parse(schema, standard).asObject()["value"]!!.asObject()
        assertEquals(4294967295L, duration["months"]!!.asNumber().toLong())
        assertEquals(4294967295L, duration["days"]!!.asNumber().toLong())
        assertEquals(4294967295L, duration["millis"]!!.asNumber().toLong())

        // 12 字节二进制也可作为 duration 输入
        assertEquals(node, roundTrip(schema, valueNode(BinaryNode.of(byteArrayOf(1, 0, 0, 0, 2, 0, 0, 0, 3, 0, 0, 0)))))

        // 缺省字段按 0 写出
        val partial = roundTrip(schema, valueNode(ObjectNode.of("months" to NumberNode.of(1)))).asObject()["value"]!!.asObject()
        assertEquals(0L, partial["days"]!!.asNumber().toLong())
        assertEquals(0L, partial["millis"]!!.asNumber().toLong())
    }

    @Test
    fun nullableUnionPreservesNullAndValues() {
        val schema = recordSchema(
            field("date", """["null",$dateType]""", "null"),
            field("timestamp", """["null",$timestampMillisType]""", "null"),
            field("amount", """["null",${decimalType(8, 2)}]""", "null"),
        )
        val withValues = ObjectNode.of(
            "date" to StringNode.of("2024-01-02"),
            "timestamp" to NullNode.INSTANCE,
            "amount" to NumberNode.of(BigDecimal("1.23")),
        )
        val withNulls = ObjectNode.of(
            "date" to NullNode.INSTANCE,
            "timestamp" to NullNode.INSTANCE,
            "amount" to NullNode.INSTANCE,
        )

        assertEquals(withValues, roundTrip(schema, withValues))
        assertEquals(withNulls, roundTrip(schema, withNulls))
        // 所有字段都有 null 默认值：缺字段时写出 null
        assertEquals(withNulls, roundTrip(schema, ObjectNode.of()))
    }

    @Test
    fun nestedCollectionsAndRecordsPreserveLogicalTypes() {
        val schema = Schema.Parser().parse(
            """
            {"type":"record","name":"NestedLogical","fields":[
              {"name":"dates","type":{"type":"array","items":$dateType}},
              {"name":"events","type":{"type":"map","values":$timestampMicrosType}},
              {"name":"detail","type":{"type":"record","name":"Detail","fields":[
                {"name":"id","type":$uuidType},
                {"name":"amount","type":${fixedDecimalType(10, 2, 8)}},
                {"name":"elapsed","type":${durationType.replace("\"name\":\"Elapsed\"", "\"name\":\"DetailElapsed\"")}}
              ]}}
            ]}
            """.trimIndent(),
        )
        val node = ObjectNode.of(
            "dates" to ArrayNode.of(StringNode.of("2024-01-02"), StringNode.of("2024-01-03")),
            "events" to ObjectNode.of(
                "start" to StringNode.of("2024-01-02T03:04:05.123456Z"),
            ),
            "detail" to ObjectNode.of(
                "id" to StringNode.of("123e4567-e89b-12d3-a456-426614174000"),
                "amount" to NumberNode.of(BigDecimal("12345.67")),
                "elapsed" to ObjectNode.of(
                    "months" to NumberNode.of(1),
                    "days" to NumberNode.of(2),
                    "millis" to NumberNode.of(3),
                ),
            ),
        )

        assertEquals(node, roundTrip(schema, node))
    }

    @Test
    fun containerRoundTripsLogicalTypes() {
        val schema = recordSchema(
            field("date", dateType),
            field("timestamp", timestampMillisType),
            field("uuid", uuidType),
            field("amount", decimalType(10, 2)),
            field("elapsed", durationType),
        )
        val mapper = AvroMapper(schema)
        val nodes = listOf(
            ObjectNode.of(
                "date" to StringNode.of("2024-01-02"),
                "timestamp" to StringNode.of("2024-01-02T03:04:05.123Z"),
                "uuid" to StringNode.of("123e4567-e89b-12d3-a456-426614174000"),
                "amount" to NumberNode.of(BigDecimal("12345.67")),
                "elapsed" to ObjectNode.of("months" to NumberNode.of(1), "days" to NumberNode.of(2), "millis" to NumberNode.of(3)),
            ),
            ObjectNode.of(
                "date" to StringNode.of("1970-01-01"),
                "timestamp" to StringNode.of("1970-01-01T00:00:00.000Z"),
                "uuid" to StringNode.of("00000000-0000-0000-0000-000000000000"),
                "amount" to NumberNode.of(BigDecimal("-0.01")),
                "elapsed" to ObjectNode.of("months" to NumberNode.of(0), "days" to NumberNode.of(0), "millis" to NumberNode.of(0)),
            ),
        )

        val bytes = mapper.formatContainerBytes(nodes)

        assertEquals(nodes, mapper.parseContainerToNodes(bytes))
    }

    @Test
    fun beanRoundTripsLogicalTypes() {
        val schema = recordSchema(
            field("date", dateType),
            field("timestamp", timestampMillisType),
            field("uuid", uuidType),
            field("amount", decimalType(10, 2)),
        )
        val mapper = AvroMapper(schema)
        val bean = LogicalBean().apply {
            this.date = "2024-01-02"
            timestamp = "2024-01-02T03:04:05.123Z"
            uuid = "123e4567-e89b-12d3-a456-426614174000"
            amount = BigDecimal("12345.67")
        }

        val restored = mapper.parse(mapper.formatBytes(bean), LogicalBean::class.java)

        assertEquals(bean.date, restored.date)
        assertEquals(bean.timestamp, restored.timestamp)
        assertEquals(bean.uuid, restored.uuid)
        assertEquals(0, bean.amount!!.compareTo(restored.amount))
    }

    @Test
    fun readerLogicalTypeAppliesToPlainWriterSchema() {
        val plainSchema = recordSchema(field("value", "\"long\""))
        val logicalSchema = recordSchema(field("value", timestampMillisType))
        val epochMillis = Instant.parse("2024-01-02T03:04:05.123Z").toEpochMilli()

        val plainBytes = AvroMapper(plainSchema).formatBytes(valueNode(NumberNode.of(epochMillis)))
        val evolved = AvroMapper(plainSchema, logicalSchema).parse(plainBytes, Node::class.java)
        assertEquals("2024-01-02T03:04:05.123Z", evolved.asObject()["value"]!!.asString())

        val logicalBytes = AvroMapper(logicalSchema).formatBytes(valueNode(StringNode.of("2024-01-02T03:04:05.123Z")))
        val downgraded = AvroMapper(logicalSchema, plainSchema).parse(logicalBytes, Node::class.java)
        assertEquals(epochMillis, downedNumber(downgraded))
    }

    @Test
    fun epochNumbersAreAcceptedOnWrite() {
        val dateNode = roundTrip(schemaOf(dateType), valueNode(NumberNode.of(1)))
        assertEquals("1970-01-02", dateNode.asObject()["value"]!!.asString())

        val timeNode = roundTrip(schemaOf(timeMillisType), valueNode(NumberNode.of(1_000)))
        assertEquals("00:00:01.000", timeNode.asObject()["value"]!!.asString())

        val timestampNode = roundTrip(
            schemaOf(timestampMillisType),
            valueNode(NumberNode.of(Instant.parse("2024-01-02T03:04:05.123Z").toEpochMilli())),
        )
        assertEquals("2024-01-02T03:04:05.123Z", timestampNode.asObject()["value"]!!.asString())
    }

    @Test
    fun unknownLogicalTypeFallsBackToBaseType() {
        val schema = recordSchema(field("value", """{"type":"long","logicalType":"custom-epoch"}"""))
        val mapper = AvroMapper(schema)

        assertNull(AvroLogicalType.fromSchema(schema.fieldSchema()))

        val node = ObjectNode.of("value" to NumberNode.of(5L))
        assertEquals(node, mapper.parse(mapper.formatBytes(node), Node::class.java))
        assertEquals(5L, rawRecord(schema, mapper.formatBytes(node)).get("value"))
    }

    @Test
    fun logicalTypeApiRecognizesSupportedSchemas() {
        assertEquals(10, AvroLogicalType.entries.size)
        assertEquals(AvroLogicalType.DATE, AvroLogicalType.fromTypeName(" date "))
        assertEquals(AvroLogicalType.DURATION, AvroLogicalType.fromTypeName("duration"))
        assertNull(AvroLogicalType.fromTypeName("unknown"))
        assertNull(AvroLogicalType.fromSchema(fieldSchema(""""int"""")))

        val cases = listOf(
            dateType to AvroLogicalType.DATE,
            timeMillisType to AvroLogicalType.TIME_MILLIS,
            timeMicrosType to AvroLogicalType.TIME_MICROS,
            timestampMillisType to AvroLogicalType.TIMESTAMP_MILLIS,
            timestampMicrosType to AvroLogicalType.TIMESTAMP_MICROS,
            localTimestampMillisType to AvroLogicalType.LOCAL_TIMESTAMP_MILLIS,
            localTimestampMicrosType to AvroLogicalType.LOCAL_TIMESTAMP_MICROS,
            decimalType(10, 2) to AvroLogicalType.DECIMAL,
            fixedDecimalType(10, 2, 8) to AvroLogicalType.DECIMAL,
            uuidType to AvroLogicalType.UUID,
            durationType to AvroLogicalType.DURATION,
        )
        for ((typeJson, expected) in cases) {
            assertEquals(expected, AvroLogicalType.fromSchema(fieldSchema(typeJson)), typeJson)
        }
    }

    @Test
    fun preEpochTimestampsAndTimeBoundariesRoundTrip() {
        val millisNode = valueNode(StringNode.of("1969-12-31T23:59:59.999Z"))
        assertEquals(millisNode, roundTrip(schemaOf(timestampMillisType), millisNode))
        assertEquals(-1L, rawRecord(schemaOf(timestampMillisType), format(schemaOf(timestampMillisType), millisNode)).get("value"))

        val microsNode = valueNode(StringNode.of("1969-12-31T23:59:59.999999Z"))
        assertEquals(microsNode, roundTrip(schemaOf(timestampMicrosType), microsNode))
        assertEquals(-1L, rawRecord(schemaOf(timestampMicrosType), format(schemaOf(timestampMicrosType), microsNode)).get("value"))

        val localNode = valueNode(StringNode.of("1969-12-31T23:59:59.999"))
        assertEquals(localNode, roundTrip(schemaOf(localTimestampMillisType), localNode))

        val endOfDayNode = valueNode(StringNode.of("23:59:59.999"))
        assertEquals(endOfDayNode, roundTrip(schemaOf(timeMillisType), endOfDayNode))
        assertEquals(
            86_399_999,
            rawRecord(schemaOf(timeMillisType), format(schemaOf(timeMillisType), endOfDayNode)).get("value"),
        )
    }

    @Test
    fun invalidKnownLogicalSchemasAreRejected() {
        val invalidTypes = listOf(
            """{"type":"bytes","logicalType":"decimal","precision":0,"scale":0}""",
            """{"type":"bytes","logicalType":"decimal","precision":2,"scale":5}""",
            """{"type":"bytes","logicalType":"decimal","precision":2,"scale":-1}""",
            """{"type":"bytes","logicalType":"decimal","scale":2}""",
            """{"type":"fixed","name":"BadDecimal","size":2,"logicalType":"decimal","precision":10,"scale":2}""",
            """{"type":"long","logicalType":"date"}""",
            """{"type":"int","logicalType":"timestamp-millis"}""",
            """{"type":"fixed","name":"BadDuration","size":8,"logicalType":"duration"}""",
            """{"type":"bytes","logicalType":"uuid"}""",
        )
        for (typeJson in invalidTypes) {
            assertFailsWith<IllegalArgumentException>(typeJson) {
                AvroLogicalType.fromSchema(fieldSchema(typeJson))
            }
            assertFailsWith<AvroException>(typeJson) {
                AvroMapper(schemaOf(typeJson)).formatBytes(valueNode(StringNode.of("2024-01-01")))
            }
        }
    }

    @Test
    fun invalidKnownLogicalSchemaIsRejectedOnRead() {
        val writerSchema = recordSchema(field("value", "\"bytes\""))
        val readerSchema = recordSchema(field("value", """{"type":"bytes","logicalType":"decimal","precision":2,"scale":5}"""))
        val bytes = AvroMapper(writerSchema).formatBytes(valueNode(BinaryNode.of(byteArrayOf(1))))

        val error = assertFailsWith<AvroParseException> {
            AvroMapper(writerSchema, readerSchema).parse(bytes, Node::class.java)
        }
        assertTrue(error.message!!.contains("logical type"))
    }

    @Test
    fun decimalPrecisionAndScaleAreEnforcedOnWrite() {
        val mapper = AvroMapper(schemaOf(decimalType(5, 2)))

        assertFailsWith<AvroException> {
            mapper.formatBytes(valueNode(NumberNode.of(BigDecimal("1234.56"))))
        }
        assertFailsWith<AvroException> {
            mapper.formatBytes(valueNode(NumberNode.of(BigDecimal("12.345"))))
        }

        // 可无损调整 scale；字符串输入按十进制解析
        val restored = mapper.parse(
            mapper.formatBytes(valueNode(NumberNode.of(BigDecimal("12.3")))),
            Node::class.java,
        )
        assertDecimal(restored.asObject()["value"]!!, "12.30", 2)

        val fromString = mapper.parse(
            mapper.formatBytes(valueNode(StringNode.of("7.50"))),
            Node::class.java,
        )
        assertDecimal(fromString.asObject()["value"]!!, "7.50", 2)
    }

    @Test
    fun invalidTemporalUuidAndDurationValuesAreRejected() {
        assertFailsWith<AvroException> {
            AvroMapper(schemaOf(dateType)).formatBytes(valueNode(StringNode.of("2024-13-01")))
        }
        assertFailsWith<AvroException> {
            AvroMapper(schemaOf(dateType)).formatBytes(valueNode(NumberNode.of(1.5)))
        }
        assertFailsWith<AvroException> {
            AvroMapper(schemaOf(timeMillisType)).formatBytes(valueNode(StringNode.of("10:15:30.1234")))
        }
        assertFailsWith<AvroException> {
            AvroMapper(schemaOf(timeMicrosType)).formatBytes(valueNode(NumberNode.of(86_400_000_000L)))
        }
        assertFailsWith<AvroException> {
            AvroMapper(schemaOf(timestampMillisType)).formatBytes(
                valueNode(StringNode.of("2024-01-02T03:04:05.123456Z")),
            )
        }
        assertFailsWith<AvroException> {
            AvroMapper(schemaOf(uuidType)).formatBytes(valueNode(StringNode.of("not-a-uuid")))
        }
        assertFailsWith<AvroException> {
            AvroMapper(schemaOf(durationType)).formatBytes(
                valueNode(ObjectNode.of("months" to NumberNode.of(-1))),
            )
        }
        assertFailsWith<AvroException> {
            AvroMapper(schemaOf(durationType)).formatBytes(
                valueNode(ObjectNode.of("months" to NumberNode.of(4_294_967_296L))),
            )
        }
        assertFailsWith<AvroException> {
            AvroMapper(schemaOf(durationType)).formatBytes(
                valueNode(ObjectNode.of("years" to NumberNode.of(1))),
            )
        }
    }

    private class LogicalBean {
        var date: String? = null
        var timestamp: String? = null
        var uuid: String? = null
        var amount: BigDecimal? = null
    }

    private fun downedNumber(node: Node): Long = node.asObject()["value"]!!.asNumber().toLong()

    private fun assertDecimal(node: Node, expected: String, scale: Int) {
        val value = node.asNumber().toBigDecimal()
        assertEquals(0, value.compareTo(BigDecimal(expected)), "decimal value of $node")
        assertEquals(scale, value.scale(), "decimal scale of $node")
    }

    private fun fixedFor(schema: Schema, unscaled: BigInteger): GenericFixed {
        val fieldSchema = schema.fieldSchema()
        val size = fieldSchema.fixedSize
        val raw = unscaled.toByteArray()
        val bytes = ByteArray(size)
        if (unscaled.signum() < 0) {
            for (index in 0 until size - raw.size) {
                bytes[index] = 0xFF.toByte()
            }
        }
        System.arraycopy(raw, 0, bytes, size - raw.size, raw.size)
        return GenericData.Fixed(fieldSchema, bytes)
    }

    private fun bytesOf(value: Any?): ByteArray {
        val buffer = (value as ByteBuffer).duplicate()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }

    private fun instantMicros(instant: Instant): Long = Math.addExact(
        Math.multiplyExact(instant.epochSecond, 1_000_000L),
        instant.nano / 1_000L,
    )

    private fun parserOf(fields: String): Schema = Schema.Parser().parse("""{"type":"record","name":"Logical","fields":[$fields]}""")

    private fun recordSchema(vararg fields: String): Schema = parserOf(fields.joinToString(","))

    private fun schemaOf(typeJson: String): Schema = recordSchema(field("value", typeJson))

    private fun field(name: String, typeJson: String, defaultValue: String? = null): String = if (defaultValue == null) {
        """{"name":"$name","type":$typeJson}"""
    } else {
        """{"name":"$name","type":$typeJson,"default":$defaultValue}"""
    }

    private fun fieldSchema(typeJson: String): Schema = schemaOf(typeJson).getField("value").schema()

    private fun Schema.fieldSchema(): Schema = getField("value").schema()

    private fun valueNode(value: Node): ObjectNode = ObjectNode.of("value" to value)

    private fun format(schema: Schema, node: Node): ByteArray = AvroMapper(schema).formatBytes(node)

    private fun parse(schema: Schema, bytes: ByteArray): Node = AvroMapper(schema).parse(bytes, Node::class.java)

    private fun roundTrip(schema: Schema, node: Node): Node = parse(schema, format(schema, node))

    private fun rawRecord(schema: Schema, bytes: ByteArray): GenericRecord = GenericDatumReader<Any?>(schema).read(null, DecoderFactory.get().binaryDecoder(bytes, null)) as GenericRecord

    private fun rawRecordBytes(schema: Schema, record: GenericRecord): ByteArray {
        val output = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().binaryEncoder(output, null)
        GenericDatumWriter<Any?>(schema).write(record, encoder)
        encoder.flush()
        return output.toByteArray()
    }

    private val dateType: String = """{"type":"int","logicalType":"date"}"""
    private val timeMillisType: String = """{"type":"int","logicalType":"time-millis"}"""
    private val timeMicrosType: String = """{"type":"long","logicalType":"time-micros"}"""
    private val timestampMillisType: String = """{"type":"long","logicalType":"timestamp-millis"}"""
    private val timestampMicrosType: String = """{"type":"long","logicalType":"timestamp-micros"}"""
    private val localTimestampMillisType: String = """{"type":"long","logicalType":"local-timestamp-millis"}"""
    private val localTimestampMicrosType: String = """{"type":"long","logicalType":"local-timestamp-micros"}"""
    private val uuidType: String = """{"type":"string","logicalType":"uuid"}"""
    private val durationType: String = """{"type":"fixed","name":"Elapsed","size":12,"logicalType":"duration"}"""
    private fun decimalType(precision: Int, scale: Int): String = """{"type":"bytes","logicalType":"decimal","precision":$precision,"scale":$scale}"""
    private fun fixedDecimalType(precision: Int, scale: Int, size: Int): String = """{"type":"fixed","name":"Amount","size":$size,"logicalType":"decimal","precision":$precision,"scale":$scale}"""
}
