package com.mingliqiye.document.format.protobuf

import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ProtobufSchema] 测试。
 */
class ProtobufSchemaTest {

    @Test
    fun builderBuildsSchemaInDeclarationOrder() {
        val schema = ProtobufSchema.builder("User", "com.example")
            .field("id", 1, ProtobufType.INT64)
            .field("name", 2, ProtobufType.STRING, required = false)
            .field("tags", 3, ProtobufType.STRING, repeated = true, nullable = true, description = "标签")
            .build()

        assertEquals("User", schema.messageName)
        assertEquals("com.example", schema.packageName)
        assertEquals("com.example.User", schema.messageFullName)
        assertEquals("com.example.User", schema.name)
        assertEquals(3, schema.protobufFields.size)
        assertEquals(listOf("id", "name", "tags"), schema.protobufFields.map { it.name })
        assertEquals(listOf("id", "name", "tags"), schema.fields.map { it.name })
        assertEquals(ProtobufType.INT64, assertNotNull(schema.protobufField("id")).protobufType)
    }

    @Test
    fun fieldLookupByNameAndNumber() {
        val schema = ProtobufSchema.builder("User")
            .field("id", 1, ProtobufType.INT64)
            .field("name", 2, ProtobufType.STRING)
            .build()

        val name = assertNotNull(schema.protobufField("name"))
        assertEquals("name", name.name)
        assertEquals(2, name.fieldNumber)
        assertEquals(ProtobufType.STRING, name.protobufType)
        assertEquals("name", assertNotNull(schema.protobufField(2)).name)
        assertEquals(SchemaType.STRING, assertNotNull(schema.field("name")).type)
        assertNull(schema.field("missing"))
        assertNull(schema.protobufField("missing"))
        assertNull(schema.protobufField(99))
    }

    @Test
    fun repeatedFieldMapsToArray() {
        val schema = ProtobufSchema.builder("User")
            .field("tags", 3, ProtobufType.STRING, repeated = true)
            .field("id", 1, ProtobufType.INT32)
            .build()

        val tags = assertNotNull(schema.protobufField("tags"))
        assertTrue(tags.repeated)
        assertEquals(SchemaType.ARRAY, tags.schemaType)
        assertEquals(SchemaType.STRING, tags.elementType)
        assertEquals(SchemaType.ARRAY, assertNotNull(schema.field("tags")).type)
        assertEquals(SchemaType.INT, assertNotNull(schema.field("id")).type)
    }

    @Test
    fun protobufTypesMapToSchemaTypes() {
        val expected = mapOf(
            ProtobufType.DOUBLE to SchemaType.DOUBLE,
            ProtobufType.FLOAT to SchemaType.DOUBLE,
            ProtobufType.INT32 to SchemaType.INT,
            ProtobufType.INT64 to SchemaType.LONG,
            ProtobufType.UINT32 to SchemaType.LONG,
            ProtobufType.UINT64 to SchemaType.DECIMAL,
            ProtobufType.SINT32 to SchemaType.INT,
            ProtobufType.SINT64 to SchemaType.LONG,
            ProtobufType.FIXED32 to SchemaType.LONG,
            ProtobufType.FIXED64 to SchemaType.DECIMAL,
            ProtobufType.SFIXED32 to SchemaType.INT,
            ProtobufType.SFIXED64 to SchemaType.LONG,
            ProtobufType.BOOL to SchemaType.BOOLEAN,
            ProtobufType.STRING to SchemaType.STRING,
            ProtobufType.BYTES to SchemaType.BYTES,
            ProtobufType.ENUM to SchemaType.STRING,
            ProtobufType.MESSAGE to SchemaType.OBJECT,
            ProtobufType.GROUP to SchemaType.OBJECT,
            ProtobufType.MAP to SchemaType.MAP,
        )

        assertEquals(ProtobufType.values().toSet(), expected.keys)
        expected.forEach { (protobufType, schemaType) -> assertEquals(schemaType, protobufType.schemaType) }
        assertEquals("int32", ProtobufType.INT32.typeName)
        assertEquals("uint64", ProtobufType.UINT64.typeName)
        assertEquals(ProtobufType.INT64, ProtobufType.fromTypeName("int64"))
        assertEquals(ProtobufType.GROUP, ProtobufType.fromTypeName("group"))
        assertNull(ProtobufType.fromTypeName("uint128"))
        assertEquals(ProtobufType.INT32, ProtobufType.fromSchemaType(SchemaType.INT))
        assertEquals(ProtobufType.STRING, ProtobufType.fromSchemaType(SchemaType.DECIMAL))
        assertFailsWith<IllegalArgumentException> { ProtobufType.fromSchemaType(SchemaType.ARRAY) }
        assertFailsWith<IllegalArgumentException> { ProtobufType.fromSchemaType(SchemaType.NULL) }
    }

    @Test
    fun builderAcceptsSchemaTypes() {
        val schema = ProtobufSchema.builder("Doc")
            .field("id", 1, SchemaType.LONG)
            .field("price", 2, SchemaType.DECIMAL)
            .field("extra", 3, SchemaType.OBJECT)
            .field("tags", 4, SchemaType.STRING, repeated = true)
            .build()

        assertEquals(ProtobufType.INT64, assertNotNull(schema.protobufField("id")).protobufType)
        assertEquals(ProtobufType.STRING, assertNotNull(schema.protobufField("price")).protobufType)
        assertEquals(ProtobufType.MESSAGE, assertNotNull(schema.protobufField("extra")).protobufType)
        assertTrue(assertNotNull(schema.protobufField("tags")).repeated)
        assertFailsWith<IllegalArgumentException> { ProtobufSchema.builder("Doc").field("tags", 1, SchemaType.ARRAY) }
        assertFailsWith<IllegalArgumentException> { ProtobufSchema.builder("Doc").field("none", 1, SchemaType.NULL) }
    }

    @Test
    fun duplicateFieldNumberRejectedOnBuild() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.builder("User")
                .field("id", 1, ProtobufType.INT64)
                .field("legacyId", 1, ProtobufType.STRING)
                .build()
        }

        assertTrue(exception.message.orEmpty().contains("字段编号"))
    }

    @Test
    fun duplicateFieldNameRejectedOnBuild() {
        val exception = assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.builder("User")
                .field("id", 1, ProtobufType.INT64)
                .field("id", 2, ProtobufType.STRING)
                .build()
        }

        assertTrue(exception.message.orEmpty().contains("字段名"))
    }

    @Test
    fun messageFullNameUsesOptionalPackage() {
        val schema = ProtobufSchemaBuilder("Event").field("id", 1, ProtobufType.INT32).build()

        assertNull(schema.packageName)
        assertEquals("Event", schema.messageFullName)
        assertEquals("Event", schema.name)
        assertEquals("com.example.Outer.Inner", ProtobufSchema.builder("Outer.Inner", "com.example").build().messageFullName)
    }

    @Test
    fun invalidFieldsRejected() {
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaField("id", 0, ProtobufType.INT32) }
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaField("id", -1, ProtobufType.INT32) }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchemaField("id", ProtobufSchemaField.MAX_FIELD_NUMBER + 1, ProtobufType.INT32)
        }
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaField("id", 19_000, ProtobufType.INT32) }
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaField("id", 19_999, ProtobufType.INT32) }
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaField("", 1, ProtobufType.INT32) }
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaField("1id", 1, ProtobufType.INT32) }
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaField("user-id", 1, ProtobufType.INT32) }

        assertEquals(18_999, ProtobufSchemaField("id", 18_999, ProtobufType.INT32).fieldNumber)
        assertEquals(20_000, ProtobufSchemaField("id", 20_000, ProtobufType.INT32).fieldNumber)
        assertEquals(
            ProtobufSchemaField.MAX_FIELD_NUMBER,
            ProtobufSchemaField("id", ProtobufSchemaField.MAX_FIELD_NUMBER, ProtobufType.INT32).fieldNumber,
        )
    }

    @Test
    fun invalidMessageAndPackageRejected() {
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaBuilder("1User") }
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaBuilder("User Name") }
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaBuilder("Outer..Inner") }
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaBuilder("User", "com..example") }
        assertFailsWith<IllegalArgumentException> { ProtobufSchemaBuilder("User", "com.1example") }
        assertFailsWith<IllegalArgumentException> { ProtobufSchema("9User", emptyList()) }
    }

    @Test
    fun emptySchemaHasNoFields() {
        val schema = ProtobufSchemaBuilder("Empty").build()

        assertTrue(schema.protobufFields.isEmpty())
        assertTrue(schema.fields.isEmpty())
        assertEquals("Empty", schema.name)
        assertNull(schema.field("any"))
        assertNull(schema.protobufField(1))
    }

    @Test
    fun fieldProjectsToSchemaField() {
        val field = ProtobufSchemaField(
            "age",
            2,
            ProtobufType.INT32,
            required = false,
            nullable = true,
            description = "年龄",
        )
        val schemaField = field.toSchemaField()

        assertEquals("age", schemaField.name)
        assertEquals(SchemaType.INT, schemaField.type)
        assertEquals(false, schemaField.required)
        assertEquals(true, schemaField.nullable)
        assertEquals("年龄", schemaField.description)
    }

    @Test
    fun jsonDescriptionRoundTrips() {
        val schema = ProtobufSchema.builder("User", "com.example")
            .field("id", 1, ProtobufType.INT64)
            .field(
                "tags",
                3,
                ProtobufType.STRING,
                repeated = true,
                required = false,
                nullable = true,
                description = "标签",
            )
            .build()

        val description = schema.toJsonDescription()
        assertEquals("User", description["message"])
        assertEquals("com.example", description["package"])
        assertEquals("com.example.User", description["fullName"])
        @Suppress("UNCHECKED_CAST")
        val fieldDescriptions = assertNotNull(description["fields"] as? List<Map<String, Any?>>)
        assertEquals(2, fieldDescriptions.size)
        assertEquals("id", fieldDescriptions[0]["name"])
        assertEquals(1, (fieldDescriptions[0]["number"] as Number).toInt())
        assertEquals("int64", fieldDescriptions[0]["type"])
        assertEquals("LONG", fieldDescriptions[0]["schemaType"])
        assertEquals(false, fieldDescriptions[0]["repeated"] as Boolean)
        assertEquals("string", fieldDescriptions[1]["type"])
        assertEquals("ARRAY", fieldDescriptions[1]["schemaType"])
        assertEquals(true, fieldDescriptions[1]["repeated"] as Boolean)
        assertEquals(false, fieldDescriptions[1]["required"] as Boolean)
        assertEquals(true, fieldDescriptions[1]["nullable"] as Boolean)
        assertEquals("标签", fieldDescriptions[1]["description"])

        val restored = ProtobufSchema.fromJsonDescription(description)
        assertEquals(schema.messageName, restored.messageName)
        assertEquals(schema.packageName, restored.packageName)
        assertEquals(schema.messageFullName, restored.messageFullName)
        assertEquals(
            schema.protobufFields.map { it.toJsonDescription() },
            restored.protobufFields.map { it.toJsonDescription() },
        )
    }

    @Test
    fun invalidJsonDescriptionRejected() {
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.fromJsonDescription(mapOf("fields" to emptyList<Any?>()))
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.fromJsonDescription(
                mapOf(
                    "message" to "User",
                    "fields" to listOf(mapOf("name" to "id", "number" to 1, "type" to "uint128")),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.fromJsonDescription(
                mapOf("message" to "User", "fields" to listOf(mapOf("number" to 1, "type" to "int32"))),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ProtobufSchema.fromJsonDescription(
                mapOf("message" to "User", "fields" to listOf(mapOf("name" to "id", "type" to "int32"))),
            )
        }
    }
}
