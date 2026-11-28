package com.mingliqiye.document.registry

import com.mingliqiye.document.registry.exception.SchemaCompatibilityException
import com.mingliqiye.document.registry.exception.SchemaNotFoundException
import com.mingliqiye.document.registry.exception.SchemaRegistryException
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
 * [InMemoryDocumentSchemaRegistry] 注册、版本、查询与删除语义测试。
 */
class InMemoryDocumentSchemaRegistryTest {

    private val subject = SchemaSubject.of("user")

    @Test
    fun registerAssignsGlobalIdAndFirstVersion() {
        val registry = InMemoryDocumentSchemaRegistry(SchemaCompatibilityPolicy.NONE)

        val registered = registry.register(subject, userSchema())

        assertEquals(SchemaId.of(1), registered.id)
        assertEquals(SchemaVersion.of(1), registered.version)
        assertEquals(subject, registered.subject)
        assertEquals(SchemaFormat.JSON, registered.type)
        assertEquals(emptyMap(), registered.metadata)
        assertTrue(registered.schema.isObject)
        assertEquals(registered, registry.latest(subject))
        assertEquals(SchemaVersion.of(1), registry.latest(subject)?.version)
    }

    @Test
    fun versionsIncrementAndIdsAreGlobal() {
        val registry = InMemoryDocumentSchemaRegistry(SchemaCompatibilityPolicy.NONE)
        val otherSubject = SchemaSubject.of("order")

        val first = registry.register(subject, userSchema())
        val second = registry.register(subject, userSchemaWithOptionalEmail())
        val other = registry.register(otherSubject, DocumentSchemaBuilder("Order").field("id", SchemaType.LONG).build())

        assertEquals(listOf(SchemaVersion.of(1), SchemaVersion.of(2)), registry.versions(subject))
        assertEquals(listOf(SchemaVersion.of(1)), registry.versions(otherSubject))
        assertEquals(SchemaId.of(2), second.id)
        assertEquals(SchemaId.of(3), other.id)
        assertEquals(2, registry.latest(subject)?.version?.value)
        assertEquals(first, registry.byId(SchemaId.of(1)))
        assertEquals(second, registry.byId(SchemaId.of(2)))
        assertEquals(otherSubject, registry.byId(SchemaId.of(3))?.subject)
    }

    @Test
    fun identicalRegistrationIsIdempotent() {
        val registry = InMemoryDocumentSchemaRegistry()

        val first = registry.register(subject, userSchema())
        val second = registry.register(subject, userSchema())

        assertEquals(first, second)
        assertEquals(listOf(SchemaVersion.of(1)), registry.versions(subject))
        assertEquals(1, registry.latest(subject)?.id?.value)
    }

    @Test
    fun sameSchemaContentSharesIdAcrossSubjects() {
        val registry = InMemoryDocumentSchemaRegistry(SchemaCompatibilityPolicy.NONE)
        val otherSubject = SchemaSubject.of("user-copy")

        val first = registry.register(subject, userSchema())
        val copy = registry.register(otherSubject, userSchema())

        assertEquals(first.id, copy.id)
        assertEquals(SchemaVersion.of(1), copy.version)
        assertEquals(listOf(SchemaVersion.of(1)), registry.versions(subject))
        assertEquals(listOf(SchemaVersion.of(1)), registry.versions(otherSubject))
    }

    @Test
    fun metadataDifferenceCreatesNewVersionWithSameId() {
        val registry = InMemoryDocumentSchemaRegistry(SchemaCompatibilityPolicy.NONE)

        val first = registry.register(subject, userSchema(), mapOf("owner" to "team-a"))
        val second = registry.register(subject, userSchema(), mapOf("owner" to "team-b"))

        assertEquals(first.id, second.id)
        assertEquals(SchemaVersion.of(1), first.version)
        assertEquals(SchemaVersion.of(2), second.version)
        assertEquals("team-b", registry.latest(subject)?.metadata?.get("owner"))
        assertEquals(listOf(SchemaVersion.of(1), SchemaVersion.of(2)), registry.versions(subject))
    }

    @Test
    fun metadataIsCopiedAndImmutable() {
        val registry = InMemoryDocumentSchemaRegistry(SchemaCompatibilityPolicy.NONE)
        val metadata = linkedMapOf("owner" to "team-a")

        val registered = registry.register(subject, userSchema(), metadata)
        metadata["owner"] = "changed"

        assertEquals(mapOf("owner" to "team-a"), registered.metadata)
        assertEquals("team-a", registry.latest(subject)?.metadata?.get("owner"))
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (registered.metadata as MutableMap<String, String>)["owner"] = "changed"
        }
    }

    @Test
    fun unknownLookupsReturnNullAndEmpty() {
        val registry = InMemoryDocumentSchemaRegistry()

        assertNull(registry.latest(subject))
        assertTrue(registry.versions(subject).isEmpty())
        assertNull(registry.byId(SchemaId.of(42)))
    }

    @Test
    fun defaultBackwardPolicyRejectsIncompatibleRegistration() {
        val registry = InMemoryDocumentSchemaRegistry()
        registry.register(subject, userSchema())

        assertFailsWith<SchemaCompatibilityException> {
            registry.register(
                subject,
                DocumentSchemaBuilder("User")
                    .field("id", SchemaType.LONG)
                    .field("name", SchemaType.STRING)
                    .field("age", SchemaType.INT, required = false, nullable = true)
                    .field("email", SchemaType.STRING)
                    .build(),
            )
        }
        assertEquals(listOf(SchemaVersion.of(1)), registry.versions(subject))
        assertEquals(listOf("id", "name", "age"), registry.latest(subject)?.documentSchema()?.fields?.map { it.name })
    }

    @Test
    fun compatibilityPolicyNONEAcceptsBreakingChange() {
        val registry = InMemoryDocumentSchemaRegistry(SchemaCompatibilityPolicy.NONE)
        registry.register(subject, userSchema())

        val breaking = registry.register(subject, DocumentSchemaBuilder("User").field("email", SchemaType.STRING).build())

        assertEquals(SchemaVersion.of(2), breaking.version)
        assertEquals(listOf(SchemaVersion.of(1), SchemaVersion.of(2)), registry.versions(subject))
    }

    @Test
    fun customCompatibilityCheckerIsUsed() {
        val registry = InMemoryDocumentSchemaRegistry { _, _ ->
            SchemaCompatibilityResult.incompatible("custom rule rejected the schema")
        }
        registry.register(subject, userSchema())

        val exception = assertFailsWith<SchemaCompatibilityException> {
            registry.register(subject, userSchemaWithOptionalEmail())
        }

        assertTrue(assertNotNull(exception.message).contains("custom rule rejected the schema"))
        assertEquals(listOf(SchemaVersion.of(1)), registry.versions(subject))
    }

    @Test
    fun registeredSchemaDecodesBackToDocumentSchema() {
        val registry = InMemoryDocumentSchemaRegistry(SchemaCompatibilityPolicy.NONE)
        val schema = DocumentSchemaBuilder("User")
            .field("id", SchemaType.LONG, description = "用户 id")
            .field("age", SchemaType.INT, required = false, nullable = true)
            .field("tags", SchemaType.ARRAY, required = false)
            .build()

        val registered = registry.register(subject, schema)

        assertSameDocumentSchema(schema, registered.documentSchema())
    }

    @Test
    fun deleteSubjectRemovesVersionsButKeepsGlobalIds() {
        val registry = InMemoryDocumentSchemaRegistry(SchemaCompatibilityPolicy.NONE)
        val first = registry.register(subject, userSchema())
        val second = registry.register(subject, userSchemaWithOptionalEmail())

        assertEquals(listOf(SchemaVersion.of(1), SchemaVersion.of(2)), registry.deleteSubject(subject))
        assertNull(registry.latest(subject))
        assertTrue(registry.versions(subject).isEmpty())
        assertEquals(first, registry.byId(first.id))
        assertEquals(second, registry.byId(second.id))
        assertFailsWith<SchemaNotFoundException> { registry.deleteSubject(subject) }
    }

    @Test
    fun invalidSchemasAreRejectedAtRegistration() {
        val registry = InMemoryDocumentSchemaRegistry()

        assertFailsWith<SchemaSerializationException> {
            registry.register(subject, DocumentSchema.of(" ", SchemaField.of("id", SchemaType.LONG)))
        }
        assertFailsWith<SchemaSerializationException> {
            registry.register(
                subject,
                DocumentSchema.of(
                    "User",
                    SchemaField.of("id", SchemaType.LONG),
                    SchemaField.of("id", SchemaType.STRING),
                ),
            )
        }
        assertTrue(registry.versions(subject).isEmpty())
    }

    @Test
    fun invalidValueObjectsAreRejected() {
        assertFailsWith<SchemaRegistryException> { SchemaSubject.of(" ") }
        assertFailsWith<SchemaRegistryException> { SchemaId.of(0) }
        assertFailsWith<SchemaRegistryException> { SchemaVersion.of(0) }
        assertFailsWith<SchemaRegistryException> { SchemaFormat.fromId("thrift") }
        assertEquals(SchemaFormat.JSON, SchemaFormat.fromId("json"))
    }

    private fun userSchema(): DocumentSchema = DocumentSchemaBuilder("User")
        .field("id", SchemaType.LONG)
        .field("name", SchemaType.STRING)
        .field("age", SchemaType.INT, required = false, nullable = true)
        .build()

    private fun userSchemaWithOptionalEmail(): DocumentSchema = DocumentSchemaBuilder("User")
        .field("id", SchemaType.LONG)
        .field("name", SchemaType.STRING)
        .field("age", SchemaType.INT, required = false, nullable = true)
        .field("email", SchemaType.STRING, required = false)
        .build()
}
