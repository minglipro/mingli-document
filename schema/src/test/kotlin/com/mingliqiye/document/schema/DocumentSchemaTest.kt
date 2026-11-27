package com.mingliqiye.document.schema

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Schema 契约测试。
 */
class DocumentSchemaTest {

    @Test
    fun builderBuildsFieldsInOrder() {
        val schema = DocumentSchemaBuilder("user")
            .field("name", SchemaType.STRING)
            .field("age", SchemaType.INT, required = false, nullable = true)
            .build()

        assertEquals("user", schema.name)
        assertEquals(listOf("name", "age"), schema.fields.map { it.name })
        assertEquals(SchemaType.INT, schema.field("age")?.type)
        assertTrue(schema.field("age")?.nullable == true)
        assertNull(schema.field("missing"))
    }

    @Test
    fun companionFactoryCreatesSchema() {
        val schema = DocumentSchema.of(
            "event",
            SchemaField.of("id", SchemaType.LONG),
            SchemaField.of("tags", SchemaType.ARRAY, required = false),
        )

        assertEquals("event", schema.name)
        assertEquals(2, schema.fields.size)
        assertEquals("id", schema.fields.first().name)
    }
}
