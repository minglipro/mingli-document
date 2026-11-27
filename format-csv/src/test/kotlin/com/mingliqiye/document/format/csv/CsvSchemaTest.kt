package com.mingliqiye.document.format.csv

import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CsvSchema] 与 [CsvSchemaBuilder] 测试。
 */
class CsvSchemaTest {

    @Test
    fun fieldsKeepDeclarationOrderAsColumnOrder() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .int("age")
            .boolean("active", required = false)
            .build()

        assertEquals("person", schema.name)
        assertEquals(listOf("name", "age", "active"), schema.columnNames)
        assertEquals(listOf("name", "age", "active"), schema.fields.map { it.name })
        assertEquals(
            listOf(SchemaType.STRING, SchemaType.INT, SchemaType.BOOLEAN),
            schema.fields.map { it.type },
        )
        assertEquals(SchemaType.INT, schema.field("age")?.type)
        assertTrue(schema.header)
        assertFalse(schema.failOnUnknownColumns)
    }

    @Test
    fun fieldLookupReturnsDeclaredFieldOrNull() {
        val schema = CsvSchema.builder("user")
            .field("name", SchemaType.STRING)
            .field("age", SchemaType.INT, required = false, nullable = true, description = "年龄")
            .build()

        val age = schema.field("age")
        assertEquals("age", age?.name)
        assertEquals(SchemaType.INT, age?.type)
        assertEquals(false, age?.required)
        assertEquals(true, age?.nullable)
        assertEquals("年龄", age?.description)
        assertNull(schema.field("missing"))
    }

    @Test
    fun csvSchemaImplementsDocumentSchema() {
        val schema: DocumentSchema = CsvSchema.builder("event")
            .field("id", SchemaType.LONG)
            .field("tags", SchemaType.STRING)
            .build()

        assertEquals("event", schema.name)
        assertEquals(2, schema.fields.size)
        assertEquals(SchemaType.LONG, schema.field("id")?.type)
        assertEquals(listOf("id", "tags"), schema.fields.map { it.name })
    }

    @Test
    fun builderMapsPrimitiveTypesToSchemaType() {
        val schema = CsvSchema.builder("types")
            .string("stringValue")
            .int("intValue")
            .long("longValue")
            .double("doubleValue")
            .decimal("decimalValue")
            .boolean("booleanValue")
            .bytes("bytesValue")
            .build()

        assertEquals(
            listOf(
                SchemaType.STRING,
                SchemaType.INT,
                SchemaType.LONG,
                SchemaType.DOUBLE,
                SchemaType.DECIMAL,
                SchemaType.BOOLEAN,
                SchemaType.BYTES,
            ),
            schema.fields.map { it.type },
        )
        assertTrue(schema.fields.all { it.required })
        assertTrue(schema.fields.none { it.nullable })
    }

    @Test
    fun headerAndUnknownColumnPolicyAreConfigurable() {
        val schema = CsvSchema.builder("person")
            .int("age")
            .header(false)
            .failOnUnknownColumns(true)
            .build()

        assertFalse(schema.header)
        assertTrue(schema.failOnUnknownColumns)
    }

    @Test
    fun companionFactoryCreatesSchema() {
        val schema = CsvSchema.of(
            "user",
            SchemaField.of("name", SchemaType.STRING),
            SchemaField.of("age", SchemaType.INT, required = false, nullable = true),
        )

        assertEquals("user", schema.name)
        assertEquals(listOf("name", "age"), schema.columnNames)
        assertEquals(SchemaType.INT, schema.field("age")?.type)
        assertEquals(true, schema.field("age")?.nullable)
        assertTrue(schema.header)
    }

    @Test
    fun duplicateColumnsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            CsvSchema.builder("person")
                .string("name")
                .int("name")
                .build()
        }
    }

    @Test
    fun blankNameAndEmptySchemaAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            CsvSchema.builder(" ").string("name").build()
        }
        assertFailsWith<IllegalArgumentException> {
            CsvSchema.builder("person").build()
        }
        assertFailsWith<IllegalArgumentException> {
            CsvSchema.builder("person").string(" ").build()
        }
    }
}
