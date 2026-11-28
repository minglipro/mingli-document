package com.mingliqiye.document.registry

import com.mingliqiye.document.schema.DocumentSchema
import kotlin.test.assertEquals

/**
 * 测试辅助：按字段逐项比较 [DocumentSchema]（`SchemaField` 没有 equals 实现）。
 */
internal fun assertSameDocumentSchema(expected: DocumentSchema, actual: DocumentSchema) {
    assertEquals(expected.name, actual.name)
    assertEquals(expected.fields.size, actual.fields.size)
    expected.fields.zip(actual.fields).forEach { (expectedField, actualField) ->
        assertEquals(expectedField.name, actualField.name)
        assertEquals(expectedField.type, actualField.type)
        assertEquals(expectedField.required, actualField.required)
        assertEquals(expectedField.nullable, actualField.nullable)
        assertEquals(expectedField.description, actualField.description)
    }
}
