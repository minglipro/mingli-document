package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.format.yaml.YamlMapper
import kotlin.test.Test
import kotlin.test.assertEquals

class KotlinYamlScalarCoercionTest {

    @Test
    fun numericYamlScalarIsCoercedToKotlinStringProperty() {
        val mapper = YamlMapper
            .builder()
            .addModules(kotlinModule())
            .build()

        val user = mapper.parse<User>(
            """
            age: 18
            name: 666
            """.trimIndent(),
        )

        assertEquals("666", user.name)
        assertEquals(18, user.age)
    }

    private data class User(
        val name: String,
        val age: Int,
    )
}
