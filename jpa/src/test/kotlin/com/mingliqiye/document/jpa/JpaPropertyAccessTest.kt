package com.mingliqiye.document.jpa

import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.exception.JpaConfigException
import com.mingliqiye.document.jpa.exception.JpaMappingException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JpaPropertyAccessTest {

    private val mapper = JpaDocumentMapper()

    @Test
    fun mapsPropertiesThroughGettersAndSetters() {
        val entity = PropertyAccessEntity().apply {
            id = 1L
            name = "alice"
            ignored = "secret"
        }

        val node = mapper.toNode(entity)

        assertEquals(setOf("ID", "NAME"), node.keys)
        assertEquals("alice", node["NAME"]!!.asString())
        assertFalse(node.containsKey("ignored"))

        val copy = mapper.fromNode(
            ObjectNode.of("ID" to NumberNode.of(2), "NAME" to StringNode.of("bob")),
            PropertyAccessEntity::class.java,
        )
        assertEquals(2L, copy.id)
        assertEquals("bob", copy.name)
    }

    @Test
    fun includesTransientGetterWhenConfigured() {
        val including = JpaDocumentMapper(JpaConfig.builder().includeTransient(true).build())
        val node = including.toNode(
            PropertyAccessEntity().apply {
                id = 1L
                name = "alice"
                ignored = "secret"
            },
        )

        assertEquals("secret", node["ignored"]!!.asString())
        assertEquals(
            "secret",
            including.fromNode(node, PropertyAccessEntity::class.java).ignored,
        )
    }

    @Test
    fun autoDetectsPropertyAccessFromIdGetter() {
        val entity = AutoPropertyEntity().apply { id = 4L }

        val node = mapper.toNode(entity)

        assertEquals(setOf("id"), node.keys)
        assertEquals(4L, mapper.fromNode(node, AutoPropertyEntity::class.java).id)
    }

    @Test
    fun forcedFieldAccessUsesBackingFields() {
        val fieldMapper = JpaDocumentMapper(JpaConfig.builder().accessType(JpaAccessType.FIELD).build())
        val entity = PropertyAccessEntity().apply {
            id = 3L
            name = "carol"
        }

        val node = fieldMapper.toNode(entity)

        assertEquals(setOf("idValue", "nameValue"), node.keys)
        val copy = fieldMapper.fromNode(node, PropertyAccessEntity::class.java)
        assertEquals(3L, copy.id)
        assertEquals("carol", copy.name)
    }

    @Test
    fun rejectsNullForNonNullablePropertyGetter() {
        val node = ObjectNode.of("NAME" to com.mingliqiye.document.core.NullNode.INSTANCE)

        assertFailsWith<JpaMappingException> { mapper.fromNode(node, PropertyAccessEntity::class.java) }
    }

    @Test
    fun rejectsUnsupportedMapAssociationMetadata() {
        assertFailsWith<JpaConfigException> {
            mapper.toNode(BrokenMapAssociationEntity())
        }
    }

    @Test
    fun rejectsDuplicateDocumentFieldNames() {
        assertFailsWith<JpaConfigException> {
            mapper.toNode(DuplicateColumnEntity())
        }
    }

    @Test
    fun isEntityReflectsAnnotationPresence() {
        assertTrue(mapper.isEntity(PropertyAccessEntity::class.java))
        assertFalse(mapper.isEntity(PlainPojo::class.java))
    }
}
