package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * [com.mingliqiye.document.annotation.DocumentDateTimeFormat] 的字段转换器。
 */
internal class DocumentDateTimeFormatConverter(
    private val pattern: String,
    timezone: String,
) : DocumentBaseConverter<Any> {

    private val zone: ZoneId? = timezone.takeIf { it.isNotBlank() }?.let(ZoneId::of)

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(pattern)

    private val dateFormat: SimpleDateFormat = SimpleDateFormat(pattern).apply {
        zone?.let { timeZone = TimeZone.getTimeZone(it) }
    }

    override fun getType(): DocumentTypeReference<Any> = DocumentTypeReference.of()

    override fun serialize(value: Any, context: SerializationContext): Node {
        val text = when (value) {
            is Date -> synchronized(dateFormat) { dateFormat.format(value) }
            is Calendar -> synchronized(dateFormat) { dateFormat.format(value.time) }
            is TemporalAccessor -> formatterWithZone(value).format(value)
            else -> throw unsupported(value.javaClass, context.property, context.path)
        }
        return StringNode.of(text)
    }

    override fun deserialize(node: Node, context: DeserializationContext): Any {
        val text = node.asString()
        val type = context.property?.getRawType()
            ?: throw DocumentSerializationException("DateTimeFormat requires a property at ${context.path}")
        return try {
            when {
                type == java.sql.Date::class.java -> java.sql.Date(parseDate(text).time)
                type == java.sql.Timestamp::class.java -> java.sql.Timestamp(parseDate(text).time)
                Date::class.java.isAssignableFrom(type) -> parseDate(text)
                Calendar::class.java.isAssignableFrom(type) -> Calendar.getInstance().apply { time = parseDate(text) }
                type == LocalDate::class.java -> LocalDate.parse(text, formatter)
                type == LocalDateTime::class.java -> LocalDateTime.parse(text, formatter)
                type == LocalTime::class.java -> LocalTime.parse(text, formatter)
                type == OffsetDateTime::class.java -> OffsetDateTime.parse(text, formatter)
                type == ZonedDateTime::class.java -> ZonedDateTime.parse(text, formatterWithZone(text))
                type == Instant::class.java -> Instant.from(formatterWithZone(text).parse(text))
                else -> throw unsupported(type, context.property, context.path)
            }
        } catch (e: DocumentSerializationException) {
            throw e
        } catch (e: Exception) {
            throw DocumentSerializationException(
                "Cannot parse '${context.property?.documentName ?: context.path}' as ${type.name} with format '$pattern'",
                e,
            )
        }
    }

    private fun formatterWithZone(value: Any): DateTimeFormatter = when {
        zone != null -> formatter.withZone(zone)
        value is Instant || value is ZonedDateTime -> formatter.withZone(ZoneId.systemDefault())
        else -> formatter
    }

    private fun formatterWithZone(text: String): DateTimeFormatter {
        if (zone != null) {
            return formatter.withZone(zone)
        }
        return formatter.withZone(ZoneId.systemDefault())
    }

    private fun parseDate(text: String): Date = synchronized(dateFormat) { dateFormat.parse(text) }

    private fun unsupported(
        type: Class<*>,
        property: DocumentProperty?,
        path: String,
    ): DocumentSerializationException = DocumentSerializationException(
        "Unsupported DateTimeFormat type ${type.name} at " +
            "${property?.declaringClass?.name ?: path}.${property?.name ?: ""}",
    )
}
