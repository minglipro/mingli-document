package com.mingliqiye.document.jdbc

import com.mingliqiye.document.serialization.PropertyNamingStrategy
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbcConfigTest {

    @Test
    fun hasExpectedDefaults() {
        val config = JdbcConfig.builder().build()

        assertEquals(PropertyNamingStrategy.AS_IS, config.namingStrategy)
        assertEquals(JdbcNullPolicy.INCLUDE, config.nullPolicy)
        assertEquals(Types.NULL, config.defaultNullSqlType)
        assertTrue(config.columnMappings.isEmpty())
        assertTrue(config.typeMappings.isEmpty())
    }

    @Test
    fun asIsKeepsColumnAndFieldNames() {
        val config = JdbcConfig.builder().build()

        assertEquals("user_name", config.columnToFieldName("user_name"))
        assertEquals("user_name", config.fieldToColumnName("user_name"))
    }

    @Test
    fun namingStrategyConvertsBothDirections() {
        val config = JdbcConfig.builder().namingStrategy(PropertyNamingStrategy.SNAKE_CASE).build()

        assertEquals("userName", config.columnToFieldName("USER_NAME"))
        assertEquals("userName", config.columnToFieldName("user_name"))
        assertEquals("user_name", config.fieldToColumnName("userName"))

        val kebab = JdbcConfig.builder().namingStrategy(PropertyNamingStrategy.KEBAB_CASE).build()
        assertEquals("userName", kebab.columnToFieldName("user-name"))
        assertEquals("user-name", kebab.fieldToColumnName("userName"))
    }

    @Test
    fun explicitColumnMappingWins() {
        val config = JdbcConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .columnMapping("ID_COL", "id")
            .build()

        assertEquals("id", config.columnToFieldName("ID_COL"))
        assertEquals("ID_COL", config.fieldToColumnName("id"))
        assertEquals("userName", config.columnToFieldName("USER_NAME"))
    }

    @Test
    fun sqlTypeForMatchesExactTypeAndSupertypes() {
        val config = JdbcConfig.builder()
            .typeMapping(Integer::class.java, Types.INTEGER)
            .typeMapping(Number::class.java, Types.NUMERIC)
            .typeMapping(CharSequence::class.java, Types.VARCHAR)
            .build()

        assertEquals(Types.INTEGER, config.sqlTypeFor(Integer::class.java))
        assertEquals(Types.NUMERIC, config.sqlTypeFor(java.lang.Long::class.java))
        assertEquals(Types.VARCHAR, config.sqlTypeFor(String::class.java))
        assertNull(config.sqlTypeFor(java.sql.Timestamp::class.java))
        assertNull(config.sqlTypeFor(null))
    }

    @Test
    fun builderValidatesArguments() {
        assertFailsWith<IllegalArgumentException> { JdbcConfig.builder().columnMapping("", "field") }
        assertFailsWith<IllegalArgumentException> { JdbcConfig.builder().columnMapping("column", "") }
        assertFailsWith<IllegalArgumentException> { JdbcConfig.builder().columnConverter("", ColumnConverter { _, _ -> null }) }
        assertFailsWith<IllegalArgumentException> { JdbcConfig.builder().columnConverter(0, ColumnConverter { _, _ -> null }) }
    }

    @Test
    fun jdbcStatementCopiesParametersAndValidates() {
        val parameters = mutableListOf("id", "name")
        val statement = JdbcStatement("SELECT * FROM users WHERE id = ? AND name = ?", parameters)
        parameters.add("extra")

        assertEquals(listOf("id", "name"), statement.parameters)
        assertFailsWith<IllegalArgumentException> { JdbcStatement(" ", listOf("id")) }
        assertFailsWith<IllegalArgumentException> { JdbcStatement("SELECT 1", listOf("")) }
    }

    @Test
    fun jdbcColumnValidatesArguments() {
        assertFailsWith<IllegalArgumentException> { JdbcColumn(0, "id", Types.INTEGER) }
        assertFailsWith<IllegalArgumentException> { JdbcColumn(1, "", Types.INTEGER) }
        val column = JdbcColumn(1, "id", Types.INTEGER, "java.lang.Integer")
        assertTrue(column.toString().contains("INTEGER"))
    }
}
