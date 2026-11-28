package com.mingliqiye.document.serialization.codegen

import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentRuntimeCodecTest {

    @Test
    fun runtimeConverterRoundTrip() {
        val mapper = JsonMapper.builder()
            .addConverter(DocumentRuntimeCodecFactory().converter(CodecUser::class.java))
            .build()

        val json = mapper.format(
            CodecUser().apply {
                name = "小于"
                age = 18
            },
        )
        val user = mapper.parse(json, CodecUser::class.java)
        assertEquals("小于", user.name)
        assertEquals(18, user.age)
    }

    @Test
    fun namingAndIgnore() {
        val mapper = JsonMapper.builder()
            .addConverter(
                DocumentRuntimeCodecFactory(namingStrategy = PropertyNamingStrategy.SNAKE_CASE)
                    .converter(NamingUser::class.java),
            )
            .build()

        val json = mapper.format(
            NamingUser().apply {
                userName = "x"
                secret = "hidden"
            },
        )
        assertEquals("""{"user_name":"x"}""", json)

        val user = mapper.parse("""{"user_name":"y"}""", NamingUser::class.java)
        assertEquals("y", user.userName)
        assertNull(user.secret)
    }

    @Test
    fun writeNulls() {
        val mapper = JsonMapper.builder()
            .addConverter(
                DocumentRuntimeCodecFactory(writeNulls = true)
                    .converter(NullUser::class.java),
            )
            .build()
        assertEquals("""{"name":null}""", mapper.format(NullUser()))
    }
}

class CodecUser {
    @DocumentName("name")
    var name: String? = null

    @DocumentName("age")
    var age: Int = 0
}

class NamingUser {
    var userName: String? = null

    @DocumentIgnore
    var secret: String? = null
}

class NullUser {
    var name: String? = null
}
