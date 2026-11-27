package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * [com.mingliqiye.document.annotation.DocumentNumberFormat] 的字段转换器。
 */
internal class DocumentNumberFormatConverter(
    pattern: String,
    locale: String,
) : DocumentBaseConverter<Any> {

    private val decimalFormat: DecimalFormat = DecimalFormat(
        pattern,
        DecimalFormatSymbols(resolveLocale(locale)),
    ).apply {
        isParseBigDecimal = true
    }

    override fun getType(): DocumentTypeReference<Any> = DocumentTypeReference.of()

    override fun serialize(value: Any, context: SerializationContext): Node {
        val number = value as? Number
            ?: throw DocumentSerializationException(
                "DocumentNumberFormat requires a Number at ${context.path}, got ${value.javaClass.name}",
            )
        val text = synchronized(decimalFormat) { decimalFormat.format(number) }
        return StringNode.of(text)
    }

    override fun deserialize(node: Node, context: DeserializationContext): Any {
        val type = context.property?.getRawType()
            ?: throw DocumentSerializationException("DocumentNumberFormat requires a property at ${context.path}")
        return try {
            val parsed = synchronized(decimalFormat) { decimalFormat.parse(node.asString()) }
                ?: throw DocumentSerializationException("Cannot parse number at ${context.path}")
            val value = parsed as? BigDecimal ?: BigDecimal(parsed.toString())
            when (type) {
                BigDecimal::class.java -> value
                BigInteger::class.java -> value.toBigIntegerExact()
                Byte::class.java, java.lang.Byte::class.java -> value.byteValueExact()
                Short::class.java, java.lang.Short::class.java -> value.shortValueExact()
                Int::class.java, java.lang.Integer::class.java -> value.intValueExact()
                Long::class.java, java.lang.Long::class.java -> value.longValueExact()
                Float::class.java, java.lang.Float::class.java -> value.toFloat()
                Double::class.java, java.lang.Double::class.java -> value.toDouble()
                Number::class.java -> value
                else -> throw DocumentSerializationException(
                    "Unsupported DocumentNumberFormat type ${type.name} at ${context.path}",
                )
            }
        } catch (e: DocumentSerializationException) {
            throw e
        } catch (e: Exception) {
            throw DocumentSerializationException(
                "Cannot parse '${context.property?.documentName ?: context.path}' as ${type.name}",
                e,
            )
        }
    }

    private fun resolveLocale(locale: String): Locale = locale.takeIf { it.isNotBlank() }?.let { Locale.forLanguageTag(it.replace('_', '-')) }
        ?: Locale.getDefault()
}
