package com.mingliqiye.document.registry

import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.registry.exception.SchemaRegistryException
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SchemaCompatibilityPolicy] 兼容性规则测试。
 */
class SchemaCompatibilityPolicyTest {

    @Test
    fun noneAcceptsBreakingChange() {
        val result = check(
            SchemaCompatibilityPolicy.NONE,
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
            DocumentSchemaBuilder("Doc").field("id", SchemaType.STRING).field("required", SchemaType.INT).build(),
        )

        assertTrue(result.compatible)
        assertTrue(result.violations.isEmpty())
    }

    @Test
    fun backwardAllowsAddedOptionalField() {
        assertTrue(
            check(
                SchemaCompatibilityPolicy.BACKWARD,
                DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
                DocumentSchemaBuilder("Doc")
                    .field("id", SchemaType.LONG)
                    .field("name", SchemaType.STRING, required = false)
                    .build(),
            ).compatible,
        )
    }

    @Test
    fun backwardRejectsAddedRequiredField() {
        val result = check(
            SchemaCompatibilityPolicy.BACKWARD,
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
            DocumentSchemaBuilder("Doc")
                .field("id", SchemaType.LONG)
                .field("name", SchemaType.STRING)
                .build(),
        )

        assertFalse(result.compatible)
        assertTrue(result.violations.any { it.contains("required field 'name' was added") }, result.violations.toString())
    }

    @Test
    fun backwardAllowsRemovedField() {
        assertTrue(
            check(
                SchemaCompatibilityPolicy.BACKWARD,
                DocumentSchemaBuilder("Doc")
                    .field("id", SchemaType.LONG)
                    .field("name", SchemaType.STRING)
                    .build(),
                DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
            ).compatible,
        )
    }

    @Test
    fun backwardRejectsTypeChange() {
        val result = check(
            SchemaCompatibilityPolicy.BACKWARD,
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
            DocumentSchemaBuilder("Doc").field("id", SchemaType.STRING).build(),
        )

        assertFalse(result.compatible)
        assertTrue(result.violations.any { it.contains("changed type from LONG to STRING") }, result.violations.toString())
    }

    @Test
    fun backwardRejectsOptionalToRequiredAndNullableToNonNullable() {
        val optionalToRequired = check(
            SchemaCompatibilityPolicy.BACKWARD,
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG, required = false).build(),
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
        )
        assertFalse(optionalToRequired.compatible)
        assertTrue(optionalToRequired.violations.any { it.contains("optional to required") })

        val nullableToNonNullable = check(
            SchemaCompatibilityPolicy.BACKWARD,
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG, required = false, nullable = true).build(),
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG, required = false).build(),
        )
        assertFalse(nullableToNonNullable.compatible)
        assertTrue(nullableToNonNullable.violations.any { it.contains("nullable to non-nullable") })
    }

    @Test
    fun forwardAllowsAddedRequiredField() {
        assertTrue(
            check(
                SchemaCompatibilityPolicy.FORWARD,
                DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
                DocumentSchemaBuilder("Doc")
                    .field("id", SchemaType.LONG)
                    .field("name", SchemaType.STRING)
                    .build(),
            ).compatible,
        )
    }

    @Test
    fun forwardRejectsRemovedRequiredField() {
        val result = check(
            SchemaCompatibilityPolicy.FORWARD,
            DocumentSchemaBuilder("Doc")
                .field("id", SchemaType.LONG)
                .field("name", SchemaType.STRING)
                .build(),
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
        )

        assertFalse(result.compatible)
        assertTrue(result.violations.any { it.contains("required field 'name' was removed") }, result.violations.toString())
    }

    @Test
    fun forwardAllowsRemovedOptionalField() {
        assertTrue(
            check(
                SchemaCompatibilityPolicy.FORWARD,
                DocumentSchemaBuilder("Doc")
                    .field("id", SchemaType.LONG)
                    .field("name", SchemaType.STRING, required = false)
                    .build(),
                DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
            ).compatible,
        )
    }

    @Test
    fun forwardRejectsRequiredToOptionalAndNonNullableToNullable() {
        val requiredToOptional = check(
            SchemaCompatibilityPolicy.FORWARD,
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG, required = false).build(),
        )
        assertFalse(requiredToOptional.compatible)
        assertTrue(requiredToOptional.violations.any { it.contains("required to optional") })

        val nonNullableToNullable = check(
            SchemaCompatibilityPolicy.FORWARD,
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG, nullable = true).build(),
        )
        assertFalse(nonNullableToNullable.compatible)
        assertTrue(nonNullableToNullable.violations.any { it.contains("non-nullable to nullable") })
    }

    @Test
    fun fullAcceptsCompatibleChangesAndRejectsEachDirectionViolation() {
        val previous = DocumentSchemaBuilder("Doc")
            .field("id", SchemaType.LONG)
            .field("name", SchemaType.STRING, required = false)
            .build()

        assertTrue(
            check(
                SchemaCompatibilityPolicy.FULL,
                previous,
                DocumentSchemaBuilder("Doc")
                    .field("id", SchemaType.LONG)
                    .field("name", SchemaType.STRING, required = false)
                    .field("extra", SchemaType.INT, required = false)
                    .build(),
            ).compatible,
        )
        assertTrue(check(SchemaCompatibilityPolicy.FULL, previous, previous.deepCopy()).compatible)
        assertFalse(
            check(
                SchemaCompatibilityPolicy.FULL,
                previous,
                DocumentSchemaBuilder("Doc").field("id", SchemaType.STRING).build(),
            ).compatible,
        )
        val addedRequired = check(
            SchemaCompatibilityPolicy.FULL,
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
            DocumentSchemaBuilder("Doc")
                .field("id", SchemaType.LONG)
                .field("name", SchemaType.STRING)
                .build(),
        )
        assertFalse(addedRequired.compatible)
        assertTrue(addedRequired.violations.any { it.startsWith("backward:") }, addedRequired.violations.toString())

        val removedRequired = check(
            SchemaCompatibilityPolicy.FULL,
            DocumentSchemaBuilder("Doc")
                .field("id", SchemaType.LONG)
                .field("name", SchemaType.STRING)
                .build(),
            DocumentSchemaBuilder("Doc").field("id", SchemaType.LONG).build(),
        )
        assertFalse(removedRequired.compatible)
        assertTrue(removedRequired.violations.any { it.startsWith("forward:") }, removedRequired.violations.toString())
    }

    @Test
    fun resultRequiresViolationsWhenIncompatible() {
        assertFailsWith<SchemaRegistryException> {
            SchemaCompatibilityResult.incompatible(emptyList())
        }
        val result = SchemaCompatibilityResult.incompatible("rule-a", "rule-b")

        assertEquals(listOf("rule-a", "rule-b"), result.violations)
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (result.violations as MutableList<String>).clear()
        }
    }

    private fun check(
        policy: SchemaCompatibilityPolicy,
        previous: DocumentSchema,
        candidate: DocumentSchema,
    ): SchemaCompatibilityResult {
        val previousNode: ObjectNode = DocumentSchemaCodec.encode(previous)
        val candidateNode: ObjectNode = DocumentSchemaCodec.encode(candidate)
        return policy.check(previousNode, candidateNode)
    }

    private fun DocumentSchema.deepCopy(): DocumentSchema {
        val node = DocumentSchemaCodec.encode(this)
        return DocumentSchemaCodec.decode(node)
    }
}
