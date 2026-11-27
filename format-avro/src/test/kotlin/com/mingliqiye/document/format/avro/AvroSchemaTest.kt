package com.mingliqiye.document.format.avro

import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Avro Schema 层测试。
 */
class AvroSchemaTest {

    @Test
    fun builderBuildsFieldsInOrder() {
        val schema = AvroSchemaBuilder("User")
            .field("id", SchemaType.LONG)
            .field("name", SchemaType.STRING)
            .field("active", SchemaType.BOOLEAN, required = false, nullable = true)
            .build()

        assertEquals("User", schema.name)
        assertNull(schema.namespace)
        assertEquals("User", schema.fullName)
        assertEquals(listOf("id", "name", "active"), schema.fields.map { it.name })
        assertEquals(SchemaType.LONG, schema.field("id")?.type)
        assertTrue(schema.field("active")?.nullable == true)
        assertTrue(schema.field("active")?.required == false)
        assertNull(schema.field("missing"))
    }

    @Test
    fun schemaIsDocumentSchemaContract() {
        val schema: DocumentSchema = AvroSchemaBuilder("Event").field("id", SchemaType.LONG).build()

        assertEquals("Event", schema.name)
        assertEquals(1, schema.fields.size)
        assertEquals(SchemaType.LONG, schema.field("id")?.type)
        assertEquals(AvroType.LONG, (schema as AvroSchema).avroType("id"))
    }

    @Test
    fun namespaceAndRecordNameProduceFullName() {
        val schema = AvroSchemaBuilder("User")
            .namespace("com.mingliqiye.document")
            .field("id", SchemaType.LONG)
            .build()

        assertEquals("User", schema.name)
        assertEquals("com.mingliqiye.document", schema.namespace)
        assertEquals("com.mingliqiye.document.User", schema.fullName)
    }

    @Test
    fun namespaceAcceptsUnderscoreSegments() {
        val schema = AvroSchemaBuilder("user_record").namespace("com.example_org.v1").build()

        assertEquals("user_record", schema.name)
        assertEquals("com.example_org.v1.user_record", schema.fullName)
    }

    @Test
    fun schemaTypeMapsToAvroType() {
        assertEquals(AvroType.STRING, AvroType.fromSchemaType(SchemaType.STRING))
        assertEquals(AvroType.INT, AvroType.fromSchemaType(SchemaType.INT))
        assertEquals(AvroType.LONG, AvroType.fromSchemaType(SchemaType.LONG))
        assertEquals(AvroType.DOUBLE, AvroType.fromSchemaType(SchemaType.DOUBLE))
        assertEquals(AvroType.BOOLEAN, AvroType.fromSchemaType(SchemaType.BOOLEAN))
        assertEquals(AvroType.BYTES, AvroType.fromSchemaType(SchemaType.BYTES))
        assertEquals(AvroType.BYTES, AvroType.fromSchemaType(SchemaType.DECIMAL))
        assertEquals(AvroType.ARRAY, AvroType.fromSchemaType(SchemaType.ARRAY))
        assertEquals(AvroType.OBJECT, AvroType.fromSchemaType(SchemaType.OBJECT))
        assertEquals(AvroType.MAP, AvroType.fromSchemaType(SchemaType.MAP))
        assertEquals(AvroType.NULL, AvroType.fromSchemaType(SchemaType.NULL))
    }

    @Test
    fun avroTypeNameMapsToSchemaType() {
        assertEquals(AvroType.STRING, AvroType.fromTypeName("string"))
        assertEquals(AvroType.INT, AvroType.fromTypeName("int"))
        assertEquals(AvroType.LONG, AvroType.fromTypeName("long"))
        assertEquals(AvroType.DOUBLE, AvroType.fromTypeName("double"))
        assertEquals(AvroType.BOOLEAN, AvroType.fromTypeName("boolean"))
        assertEquals(AvroType.BYTES, AvroType.fromTypeName("bytes"))
        assertEquals(AvroType.ARRAY, AvroType.fromTypeName("array"))
        assertEquals(AvroType.OBJECT, AvroType.fromTypeName("object"))
        assertEquals(AvroType.MAP, AvroType.fromTypeName("map"))
        assertEquals(AvroType.NULL, AvroType.fromTypeName("null"))
        assertEquals(AvroType.STRING, AvroType.fromTypeName(" string "))
        assertNull(AvroType.fromTypeName("float"))
        assertNull(AvroType.fromTypeName(""))
    }

    @Test
    fun avroTypeNamesMatchAvroBasicTypes() {
        assertEquals(
            setOf("string", "int", "long", "double", "boolean", "bytes", "array", "object", "map", "null"),
            AvroType.entries.map { it.typeName }.toSet(),
        )
        assertEquals(
            SchemaType.entries.toSet() - setOf(SchemaType.DECIMAL),
            AvroType.entries.map { it.schemaType }.toSet(),
        )
    }

    @Test
    fun schemaExposesAvroTypeForFields() {
        val schema = AvroSchemaBuilder("User")
            .field("id", SchemaType.LONG)
            .field("tags", SchemaType.ARRAY)
            .build()

        assertEquals(AvroType.LONG, schema.avroType("id"))
        assertEquals(AvroType.ARRAY, schema.avroType("tags"))
        assertEquals("long", schema.avroTypeName("id"))
        assertEquals("array", schema.avroTypeName("tags"))
        assertNull(schema.avroType("missing"))
        assertNull(schema.avroTypeName("missing"))
        assertEquals(AvroType.LONG, schema.avroType(schema.field("id")!!))
    }

    @Test
    fun builderAcceptsAvroTypes() {
        val schema = AvroSchemaBuilder("User")
            .field("name", AvroType.STRING)
            .field("score", AvroType.DOUBLE, required = false, nullable = true)
            .build()

        assertEquals(SchemaType.STRING, schema.field("name")?.type)
        assertEquals(SchemaType.DOUBLE, schema.field("score")?.type)
        assertEquals(AvroType.DOUBLE, schema.avroType("score"))
        assertTrue(schema.field("score")?.nullable == true)
    }

    @Test
    fun factoryCreatesSchema() {
        val schema = AvroSchema.of(
            "Event",
            "com.example",
            SchemaField.of("id", SchemaType.LONG),
            SchemaField.of("tags", SchemaType.ARRAY, required = false),
        )

        assertEquals("Event", schema.name)
        assertEquals("com.example.Event", schema.fullName)
        assertEquals(listOf("id", "tags"), schema.fields.map { it.name })

        val noNamespace = AvroSchema.of("Event", SchemaField.of("id", SchemaType.LONG))
        assertNull(noNamespace.namespace)
        assertEquals("Event", noNamespace.fullName)
    }

    @Test
    fun toJsonRendersRecordFieldsAndNamespace() {
        val schema = AvroSchemaBuilder("User")
            .namespace("com.example")
            .field("id", SchemaType.LONG)
            .field("name", SchemaType.STRING)
            .field("nick", SchemaType.STRING, required = false, nullable = true, description = "昵称")
            .build()

        assertEquals(
            "{\"type\":\"record\",\"name\":\"User\",\"namespace\":\"com.example\",\"fields\":[" +
                "{\"name\":\"id\",\"type\":\"long\"}," +
                "{\"name\":\"name\",\"type\":\"string\"}," +
                "{\"name\":\"nick\",\"type\":[\"null\",\"string\"],\"default\":null,\"doc\":\"昵称\"}]}",
            schema.toJson(),
        )
    }

    @Test
    fun toJsonEscapesDescription() {
        val schema = AvroSchemaBuilder("Doc")
            .field("text", SchemaType.STRING, description = "a\"b\\c\nd")
            .build()

        assertTrue(schema.toJson().contains("\"doc\":\"a\\\"b\\\\c\\nd\""))
    }

    @Test
    fun emptyFieldsAreAllowed() {
        val schema = AvroSchemaBuilder("Empty").build()

        assertEquals(0, schema.fields.size)
        assertEquals("{\"type\":\"record\",\"name\":\"Empty\",\"fields\":[]}", schema.toJson())
    }

    @Test
    fun nullNamespaceIsAllowed() {
        val schema = AvroSchemaBuilder("User").namespace(null).build()

        assertNull(schema.namespace)
        assertEquals("User", schema.fullName)
    }

    @Test
    fun invalidRecordNameIsRejected() {
        assertFailsWith<IllegalArgumentException> { AvroSchemaBuilder("").build() }
        assertFailsWith<IllegalArgumentException> { AvroSchemaBuilder("1User").build() }
        assertFailsWith<IllegalArgumentException> { AvroSchemaBuilder("user-record").build() }
        assertFailsWith<IllegalArgumentException> { AvroSchemaBuilder("user.record").build() }
    }

    @Test
    fun invalidNamespaceIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            AvroSchemaBuilder("User").namespace("com..example").build()
        }
        assertFailsWith<IllegalArgumentException> {
            AvroSchemaBuilder("User").namespace("com.example.").build()
        }
        assertFailsWith<IllegalArgumentException> {
            AvroSchemaBuilder("User").namespace("").build()
        }
        assertFailsWith<IllegalArgumentException> {
            AvroSchemaBuilder("User").namespace("com.example-org").build()
        }
    }

    @Test
    fun invalidFieldNameIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            AvroSchemaBuilder("User").field("", SchemaType.STRING).build()
        }
        assertFailsWith<IllegalArgumentException> {
            AvroSchemaBuilder("User").field("user-name", SchemaType.STRING).build()
        }
        assertFailsWith<IllegalArgumentException> {
            AvroSchemaBuilder("User").field("1name", SchemaType.STRING).build()
        }
    }

    @Test
    fun duplicateFieldNamesAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            AvroSchemaBuilder("User")
                .field("id", SchemaType.LONG)
                .field("id", SchemaType.STRING)
                .build()
        }
    }
}
