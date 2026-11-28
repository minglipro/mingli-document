package com.mingliqiye.document.jpa.internal

/**
 * 主键种类。
 */
internal enum class JpaIdentifierKind {

    /** 单个基本类型 `@Id`。 */
    SIMPLE,

    /** `@EmbeddedId`（或 `@Id` + `@Embedded`）复合主键。 */
    EMBEDDED,

    /** `@IdClass` 复合主键（多个基本类型 `@Id` 属性）。 */
    ID_CLASS,
}

/**
 * 实体的主键元数据，用于实体作为 Map 键（`@MapKeyJoinColumn`）时的标识符文本互转。
 *
 * @property kind 主键种类
 * @property properties 主键属性（声明顺序）：
 * - [JpaIdentifierKind.SIMPLE]：唯一 `@Id` 属性；
 * - [JpaIdentifierKind.EMBEDDED]：唯一 `@EmbeddedId` 属性；
 * - [JpaIdentifierKind.ID_CLASS]：多个 `@Id` 基本属性
 * @property embeddedIdType `@EmbeddedId` 的 id 类型；非 EMBEDDED 时为 `null`
 */
internal class JpaIdentifierMetadata(
    val kind: JpaIdentifierKind,
    val properties: List<JpaProperty>,
    val embeddedIdType: Class<*>? = null,
)
