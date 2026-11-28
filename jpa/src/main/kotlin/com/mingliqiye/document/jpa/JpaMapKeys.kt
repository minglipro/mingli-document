package com.mingliqiye.document.jpa

import com.mingliqiye.document.jpa.internal.JpaValueConverter
import jakarta.persistence.EnumType
import jakarta.persistence.TemporalType

/**
 * Map 键与文档对象字段名之间的转换规则。
 *
 * [JpaDocumentMapper] 在映射 Map 关联 / Map `@ElementCollection` 时使用这套规则：
 * 文档对象字段名为字符串化的 Map 键；读入时再反向转换为 `Map<K, V>` 的键类型。
 *
 * 该对象把规则公开给需要复用 jpa 元数据的扩展模块（例如 `jpa-hibernate` 在
 * Hibernate 感知映射中读取/写出 Map 关联），保证两条路径的键格式一致。
 *
 * 支持的键类型：String/CharSequence/Char、boolean、数字（byte/short/int/long/
 * float/double/BigDecimal/BigInteger）、枚举（`@MapKeyEnumerated`，默认 ORDINAL）、
 * `java.util.Date`/`Calendar`（`@MapKeyTemporal`）、`java.time` 日期时间、UUID。
 *
 * 格式：
 * - 字符串/字符：原样；
 * - 布尔：`true`/`false`；
 * - 数字：精确十进制文本（`NumberNode.raw`）；
 * - 枚举：`ORDINAL` 为序号文本，`STRING` 为常量名；
 * - 日期时间：ISO-8601 文本（`Date`/`Calendar` 按 `@MapKeyTemporal` 或自然精度）。
 */
public object JpaMapKeys {

    private val converter: JpaValueConverter = JpaValueConverter()

    /**
     * Map 键 -> 文档对象字段名。
     *
     * @param key Map 键，不能为 `null`
     * @param keyType Map 键类型
     * @param enumType `@MapKeyEnumerated`；`null` 表示 JPA 默认 `ORDINAL`
     * @param temporalType `@MapKeyTemporal`；`null` 表示按目标日期类型的自然精度
     * @param path 当前文档路径，用于异常信息
     * @throws com.mingliqiye.document.jpa.exception.JpaMappingException 键为 null、
     * 类型不受支持或无法格式化时抛出
     */
    @JvmStatic
    public fun toFieldName(
        key: Any?,
        keyType: Class<*>,
        enumType: EnumType? = null,
        temporalType: TemporalType? = null,
        path: String = "$",
    ): String = converter.writeMapKey(key, keyType, enumType, temporalType, path)

    /**
     * 文档对象字段名 -> Map 键，是 [toFieldName] 的逆操作。
     *
     * @param text 文档对象字段名
     * @param keyType Map 键类型
     * @param enumType `@MapKeyEnumerated`；`null` 表示 JPA 默认 `ORDINAL`
     * @param temporalType `@MapKeyTemporal`；`null` 表示按目标日期类型的自然精度
     * @param path 当前文档路径，用于异常信息
     * @throws com.mingliqiye.document.jpa.exception.JpaMappingException 键文本无法
     * 转换为目标键类型时抛出
     */
    @JvmStatic
    public fun fromFieldName(
        text: String,
        keyType: Class<*>,
        enumType: EnumType? = null,
        temporalType: TemporalType? = null,
        path: String = "$",
    ): Any = converter.readMapKey(text, keyType, enumType, temporalType, path)
}
