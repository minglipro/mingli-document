package com.mingliqiye.document.serialization

import com.mingliqiye.utils.aliasfor.AliasFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DocumentPropertyTest {

    @Test
    fun aliasAnnotationReader() {
        val field = AliasBean::class.java.getDeclaredField("name")
        val property = DefaultDocumentProperty(
            AliasBean::class.java,
            "name",
            String::class.java,
            listOf(field),
        )

        val annotation = property.getAnnotation(AliasedName::class.java)
        assertNotNull(annotation)
        assertEquals("aliased", annotation.value)
        assertEquals("aliased", annotation.alias)
    }

    private class AliasBean {
        @field:AliasedName("aliased")
        var name: String = ""
    }
}

/**
 * 测试用别名注解。
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
private annotation class AliasedName(
    @get:AliasFor("alias") val value: String = "",
    @get:AliasFor("value") val alias: String = "",
)
