package com.mingliqiye.document.core

import java.lang.reflect.ParameterizedType
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentTypeReferenceTest {

    @Test
    fun reifiedGenericType() {
        val reference = DocumentTypeReference.of<List<String>>()
        val rawType = reference.type as ParameterizedType

        assertEquals(List::class.java as Class<*>, reference.getRawType() as Class<*>)
        assertEquals(List::class.java, rawType.rawType)
        assertEquals(String::class.java, rawType.actualTypeArguments[0])
    }

    @Test
    fun classAndTypeFactories() {
        assertEquals(User::class.java, DocumentTypeReference.of(User::class.java).type)

        val reference = DocumentTypeReference.of<List<User>>()
        val rawType = reference.type as ParameterizedType
        assertEquals(User::class.java, rawType.actualTypeArguments[0])
    }

    @Test
    fun listAndMapFactories() {
        val listType = DocumentTypeReference.listType(User::class.java).type as ParameterizedType
        assertEquals(List::class.java, listType.rawType)
        assertEquals(User::class.java, listType.actualTypeArguments[0])

        val mapType = DocumentTypeReference.mapType(String::class.java, User::class.java).type as ParameterizedType
        assertEquals(Map::class.java, mapType.rawType)
        assertEquals(String::class.java, mapType.actualTypeArguments[0])
        assertEquals(User::class.java, mapType.actualTypeArguments[1])
    }

    private class User
}
