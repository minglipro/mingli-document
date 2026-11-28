package com.mingliqiye.document.openapi

import com.mingliqiye.document.schema.SchemaType

/**
 * OpenAPI 字段类型描述。
 *
 * 通用 [SchemaType] 只描述字段的类型类别，不携带元素类型、枚举取值、嵌套 Schema
 * 等 OpenAPI 需要的细节。需要补充这些细节时，通过
 * [OpenApiConfigBuilder.fieldType] 为 `schemaName.fieldName` 指定本类型。
 *
 * 默认映射（未覆盖时）为：
 *
 * - [SchemaType.ARRAY] -> `array<string>`
 * - [SchemaType.MAP] -> `object` + `additionalProperties: string`
 * - [SchemaType.OBJECT] -> 自由对象 `{"type": "object"}`
 *
 * 使用工厂方法构造：
 *
 * ```kotlin
 * OpenApiFieldType.reference("Address")
 * OpenApiFieldType.array(OpenApiFieldType.reference("Line"))
 * OpenApiFieldType.enumOf("ACTIVE", "DISABLED")
 * ```
 */
public sealed interface OpenApiFieldType {

    /** [OpenApiFieldType] 工厂方法。 */
    public companion object {

        /**
         * 基本类型；映射规则与字段声明类型相同。
         *
         * @param type Schema 类型
         */
        @JvmStatic
        public fun primitive(type: SchemaType): OpenApiFieldType = OpenApiPrimitiveType(type)

        /**
         * 引用 `components.schemas` 中的另一个 Schema。
         *
         * @param schemaName 目标 Schema 名称，生成时必须已注册
         */
        @JvmStatic
        public fun reference(schemaName: String): OpenApiFieldType = OpenApiReferenceType(schemaName)

        /**
         * 数组类型。
         *
         * @param items 元素类型
         */
        @JvmStatic
        public fun array(items: OpenApiFieldType): OpenApiFieldType = OpenApiArrayType(items)

        /**
         * Map 类型。
         *
         * @param values 值类型
         */
        @JvmStatic
        public fun map(values: OpenApiFieldType): OpenApiFieldType = OpenApiMapType(values)

        /**
         * 字符串枚举类型。
         *
         * @param values 枚举取值，不能为空且不能重复
         */
        @JvmStatic
        public fun enumOf(vararg values: String): OpenApiFieldType = OpenApiEnumType(values.toList())
    }
}
