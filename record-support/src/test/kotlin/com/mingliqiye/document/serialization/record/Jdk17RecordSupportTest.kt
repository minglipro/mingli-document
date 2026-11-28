package com.mingliqiye.document.serialization.record

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.serialization.JavaRecordSupport
import java.lang.reflect.ParameterizedType
import java.lang.reflect.RecordComponent
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [Jdk17RecordSupport] 的 SPI 契约测试。
 *
 * 验证 provider 能被 [ServiceLoader] 发现、record 判断、组件元数据（名称/泛型类型/
 * 访问器/RecordComponent 元素）与空 record / 非 record 的边界行为。
 */
class Jdk17RecordSupportTest {

    private val support = Jdk17RecordSupport()

    @Test
    fun serviceLoaderDiscoversProvider() {
        val providers = ServiceLoader.load(JavaRecordSupport::class.java).toList()

        assertTrue(
            providers.any { it is Jdk17RecordSupport },
            "expected Jdk17RecordSupport in ${providers.map { it.javaClass.name }}",
        )
    }

    @Test
    fun isRecordRecognizesJavaRecords() {
        assertTrue(support.isRecord(RecordAddress::class.java))
        assertTrue(support.isRecord(EmptyRecord::class.java))
        assertFalse(support.isRecord(String::class.java))
        assertFalse(support.isRecord(Any::class.java))
        assertFalse(support.isRecord(Jdk17RecordSupportTest::class.java))
    }

    @Test
    fun componentsExposeNameTypeAccessorAndElement() {
        val components = support.components(RecordUser::class.java)

        assertEquals(
            listOf("name", "age", "address", "tags", "scores", "internalTag", "nickname"),
            components.map { it.name },
        )
        assertEquals<Any?>(String::class.java, components[0].type)
        assertEquals<Any?>(Int::class.javaPrimitiveType, components[1].type)
        assertEquals<Any?>(RecordAddress::class.java, components[2].type)

        val tagsType = components[3].type as ParameterizedType
        assertEquals<Any?>(List::class.java, tagsType.rawType)
        assertEquals<Any?>(String::class.java, tagsType.actualTypeArguments[0])

        assertEquals("name", components[0].accessor.name)
        assertEquals(RecordUser::class.java.getMethod("name"), components[0].accessor)
        assertEquals("nickname", components[6].accessor.name)

        assertTrue(components[0].element is RecordComponent)
        assertNotNull(
            components[0].accessor.getAnnotation(DocumentName::class.java),
            "record component annotation should be visible on the accessor",
        )
    }

    @Test
    fun nonRecordHasNoComponents() {
        assertTrue(support.components(String::class.java).isEmpty())
        assertTrue(support.components(Jdk17RecordSupportTest::class.java).isEmpty())
    }

    @Test
    fun emptyRecordIsRecordWithNoComponents() {
        assertTrue(support.isRecord(EmptyRecord::class.java))
        assertTrue(support.components(EmptyRecord::class.java).isEmpty())
    }
}
