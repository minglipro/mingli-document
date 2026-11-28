package com.mingliqiye.document.registry

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.registry.exception.SchemaSerializationException
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [RemoteDocumentSchemaRegistry] 远程适配器测试（使用内存 Fake 客户端）。
 */
class RemoteDocumentSchemaRegistryTest {

    private val subject = SchemaSubject.of("user")
    private val client = FakeSchemaRegistryClient()
    private val registry = RemoteDocumentSchemaRegistry(client)

    @Test
    fun registerEncodesDocumentSchemaAndUsesJsonFormat() {
        val schema = userSchema()
        val metadata = mapOf("owner" to "team-a")

        val registered = registry.register(subject, schema, metadata)

        assertEquals(DocumentSchemaCodec.encode(schema), client.lastRegisterNode)
        assertEquals(SchemaFormat.JSON, client.lastRegisterFormat)
        assertEquals(metadata, client.lastRegisterMetadata)
        assertEquals(registered, registry.latest(subject))
        assertSameDocumentSchema(schema, registered.documentSchema())
    }

    @Test
    fun queryMethodsDelegateToClient() {
        val first = registry.register(subject, userSchema())
        val second = registry.register(subject, userSchemaWithOptionalEmail())

        assertEquals(listOf(SchemaVersion.of(1), SchemaVersion.of(2)), registry.versions(subject))
        assertEquals(first, registry.byId(first.id))
        assertEquals(second, registry.byId(second.id))
        assertNull(registry.byId(SchemaId.of(99)))
        assertNull(registry.latest(SchemaSubject.of("unknown")))

        assertEquals(listOf(SchemaVersion.of(1), SchemaVersion.of(2)), registry.deleteSubject(subject))
        assertNull(registry.latest(subject))
        assertTrue(registry.versions(subject).isEmpty())
    }

    @Test
    fun adapterDoesNotApplyLocalCompatibilityPolicy() {
        registry.register(subject, userSchema())

        val breaking = registry.register(subject, DocumentSchemaBuilder("User").field("email", SchemaType.STRING).build())

        assertEquals(SchemaVersion.of(2), breaking.version)
        assertEquals(listOf(SchemaVersion.of(1), SchemaVersion.of(2)), registry.versions(subject))
    }

    @Test
    fun nonJsonRemotePayloadCannotBeDecoded() {
        val avroRecord = RegisteredSchema(
            subject = subject,
            id = SchemaId.of(7),
            version = SchemaVersion.of(1),
            schema = StringNode.of("""{"type":"record","name":"User"}"""),
            type = SchemaFormat.AVRO,
        )

        assertFailsWith<SchemaSerializationException> { avroRecord.documentSchema() }
    }

    private fun userSchema(): DocumentSchema = DocumentSchemaBuilder("User")
        .field("id", SchemaType.LONG)
        .field("name", SchemaType.STRING)
        .build()

    private fun userSchemaWithOptionalEmail(): DocumentSchema = DocumentSchemaBuilder("User")
        .field("id", SchemaType.LONG)
        .field("name", SchemaType.STRING)
        .field("email", SchemaType.STRING, required = false)
        .build()

    /**
     * 模拟远程 registry 客户端：只做存储，不做兼容性检查。
     */
    private class FakeSchemaRegistryClient : SchemaRegistryClient {

        var lastRegisterSubject: SchemaSubject? = null
        var lastRegisterNode: Node? = null
        var lastRegisterFormat: SchemaFormat? = null
        var lastRegisterMetadata: Map<String, String>? = null

        private val versionsBySubject = LinkedHashMap<SchemaSubject, MutableList<RegisteredSchema>>()
        private val schemasById = LinkedHashMap<SchemaId, RegisteredSchema>()
        private var nextId = 1L

        override fun register(
            subject: SchemaSubject,
            schema: Node,
            format: SchemaFormat,
            metadata: Map<String, String>,
        ): RegisteredSchema {
            lastRegisterSubject = subject
            lastRegisterNode = schema
            lastRegisterFormat = format
            lastRegisterMetadata = metadata
            val versions = versionsBySubject.getOrPut(subject) { ArrayList() }
            val registered = RegisteredSchema(
                subject = subject,
                id = SchemaId.of(nextId),
                version = SchemaVersion.of(versions.size + 1),
                schema = schema,
                type = format,
                metadata = metadata,
            )
            nextId += 1
            versions.add(registered)
            schemasById.putIfAbsent(registered.id, registered)
            return registered
        }

        override fun latest(subject: SchemaSubject): RegisteredSchema? = versionsBySubject[subject]?.lastOrNull()

        override fun byId(id: SchemaId): RegisteredSchema? = schemasById[id]

        override fun versions(subject: SchemaSubject): List<SchemaVersion> = versionsBySubject[subject]?.map { it.version } ?: emptyList()

        override fun deleteSubject(subject: SchemaSubject): List<SchemaVersion> {
            val versions = versionsBySubject.remove(subject) ?: return emptyList()
            return versions.map { it.version }
        }
    }
}
