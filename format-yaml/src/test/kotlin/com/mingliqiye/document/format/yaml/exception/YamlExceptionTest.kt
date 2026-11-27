package com.mingliqiye.document.format.yaml.exception

import com.mingliqiye.document.exception.DocumentException
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class YamlExceptionTest {

    @Test
    fun yamlParseExceptionExtendsYamlException() {
        assertTrue(YamlParseException("parse") is YamlException)
        assertTrue(YamlException("yaml") is DocumentException)
    }

    @Test
    fun keepsCause() {
        val cause = IllegalStateException("root")
        assertSame(cause, YamlParseException("yaml", cause).cause)
    }
}
