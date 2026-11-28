package com.mingliqiye.document.jdbc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JdbcNamedParametersTest {

    private val writer = JdbcDocumentWriter()

    @Test
    fun parsesSimpleParameters() {
        val statement = writer.namedStatement("INSERT INTO users (name, age) VALUES (:name, :age)")

        assertEquals(listOf("name", "age"), statement.parameters)
    }

    @Test
    fun parsesNoParameters() {
        assertTrue(writer.namedStatement("SELECT 1").parameters.isEmpty())
    }

    @Test
    fun ignoresSingleQuotedLiterals() {
        val statement = writer.namedStatement(
            "INSERT INTO users (name) VALUES (':fake', :real, 'it''s :also-fake')",
        )

        assertEquals(listOf("real"), statement.parameters)
    }

    @Test
    fun ignoresQuotedIdentifiers() {
        val statement = writer.namedStatement("SELECT \":fake\", `:fake2` FROM t WHERE id = :id")

        assertEquals(listOf("id"), statement.parameters)
    }

    @Test
    fun ignoresComments() {
        val statement = writer.namedStatement(
            "SELECT * FROM t /* :block */ WHERE a = :first -- :line\n AND b = :second",
        )

        assertEquals(listOf("first", "second"), statement.parameters)
    }

    @Test
    fun ignoresPostgresCastOperator() {
        val statement = writer.namedStatement("SELECT :value::text, :other::int")

        assertEquals(listOf("value", "other"), statement.parameters)
    }

    @Test
    fun supportsUnderscoresAndDigits() {
        val statement = writer.namedStatement("SELECT :user_name, :a1, :_hidden")

        assertEquals(listOf("user_name", "a1", "_hidden"), statement.parameters)
    }

    @Test
    fun supportsRepeatedParameters() {
        val statement = writer.namedStatement("UPDATE t SET n = :value WHERE n = :value")

        assertEquals(listOf("value", "value"), statement.parameters)
    }

    @Test
    fun bindsRepeatedParameters() {
        val fake = FakePreparedStatement()
        val count = writer.bindNamed(
            fake.asPreparedStatement(),
            "UPDATE t SET n = :value WHERE n = :value",
            linkedMapOf("value" to 5),
        )

        assertEquals(2, count)
        assertEquals(2, fake.bound.size)
        assertEquals(java.math.BigDecimal.valueOf(5), assertNotNull(fake.boundAt(1)).value)
        assertEquals(java.math.BigDecimal.valueOf(5), assertNotNull(fake.boundAt(2)).value)
    }
}
