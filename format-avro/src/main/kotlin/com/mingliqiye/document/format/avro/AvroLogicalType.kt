package com.mingliqiye.document.format.avro

import org.apache.avro.LogicalTypes
import org.apache.avro.Schema

/** Avro `duration` 逻辑类型固定为 size 12 的 fixed。 */
internal const val AVRO_DURATION_SIZE: Int = 12

/** Avro Schema 中声明逻辑类型的属性名。 */
private const val LOGICAL_TYPE_PROP: String = "logicalType"

/** Avro decimal 逻辑类型的 precision 属性名。 */
private const val PRECISION_PROP: String = "precision"

/** Avro decimal 逻辑类型的 scale 属性名。 */
private const val SCALE_PROP: String = "scale"

/**
 * Avro logical type。
 *
 * 枚举本模块显式支持的逻辑类型；[fromSchema] 识别 Schema 节点上的逻辑类型，
 * 未知逻辑类型返回 `null`，编解码回退到底层 Avro 类型的原有行为。
 *
 * 已知逻辑类型但 Schema 结构非法时 [fromSchema] 抛 [IllegalArgumentException]：
 * - 底层类型不匹配（如 `date` 标注在 `long` 上）；
 * - `decimal` 缺少 precision、precision/scale 非法或 fixed 容量不足；
 * - `duration` 不是 size 12 的 `fixed`。
 *
 * 注意 Apache Avro 1.11 的 [LogicalTypes] 未注册 `duration`，因此 `duration`
 * 通过 Schema 的原始 `logicalType` 属性识别。
 *
 * @property typeName Schema 中 `logicalType` 属性值
 */
public enum class AvroLogicalType(
    public val typeName: String,
) {
    /** 自 1970-01-01 起的天数，底层 `int`。 */
    DATE("date"),

    /** 自午夜起的毫秒数，底层 `int`。 */
    TIME_MILLIS("time-millis"),

    /** 自午夜起的微秒数，底层 `long`。 */
    TIME_MICROS("time-micros"),

    /** UTC 时间戳，自 epoch 起的毫秒数，底层 `long`。 */
    TIMESTAMP_MILLIS("timestamp-millis"),

    /** UTC 时间戳，自 epoch 起的微秒数，底层 `long`。 */
    TIMESTAMP_MICROS("timestamp-micros"),

    /** 无时区本地时间戳，自 epoch 起的毫秒数，底层 `long`。 */
    LOCAL_TIMESTAMP_MILLIS("local-timestamp-millis"),

    /** 无时区本地时间戳，自 epoch 起的微秒数，底层 `long`。 */
    LOCAL_TIMESTAMP_MICROS("local-timestamp-micros"),

    /** 任意精度定点小数，底层 `bytes` 或 `fixed`。 */
    DECIMAL("decimal"),

    /** RFC-4122 UUID，底层 `string`。 */
    UUID("uuid"),

    /** Avro 规范中 12 字节 little-endian 的 months/days/millis，底层 size 12 的 `fixed`。 */
    DURATION("duration"),
    ;

    public companion object {

        /**
         * 按 `logicalType` 属性值查找逻辑类型。
         *
         * @param typeName 属性值，允许首尾空白
         * @return 逻辑类型；未知名称返回 `null`
         */
        @JvmStatic
        public fun fromTypeName(typeName: String): AvroLogicalType? {
            val normalized = typeName.trim()
            return entries.firstOrNull { it.typeName == normalized }
        }

        /**
         * 读取 Schema 节点的逻辑类型。
         *
         * 未知逻辑类型返回 `null`（调用方回退底层类型）；已知逻辑类型的 Schema
         * 结构非法时抛 [IllegalArgumentException]。
         *
         * @param schema Avro Schema 节点
         * @return 逻辑类型；无逻辑类型或未知名称返回 `null`
         * @throws IllegalArgumentException Schema 结构与声明的已知逻辑类型不匹配时抛出
         */
        @JvmStatic
        public fun fromSchema(schema: Schema): AvroLogicalType? {
            val typeName = declaredTypeName(schema) ?: return null
            val logicalType = fromTypeName(typeName) ?: return null
            validate(schema, logicalType)
            return logicalType
        }

        /**
         * 读取 decimal 逻辑类型的 precision/scale。
         *
         * 供 [fromSchema] 返回 [DECIMAL] 后做编解码使用；Schema 不是合法 decimal
         * 时抛 [IllegalArgumentException]。
         *
         * @param schema decimal 底层 Schema 节点
         * @return precision 与 scale
         */
        internal fun decimalScale(schema: Schema): Pair<Int, Int> {
            val logicalType = schema.logicalType
            if (logicalType is LogicalTypes.Decimal) {
                return logicalType.precision to logicalType.scale
            }
            val precision = integerProp(schema, PRECISION_PROP)
                ?: throw IllegalArgumentException("Avro decimal logical type requires an integer '$PRECISION_PROP' property")
            val scale = integerProp(schema, SCALE_PROP) ?: 0
            return precision to scale
        }

        /** Schema 声明的 `logicalType`：优先取 Apache Avro 已解析的结果，否则读原始属性。 */
        private fun declaredTypeName(schema: Schema): String? = schema.logicalType?.name ?: (schema.getObjectProp(LOGICAL_TYPE_PROP) as? String)

        private fun validate(schema: Schema, logicalType: AvroLogicalType) {
            when (logicalType) {
                DATE, TIME_MILLIS -> requireBaseType(schema, logicalType, Schema.Type.INT)
                TIME_MICROS,
                TIMESTAMP_MILLIS,
                TIMESTAMP_MICROS,
                LOCAL_TIMESTAMP_MILLIS,
                LOCAL_TIMESTAMP_MICROS,
                -> requireBaseType(schema, logicalType, Schema.Type.LONG)

                UUID -> requireBaseType(schema, logicalType, Schema.Type.STRING)
                DECIMAL -> validateDecimal(schema)
                DURATION -> validateDuration(schema, logicalType)
            }
        }

        private fun requireBaseType(schema: Schema, logicalType: AvroLogicalType, expected: Schema.Type) {
            if (schema.type != expected) {
                throw IllegalArgumentException(
                    "Avro ${logicalType.typeName} logical type requires ${expected.avroTypeName()} " +
                        "but schema was ${schema.type.avroTypeName()}",
                )
            }
        }

        private fun validateDecimal(schema: Schema) {
            if (schema.type != Schema.Type.BYTES && schema.type != Schema.Type.FIXED) {
                throw IllegalArgumentException(
                    "Avro decimal logical type requires bytes or fixed but schema was ${schema.type.avroTypeName()}",
                )
            }
            // Apache Avro 已解析出 Decimal 时说明其已完成校验
            if (schema.logicalType is LogicalTypes.Decimal) {
                return
            }
            val precision = integerProp(schema, PRECISION_PROP)
                ?: throw IllegalArgumentException("Avro decimal logical type requires an integer '$PRECISION_PROP' property")
            val scale = integerProp(schema, SCALE_PROP) ?: 0
            if (precision <= 0) {
                throw IllegalArgumentException("Invalid Avro decimal precision $precision (must be positive)")
            }
            if (scale < 0) {
                throw IllegalArgumentException("Invalid Avro decimal scale $scale (must not be negative)")
            }
            if (scale > precision) {
                throw IllegalArgumentException("Invalid Avro decimal scale $scale (greater than precision $precision)")
            }
            if (schema.type == Schema.Type.FIXED) {
                val maxPrecision = maxDecimalPrecision(schema.fixedSize)
                if (precision.toLong() > maxPrecision) {
                    throw IllegalArgumentException(
                        "Avro fixed(${schema.fixedSize}) cannot store decimal($precision) (max precision $maxPrecision)",
                    )
                }
            }
        }

        private fun validateDuration(schema: Schema, logicalType: AvroLogicalType) {
            requireBaseType(schema, logicalType, Schema.Type.FIXED)
            if (schema.fixedSize != AVRO_DURATION_SIZE) {
                throw IllegalArgumentException(
                    "Avro duration logical type requires fixed size $AVRO_DURATION_SIZE " +
                        "but schema was fixed(${schema.fixedSize})",
                )
            }
        }

        /** 与 Apache Avro 一致：fixed 能表达的十进制位数上限。 */
        private fun maxDecimalPrecision(fixedSize: Int): Long = Math.round(Math.floor(Math.log10(2.0) * (8 * fixedSize - 1)))

        private fun integerProp(schema: Schema, name: String): Int? = schema.getObjectProp(name) as? Int

        private fun Schema.Type.avroTypeName(): String = name.lowercase()
    }
}
