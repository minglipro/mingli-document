package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.format.yaml.exception.YamlParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class YamlAliasLimitTest {

    private val text = """
        base: &base {a: 1}
        one: *base
        two: *base
    """.trimIndent()

    @Test
    fun maxAliasesRejectsAliasExpansion() {
        assertFailsWith<YamlParseException> {
            YamlReader(text, maxAliases = 1).read()
        }
        YamlReader(text, maxAliases = 2).read()
    }
}
