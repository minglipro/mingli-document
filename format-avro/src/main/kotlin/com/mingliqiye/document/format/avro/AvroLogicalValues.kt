package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.avro.exception.AvroException
import com.mingliqiye.document.format.avro.exception.AvroParseException
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericFixed
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID

/**
 * 已知 Avro logical type 与 [Node] 之间的双向转换。
 *
 * 读取（datum → Node）：
 * - `date` → `yyyy-MM-dd`
 * - `time-millis` / `time-micros` → `HH:mm:ss.SSS` / `HH:mm:ss.SSSSSS`
 * - `timestamp-millis` / `timestamp-micros` → `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` /
 *   `yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'`（UTC）
 * - `local-timestamp-millis` / `local-timestamp-micros` → 无时区 ISO 本地时间
 * - `decimal` → 保留 scale 的 [NumberNode]
 * - `uuid` → [StringNode]
 * - `duration` → `{"months": n, "days": n, "millis": n}` [ObjectNode]
 *
 * 写出时按 Schema 的逻辑类型反向编码：日期/时间/时间戳接受 ISO-8601 字符串，
 * 也接受对应的 epoch 数值（天/毫秒/微秒）；decimal 接受 [NumberNode] 或十进制
 * 字符串；uuid 接受可解析的 UUID 字符串并按 canonical 小写写出；duration 接受
 * 上述对象，也接受 12 字节 [com.mingliqiye.document.core.BinaryNode] 或 Base64
 * 字符串（由 [AvroWireWriter] 回退到 fixed 处理）。
 *
 * 精度超出逻辑类型承载能力（如毫秒逻辑类型收到微秒值、decimal 需要舍入或超出
 * precision）一律报错而不是静默截断。
 */
internal object AvroLogicalValues {

    private const val NANOS_PER_MILLI: Long = 1_000_000L
    private const val NANOS_PER_MICRO: Long = 1_000L
    private const val MICROS_PER_SECOND: Long = 1_000_000L
    private const val MILLIS_PER_DAY: Long = 86_400_000L
    private const val MICROS_PER_DAY: Long = 86_400_000_000L
    private const val UINT32_MAX: Long = 0xFFFF_FFFFL

    private const val DURATION_MONTHS: String = "months"
    private const val DURATION_DAYS: String = "days"
    private const val DURATION_MILLIS: String = "millis"

    private val DURATION_FIELDS: Set<String> = setOf(DURATION_MONTHS, DURATION_DAYS, DURATION_MILLIS)

    private val TIME_MILLIS_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS", Locale.ROOT)
    private val TIME_MICROS_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS", Locale.ROOT)
    private val TIMESTAMP_MILLIS_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)
    private val TIMESTAMP_MICROS_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)
    private val LOCAL_TIMESTAMP_MILLIS_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT)
    private val LOCAL_TIMESTAMP_MICROS_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ROOT)

    /**
     * 把逻辑类型 datum 转换为 [Node]。
     *
     * @param datum Avro 通用 datum
     * @param schema datum 对应的 Schema 节点
     * @param logicalType Schema 声明的逻辑类型
     * @param path 出错时用于定位的路径
     * @return 文档树节点
     * @throws AvroParseException datum 与逻辑类型不匹配或值非法时抛出
     */
    fun toNode(datum: Any?, schema: Schema, logicalType: AvroLogicalType, path: String): Node = try {
        when (logicalType) {
            AvroLogicalType.DATE -> StringNode.of(LocalDate.ofEpochDay(epochDatum(datum, path, logicalType)).toString())

            AvroLogicalType.TIME_MILLIS -> {
                val millis = epochDatum(datum, path, logicalType)
                checkRange(millis, 0L, MILLIS_PER_DAY, path, logicalType)
                StringNode.of(LocalTime.ofNanoOfDay(millis * NANOS_PER_MILLI).format(TIME_MILLIS_FORMATTER))
            }

            AvroLogicalType.TIME_MICROS -> {
                val micros = epochDatum(datum, path, logicalType)
                checkRange(micros, 0L, MICROS_PER_DAY, path, logicalType)
                StringNode.of(LocalTime.ofNanoOfDay(micros * NANOS_PER_MICRO).format(TIME_MICROS_FORMATTER))
            }

            AvroLogicalType.TIMESTAMP_MILLIS -> StringNode.of(
                TIMESTAMP_MILLIS_FORMATTER.format(Instant.ofEpochMilli(epochDatum(datum, path, logicalType))),
            )

            AvroLogicalType.TIMESTAMP_MICROS -> StringNode.of(
                TIMESTAMP_MICROS_FORMATTER.format(instantOfEpochMicros(epochDatum(datum, path, logicalType))),
            )

            AvroLogicalType.LOCAL_TIMESTAMP_MILLIS -> StringNode.of(
                LOCAL_TIMESTAMP_MILLIS_FORMATTER.format(
                    localDateTimeOfEpochMillis(epochDatum(datum, path, logicalType)),
                ),
            )

            AvroLogicalType.LOCAL_TIMESTAMP_MICROS -> StringNode.of(
                LOCAL_TIMESTAMP_MICROS_FORMATTER.format(
                    localDateTimeOfEpochMicros(epochDatum(datum, path, logicalType)),
                ),
            )

            AvroLogicalType.DECIMAL -> NumberNode.of(decimalValue(datum, schema, logicalType, path))

            AvroLogicalType.UUID -> StringNode.of(charSequenceDatum(datum, path, logicalType))

            AvroLogicalType.DURATION -> durationNode(datum, path)
        }
    } catch (e: AvroParseException) {
        throw e
    } catch (e: RuntimeException) {
        throw AvroParseException("Invalid Avro ${logicalType.typeName} value at $path: ${e.message}", e)
    }

    /**
     * 把 [Node] 按逻辑类型编码为 Avro 通用 datum。
     *
     * @param node 文档树节点
     * @param schema 目标 Schema 节点
     * @param logicalType 目标逻辑类型
     * @param path 出错时用于定位的路径
     * @return Avro 通用 datum
     * @throws AvroException 节点值无法表示为该逻辑类型时抛出
     */
    fun toDatum(node: Node, schema: Schema, logicalType: AvroLogicalType, path: String): Any = try {
        when (logicalType) {
            AvroLogicalType.DATE -> dateDatum(node, path)
            AvroLogicalType.TIME_MILLIS -> timeMillisDatum(node, path)
            AvroLogicalType.TIME_MICROS -> timeMicrosDatum(node, path)
            AvroLogicalType.TIMESTAMP_MILLIS -> timestampMillisDatum(node, path)
            AvroLogicalType.TIMESTAMP_MICROS -> timestampMicrosDatum(node, path)
            AvroLogicalType.LOCAL_TIMESTAMP_MILLIS -> localTimestampMillisDatum(node, path)
            AvroLogicalType.LOCAL_TIMESTAMP_MICROS -> localTimestampMicrosDatum(node, path)
            AvroLogicalType.DECIMAL -> decimalDatum(node, schema, path)
            AvroLogicalType.UUID -> uuidDatum(node, path)
            AvroLogicalType.DURATION -> durationDatum(node, schema, path)
        }
    } catch (e: AvroException) {
        throw e
    } catch (e: RuntimeException) {
        throw AvroException("Failed to encode Avro ${logicalType.typeName} at $path: ${e.message}", e)
    }

    private fun dateDatum(node: Node, path: String): Int {
        val epochDay = if (node.isString) {
            parseDate(node.asString(), path).toEpochDay()
        } else {
            exactLong(numberNode(node, path, AvroLogicalType.DATE), path, AvroLogicalType.DATE)
        }
        if (epochDay < Int.MIN_VALUE.toLong() || epochDay > Int.MAX_VALUE.toLong()) {
            throw AvroException("Avro date epoch day $epochDay at $path does not fit Avro int")
        }
        return epochDay.toInt()
    }

    private fun timeMillisDatum(node: Node, path: String): Int {
        if (node.isString) {
            val time = parseTime(node.asString(), path, AvroLogicalType.TIME_MILLIS)
            if (time.nano % NANOS_PER_MILLI != 0L) {
                throw precisionError(node.asString(), path, AvroLogicalType.TIME_MILLIS)
            }
            return (time.toNanoOfDay() / NANOS_PER_MILLI).toInt()
        }
        val millis = exactLong(numberNode(node, path, AvroLogicalType.TIME_MILLIS), path, AvroLogicalType.TIME_MILLIS)
        checkRangeForWrite(millis, 0L, MILLIS_PER_DAY, path, AvroLogicalType.TIME_MILLIS)
        return millis.toInt()
    }

    private fun timeMicrosDatum(node: Node, path: String): Long {
        if (node.isString) {
            val time = parseTime(node.asString(), path, AvroLogicalType.TIME_MICROS)
            if (time.nano % NANOS_PER_MICRO != 0L) {
                throw precisionError(node.asString(), path, AvroLogicalType.TIME_MICROS)
            }
            return time.toNanoOfDay() / NANOS_PER_MICRO
        }
        val micros = exactLong(numberNode(node, path, AvroLogicalType.TIME_MICROS), path, AvroLogicalType.TIME_MICROS)
        checkRangeForWrite(micros, 0L, MICROS_PER_DAY, path, AvroLogicalType.TIME_MICROS)
        return micros
    }

    private fun timestampMillisDatum(node: Node, path: String): Long {
        if (node.isString) {
            val instant = parseInstant(node.asString(), path, AvroLogicalType.TIMESTAMP_MILLIS)
            if (instant.nano % NANOS_PER_MILLI != 0L) {
                throw precisionError(node.asString(), path, AvroLogicalType.TIMESTAMP_MILLIS)
            }
            return instant.toEpochMilli()
        }
        return exactLong(
            numberNode(node, path, AvroLogicalType.TIMESTAMP_MILLIS),
            path,
            AvroLogicalType.TIMESTAMP_MILLIS,
        )
    }

    private fun timestampMicrosDatum(node: Node, path: String): Long {
        if (node.isString) {
            val instant = parseInstant(node.asString(), path, AvroLogicalType.TIMESTAMP_MICROS)
            if (instant.nano % NANOS_PER_MICRO != 0L) {
                throw precisionError(node.asString(), path, AvroLogicalType.TIMESTAMP_MICROS)
            }
            return epochMicros(instant)
        }
        return exactLong(
            numberNode(node, path, AvroLogicalType.TIMESTAMP_MICROS),
            path,
            AvroLogicalType.TIMESTAMP_MICROS,
        )
    }

    private fun localTimestampMillisDatum(node: Node, path: String): Long {
        if (node.isString) {
            val dateTime = parseLocalDateTime(node.asString(), path, AvroLogicalType.LOCAL_TIMESTAMP_MILLIS)
            if (dateTime.nano % NANOS_PER_MILLI != 0L) {
                throw precisionError(node.asString(), path, AvroLogicalType.LOCAL_TIMESTAMP_MILLIS)
            }
            return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        return exactLong(
            numberNode(node, path, AvroLogicalType.LOCAL_TIMESTAMP_MILLIS),
            path,
            AvroLogicalType.LOCAL_TIMESTAMP_MILLIS,
        )
    }

    private fun localTimestampMicrosDatum(node: Node, path: String): Long {
        if (node.isString) {
            val dateTime = parseLocalDateTime(node.asString(), path, AvroLogicalType.LOCAL_TIMESTAMP_MICROS)
            if (dateTime.nano % NANOS_PER_MICRO != 0L) {
                throw precisionError(node.asString(), path, AvroLogicalType.LOCAL_TIMESTAMP_MICROS)
            }
            return epochMicros(dateTime.toInstant(ZoneOffset.UTC))
        }
        return exactLong(
            numberNode(node, path, AvroLogicalType.LOCAL_TIMESTAMP_MICROS),
            path,
            AvroLogicalType.LOCAL_TIMESTAMP_MICROS,
        )
    }

    private fun decimalDatum(node: Node, schema: Schema, path: String): Any {
        val (precision, scale) = AvroLogicalType.decimalScale(schema)
        val value = when {
            node.isNumber -> node.asNumber().toBigDecimal()
            node.isString -> try {
                BigDecimal(node.asString())
            } catch (e: NumberFormatException) {
                throw AvroException("Invalid decimal string '${node.asString()}' at $path", e)
            }

            else -> throw AvroException(
                "Expected number or decimal string at $path for Avro decimal but was ${node.type}",
            )
        }
        val scaled = try {
            value.setScale(scale, RoundingMode.UNNECESSARY)
        } catch (e: ArithmeticException) {
            throw AvroException("Cannot encode decimal '$value' at $path as scale $scale without rounding", e)
        }
        if (scaled.precision() > precision) {
            throw AvroException(
                "Cannot encode decimal '$value' at $path with precision ${scaled.precision()} " +
                    "as max precision $precision",
            )
        }
        val unscaled = scaled.unscaledValue().toByteArray()
        return when (schema.type) {
            Schema.Type.BYTES -> ByteBuffer.wrap(unscaled)
            Schema.Type.FIXED -> fixedDecimal(schema, unscaled, scaled.signum(), path)
            else -> throw AvroException(
                "Avro decimal at $path must be backed by bytes or fixed but was ${schema.type}",
            )
        }
    }

    private fun uuidDatum(node: Node, path: String): String {
        val value = node.asStringOrNull()
            ?: throw AvroException("Expected UUID string at $path but was ${node.type}")
        return try {
            UUID.fromString(value).toString()
        } catch (e: IllegalArgumentException) {
            throw AvroException("Invalid UUID '$value' at $path", e)
        }
    }

    private fun durationDatum(node: Node, schema: Schema, path: String): GenericData.Fixed {
        val objectNode = node.asObjectOrNull()
            ?: throw AvroException(
                "Expected object with $DURATION_MONTHS/$DURATION_DAYS/$DURATION_MILLIS at $path " +
                    "for Avro duration but was ${node.type}",
            )
        for (key in objectNode.keys) {
            if (key !in DURATION_FIELDS) {
                throw AvroException(
                    "Unknown Avro duration field '$key' at $path (expected $DURATION_MONTHS/$DURATION_DAYS/$DURATION_MILLIS)",
                )
            }
        }
        val months = durationComponent(objectNode, DURATION_MONTHS, path)
        val days = durationComponent(objectNode, DURATION_DAYS, path)
        val millis = durationComponent(objectNode, DURATION_MILLIS, path)
        val bytes = ByteArray(AVRO_DURATION_SIZE)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(months.toInt())
            .putInt(days.toInt())
            .putInt(millis.toInt())
        return GenericData.Fixed(schema, bytes)
    }

    private fun durationComponent(objectNode: ObjectNode, name: String, path: String): Long {
        val child = objectNode[name] ?: return 0L
        val number = child.asNumberOrNull()
            ?: throw AvroException("Expected number at $path.$name for Avro duration but was ${child.type}")
        val value = try {
            number.toLong()
        } catch (e: ArithmeticException) {
            throw AvroException("Number '${number.raw}' at $path.$name does not fit Avro duration", e)
        }
        if (value < 0L || value > UINT32_MAX) {
            throw AvroException("Avro duration $name at $path must be in [0, $UINT32_MAX] but was $value")
        }
        return value
    }

    private fun fixedDecimal(schema: Schema, unscaled: ByteArray, signum: Int, path: String): GenericData.Fixed {
        val size = schema.fixedSize
        if (unscaled.size > size) {
            throw AvroException("Decimal at $path needs ${unscaled.size} bytes but fixed size is $size")
        }
        val bytes = ByteArray(size)
        val offset = size - unscaled.size
        if (signum < 0) {
            for (index in 0 until offset) {
                bytes[index] = 0xFF.toByte()
            }
        }
        System.arraycopy(unscaled, 0, bytes, offset, unscaled.size)
        return GenericData.Fixed(schema, bytes)
    }

    private fun decimalValue(datum: Any?, schema: Schema, logicalType: AvroLogicalType, path: String): BigDecimal {
        val (_, scale) = AvroLogicalType.decimalScale(schema)
        val bytes = bytesDatum(datum, path, logicalType)
        return if (bytes.isEmpty()) {
            BigDecimal.ZERO.setScale(scale)
        } else {
            BigDecimal(BigInteger(bytes), scale)
        }
    }

    private fun durationNode(datum: Any?, path: String): ObjectNode {
        val bytes = bytesDatum(datum, path, AvroLogicalType.DURATION)
        if (bytes.size != AVRO_DURATION_SIZE) {
            throw AvroParseException("Avro duration at $path expects $AVRO_DURATION_SIZE bytes but was ${bytes.size}")
        }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return ObjectNode.of(
            DURATION_MONTHS to NumberNode.of(buffer.int.toLong() and UINT32_MAX),
            DURATION_DAYS to NumberNode.of(buffer.int.toLong() and UINT32_MAX),
            DURATION_MILLIS to NumberNode.of(buffer.int.toLong() and UINT32_MAX),
        )
    }

    private fun epochDatum(datum: Any?, path: String, logicalType: AvroLogicalType): Long = when (datum) {
        is Int -> datum.toLong()
        is Long -> datum
        is Short -> datum.toLong()
        is Byte -> datum.toLong()
        null -> throw AvroParseException(
            "Expected integer datum at $path for Avro ${logicalType.typeName} but was null",
        )

        else -> throw AvroParseException(
            "Expected integer datum at $path for Avro ${logicalType.typeName} but was ${datum.javaClass.name}",
        )
    }

    private fun charSequenceDatum(datum: Any?, path: String, logicalType: AvroLogicalType): String = when (datum) {
        is CharSequence -> datum.toString()
        null -> throw AvroParseException(
            "Expected string datum at $path for Avro ${logicalType.typeName} but was null",
        )

        else -> throw AvroParseException(
            "Expected string datum at $path for Avro ${logicalType.typeName} but was ${datum.javaClass.name}",
        )
    }

    private fun bytesDatum(datum: Any?, path: String, logicalType: AvroLogicalType): ByteArray = when (datum) {
        is ByteBuffer -> {
            val copy = datum.duplicate()
            val bytes = ByteArray(copy.remaining())
            copy.get(bytes)
            bytes
        }

        is GenericFixed -> datum.bytes()
        null -> throw AvroParseException(
            "Expected bytes or fixed datum at $path for Avro ${logicalType.typeName} but was null",
        )

        else -> throw AvroParseException(
            "Expected bytes or fixed datum at $path for Avro ${logicalType.typeName} but was ${datum.javaClass.name}",
        )
    }

    private fun numberNode(node: Node, path: String, logicalType: AvroLogicalType): NumberNode = node.asNumberOrNull() ?: throw AvroException(
        "Expected number or ISO-8601 string at $path for Avro ${logicalType.typeName} but was ${node.type}",
    )

    private fun exactLong(number: NumberNode, path: String, logicalType: AvroLogicalType): Long = try {
        number.toLong()
    } catch (e: ArithmeticException) {
        throw AvroException(
            "Number '${number.raw}' at $path does not fit Avro ${logicalType.typeName} integer representation",
            e,
        )
    }

    private fun checkRange(value: Long, min: Long, maxExclusive: Long, path: String, logicalType: AvroLogicalType) {
        if (value < min || value >= maxExclusive) {
            throw AvroParseException(
                "Avro ${logicalType.typeName} value $value at $path must be in [$min, $maxExclusive)",
            )
        }
    }

    private fun checkRangeForWrite(
        value: Long,
        min: Long,
        maxExclusive: Long,
        path: String,
        logicalType: AvroLogicalType,
    ) {
        if (value < min || value >= maxExclusive) {
            throw AvroException(
                "Avro ${logicalType.typeName} value $value at $path must be in [$min, $maxExclusive)",
            )
        }
    }

    private fun precisionError(value: String, path: String, logicalType: AvroLogicalType): AvroException = AvroException(
        "Avro ${logicalType.typeName} value '$value' at $path has finer precision than the logical type allows",
    )

    private fun instantOfEpochMicros(micros: Long): Instant = Instant.ofEpochSecond(
        Math.floorDiv(micros, MICROS_PER_SECOND),
        Math.floorMod(micros, MICROS_PER_SECOND) * NANOS_PER_MICRO,
    )

    private fun localDateTimeOfEpochMillis(epochMillis: Long): LocalDateTime = LocalDateTime.ofEpochSecond(
        Math.floorDiv(epochMillis, 1_000L),
        (Math.floorMod(epochMillis, 1_000L) * NANOS_PER_MILLI).toInt(),
        ZoneOffset.UTC,
    )

    private fun localDateTimeOfEpochMicros(epochMicros: Long): LocalDateTime = LocalDateTime.ofEpochSecond(
        Math.floorDiv(epochMicros, MICROS_PER_SECOND),
        (Math.floorMod(epochMicros, MICROS_PER_SECOND) * NANOS_PER_MICRO).toInt(),
        ZoneOffset.UTC,
    )

    private fun epochMicros(instant: Instant): Long = try {
        Math.addExact(
            Math.multiplyExact(instant.epochSecond, MICROS_PER_SECOND),
            instant.nano / NANOS_PER_MICRO,
        )
    } catch (e: ArithmeticException) {
        throw AvroException("Instant '$instant' does not fit Avro timestamp-micros range", e)
    }

    private fun parseDate(value: String, path: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        throw AvroException("Invalid ISO-8601 date '$value' at $path", e)
    }

    private fun parseTime(value: String, path: String, logicalType: AvroLogicalType): LocalTime = try {
        LocalTime.parse(value)
    } catch (e: DateTimeParseException) {
        throw AvroException("Invalid ISO-8601 time '$value' at $path for Avro ${logicalType.typeName}", e)
    }

    private fun parseInstant(value: String, path: String, logicalType: AvroLogicalType): Instant = try {
        Instant.parse(value)
    } catch (e: DateTimeParseException) {
        throw AvroException(
            "Invalid ISO-8601 UTC timestamp '$value' at $path for Avro ${logicalType.typeName}",
            e,
        )
    }

    private fun parseLocalDateTime(
        value: String,
        path: String,
        logicalType: AvroLogicalType,
    ): LocalDateTime = try {
        LocalDateTime.parse(value)
    } catch (e: DateTimeParseException) {
        throw AvroException(
            "Invalid ISO-8601 local date-time '$value' at $path for Avro ${logicalType.typeName}",
            e,
        )
    }
}
