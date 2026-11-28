package com.mingliqiye.document.registry

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.registry.exception.SchemaSerializationException
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaField
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [DocumentSchemaCodec] 编码/反解与 JSON 文本往返测试。
 */
class DocumentSchemaCodecTest {

    @Test
    fun encodesCanonicalJsonSchemaNode() {
        val schema = DocumentSchemaBuilder("User")
            .field("id", SchemaType.LONG, description = "用户 id")
            .field("name", SchemaType.STRING)
            .field("tags", SchemaType.ARRAY, required = false, nullable = true)
            .build()

        val node = DocumentSchemaCodec.encode(schema)

        assertEquals("User", node["title"]?.asString())
        assertEquals("object", node["type"]?.asString())
        val properties = assertNotNull(node["properties"]).asObject()
        assertEquals(listOf("id", "name", "tags"), properties.keys.toList())
        val id = assertNotNull(properties["id"]).asObject()
        assertEquals("integer", id["type"]?.asString())
        assertEquals("int64", id["format"]?.asString())
        assertEquals("用户 id", id["description"]?.asString())
        assertEquals(listOf("id", "name"), assertNotNull(node["required"]).asArray().map { it.asString() })
        assertEquals(
            listOf("array", "null"),
            assertNotNull(assertNotNull(properties["tags"]).asObject()["type"]).asArray().map { it.asString() },
        )
    }

    @Test
    fun encodesScalarAndCompositeTypes() {
        val schema = DocumentSchemaBuilder("Doc")
            .field("intValue", SchemaType.INT)
            .field("doubleValue", SchemaType.DOUBLE)
            .field("decimalValue", SchemaType.DECIMAL)
            .field("booleanValue", SchemaType.BOOLEAN)
            .field("bytesValue", SchemaType.BYTES)
            .field("objectValue", SchemaType.OBJECT)
            .field("mapValue", SchemaType.MAP)
            .field("nullValue", SchemaType.NULL)
            .build()

        val properties = assertNotNull(DocumentSchemaCodec.encode(schema)["properties"]).asObject()

        assertEquals("int32", assertNotNull(properties["intValue"]).asObject()["format"]?.asString())
        assertEquals("double", assertNotNull(properties["doubleValue"]).asObject()["format"]?.asString())
        assertNull(assertNotNull(properties["decimalValue"]).asObject()["format"])
        assertEquals("base64", assertNotNull(properties["bytesValue"]).asObject()["contentEncoding"]?.asString())
        assertEquals("object", assertNotNull(properties["objectValue"]).asObject()["type"]?.asString())
        assertNull(assertNotNull(properties["objectValue"]).asObject()["additionalProperties"])
        assertEquals("object", assertNotNull(properties["mapValue"]).asObject()["type"]?.asString())
        assertNotNull(assertNotNull(properties["mapValue"]).asObject()["additionalProperties"])
        assertEquals("null", assertNotNull(properties["nullValue"]).asObject()["type"]?.asString())
    }

    @Test
    fun roundTripsThroughJsonText() {
        val schema = DocumentSchemaBuilder("Order")
            .field("id", SchemaType.LONG, description = "订单 id")
            .field("amount", SchemaType.DECIMAL, nullable = true)
            .field("lines", SchemaType.ARRAY, required = false)
            .field("labels", SchemaType.MAP, required = false)
            .field("raw", SchemaType.BYTES)
            .build()
        val mapper = JsonMapper()

        val json = mapper.format(DocumentSchemaCodec.encode(schema))
        val restored = DocumentSchemaCodec.decode(mapper.parse(json, Node::class.java))

        assertSameDocumentSchema(schema, restored)
        assertTrue(json.contains("Order"), json)
        assertTrue(json.contains("int64"), json)
    }

    @Test
    fun roundTripsNativeNodeWithoutJsonText() {
        val schema = DocumentSchemaBuilder("User")
            .field("id", SchemaType.INT)
            .field("age", SchemaType.INT, required = false, nullable = true)
            .field("score", SchemaType.DOUBLE, required = false)
            .build()

        val restored = schema.toSchemaNode().toDocumentSchema()

        assertSameDocumentSchema(schema, restored)
    }

    @Test
    fun decodeFallsBackToProvidedNameWhenTitleIsMissing() {
        val node = ObjectNode.of(
            "type" to StringNode.of("object"),
            "properties" to ObjectNode.of(
                "name" to ObjectNode.of("type" to StringNode.of("string")),
            ),
            "required" to ArrayNode.of(StringNode.of("name")),
        )

        val schema = node.toDocumentSchema("fallback")

        assertEquals("fallback", schema.name)
        assertEquals(listOf("name"), schema.fields.map { it.name })
        assertTrue(schema.fields[0].required)
    }

    @Test
    fun decodeWithoutTitleAndNameIsRejected() {
        assertFailsWith<SchemaSerializationException> {
            ObjectNode.EMPTY.toDocumentSchema()
        }
    }

    @Test
    fun decodeRejectsMalformedNodes() {
        assertFailsWith<SchemaSerializationException> {
            StringNode.of("not-an-object").toDocumentSchema("name")
        }
        assertFailsWith<SchemaSerializationException> {
            ObjectNode.of("type" to StringNode.of("array")).toDocumentSchema("name")
        }
        assertFailsWith<SchemaSerializationException> {
            ObjectNode.of(
                "type" to StringNode.of("object"),
                "properties" to ObjectNode.of("field" to ObjectNode.EMPTY),
            ).toDocumentSchema("name")
        }
        assertFailsWith<SchemaSerializationException> {
            ObjectNode.of(
                "type" to StringNode.of("object"),
                "properties" to ObjectNode.of(
                    "field" to ObjectNode.of("type" to StringNode.of("timestamp")),
                ),
            ).toDocumentSchema("name")
        }
        assertFailsWith<SchemaSerializationException> {
            ObjectNode.of(
                "type" to StringNode.of("object"),
                "properties" to ObjectNode.of("field" to StringNode.of("string")),
            ).toDocumentSchema("name")
        }
        assertFailsWith<SchemaSerializationException> {
            ObjectNode.of(
                "type" to StringNode.of("object"),
                "properties" to ObjectNode.of("field" to ObjectNode.of("type" to StringNode.of("string"))),
                "required" to StringNode.of("field"),
            ).toDocumentSchema("name")
        }
        assertFailsWith<SchemaSerializationException> {
            ObjectNode.of(
                "type" to StringNode.of("object"),
                "properties" to ObjectNode.of(
                    "field" to ObjectNode.of(
                        "type" to ArrayNode.of(StringNode.of("string"), StringNode.of("integer")),
                    ),
                ),
            ).toDocumentSchema("name")
        }
        assertFailsWith<SchemaSerializationException> {
            ObjectNode.of(
                "type" to StringNode.of("object"),
                "properties" to ObjectNode.of(
                    "field" to ObjectNode.of(
                        "type" to StringNode.of("integer"),
                        "format" to StringNode.of("uint64"),
                    ),
                ),
            ).toDocumentSchema("name")
        }
    }

    @Test
    fun encodeRejectsInvalidSchemas() {
        assertFailsWith<SchemaSerializationException> {
            DocumentSchemaCodec.encode(DocumentSchema.of(" "))
        }
        assertFailsWith<SchemaSerializationException> {
            DocumentSchemaCodec.encode(DocumentSchema.of("Doc", SchemaField.of("", SchemaType.STRING)))
        }
        assertFailsWith<SchemaSerializationException> {
            DocumentSchemaCodec.encode(
                DocumentSchema.of(
                    "Doc",
                    SchemaField.of("duplicated", SchemaType.STRING),
                    SchemaField.of("duplicated", SchemaType.INT),
                ),
            )
        }
    }
}
