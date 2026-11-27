package com.mingliqiye.document.core

import java.lang.reflect.ParameterizedType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * 公共 [DocumentTypeReference] 契约测试。
 *
 * 覆盖 reified 泛型、Class/Type 工厂、listType/mapType 以及 rawType 解析；
 * 与 [DocumentTypeReferenceTest] 互补。
 */
class DocumentTypeReferenceContractTest {

    @Test
    fun reifiedClassReferenceCarriesExactClass() {
        val reference = DocumentTypeReference.of<ContractModel>()

        assertEquals(ContractModel::class.java, reference.type)
        assertEquals(ContractModel::class.java, reference.getRawType())
        assertEquals(DocumentTypeReference.of(ContractModel::class.java), reference)
        assertEquals(reference, DocumentTypeReference.of(ContractModel::class.java))
    }

    @Test
    fun reifiedCollectionReferenceKeepsTypeArgument() {
        val reference = DocumentTypeReference.of<List<ContractModel>>()

        assertTrue(reference.type is ParameterizedType)
        val type = reference.type as ParameterizedType
        assertEquals(List::class.java, type.rawType)
        assertEquals(1, type.actualTypeArguments.size)
        assertEquals(ContractModel::class.java, type.actualTypeArguments[0])
        assertNull(type.ownerType)
        assertEquals(List::class.java as Class<*>, reference.getRawType() as Class<*>)
    }

    @Test
    fun nestedReifiedGenericsKeepFullTypeTree() {
        val reference = DocumentTypeReference.of<Map<String, List<Int>>>()
        val mapType = reference.type as ParameterizedType

        assertEquals(Map::class.java, mapType.rawType)
        assertNull(mapType.ownerType)
        assertEquals(2, mapType.actualTypeArguments.size)
        assertEquals(String::class.java, mapType.actualTypeArguments[0])

        val listType = mapType.actualTypeArguments[1] as ParameterizedType
        assertEquals(List::class.java, listType.rawType)
        assertNull(listType.ownerType)
        assertEquals(1, listType.actualTypeArguments.size)
        assertEquals(Int::class.javaObjectType, listType.actualTypeArguments[0])

        assertEquals(Map::class.java as Class<*>, reference.getRawType() as Class<*>)
    }

    @Test
    fun classFactoryWrapsExactClass() {
        val reference = DocumentTypeReference.of(ContractModel::class.java)

        assertSame(ContractModel::class.java, reference.type)
        assertEquals(ContractModel::class.java, reference.getRawType())
    }

    @Test
    fun typeFactoryPreservesSuppliedType() {
        val declared = DocumentTypeReference.of<List<ContractModel>>().type
        val reference = DocumentTypeReference.of<Any>(declared)

        assertSame(declared, reference.type)
        assertEquals(declared, reference.type)
        assertEquals(List::class.java as Class<*>, reference.getRawType() as Class<*>)
    }

    @Test
    fun listTypeFactoryExposesRawTypeAndElementType() {
        val reference = DocumentTypeReference.listType(ContractModel::class.java)
        val type = reference.type as ParameterizedType

        assertEquals(List::class.java as Class<*>, reference.getRawType() as Class<*>)
        assertEquals(List::class.java, type.rawType)
        assertNull(type.ownerType)
        assertEquals(1, type.actualTypeArguments.size)
        assertEquals(ContractModel::class.java, type.actualTypeArguments[0])
    }

    @Test
    fun mapTypeFactoryExposesRawTypeAndEntryTypes() {
        val reference = DocumentTypeReference.mapType(String::class.java, ContractModel::class.java)
        val type = reference.type as ParameterizedType

        assertEquals(Map::class.java as Class<*>, reference.getRawType() as Class<*>)
        assertEquals(Map::class.java, type.rawType)
        assertNull(type.ownerType)
        assertEquals(2, type.actualTypeArguments.size)
        assertEquals(String::class.java, type.actualTypeArguments[0])
        assertEquals(ContractModel::class.java, type.actualTypeArguments[1])
    }

    @Test
    fun equalityMatchesForNonGenericReferences() {
        val classReference = DocumentTypeReference.of(ContractModel::class.java)
        val reifiedReference = DocumentTypeReference.of<ContractModel>()

        assertEquals(classReference, reifiedReference)
        assertEquals(reifiedReference, classReference)
        assertEquals(classReference.hashCode(), reifiedReference.hashCode())
        assertNotEquals<Any>(classReference, DocumentTypeReference.of(String::class.java))
    }

    @Test
    fun parameterizedReferencesAreValueEqual() {
        val first = DocumentTypeReference.of<List<String>>()
        val second = DocumentTypeReference.of<List<String>>()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(
            DocumentTypeReference.listType(String::class.java),
            DocumentTypeReference.listType(String::class.java),
        )
        assertNotEquals<Any>(first, DocumentTypeReference.of<List<Int>>())
    }
}

/**
 * 测试用目标类型。
 */
private class ContractModel
