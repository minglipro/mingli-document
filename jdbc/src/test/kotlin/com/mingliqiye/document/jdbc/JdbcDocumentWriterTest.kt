package com.mingliqiye.document.jdbc

import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.jdbc.exception.JdbcWriteException
import com.mingliqiye.document.serialization.DocumentMapper
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import java.math.BigDecimal
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JdbcDocumentWriterTest {

    private fun writer(
        config: JdbcConfig = JdbcConfig.builder().build(),
        documentMapper: DocumentMapper? = null,
    ): JdbcDocumentWriter = JdbcDocumentWriter(documentMapper, config)

    @Test
    fun generatesInsertStatementAndBindsInOrder() {
        val writer = writer()
        val value = linkedMapOf<String, Any?>("id" to 1, "name" to "alice")
        val plan = writer.insertStatement("users", value)

        assertEquals("INSERT INTO users (id, name) VALUES (?, ?)", plan.sql)
        assertEquals(listOf("id", "name"), plan.parameters)

        val fake = FakePreparedStatement()
        val boundCount = writer.bind(fake.asPreparedStatement(), plan, value)

        assertEquals(2, boundCount)
        assertEquals(2, fake.bound.size)
        assertEquals("setObject", assertNotNull(fake.boundAt(1)).method)
        assertEquals(BigDecimal.ONE, assertNotNull(fake.boundAt(1)).value)
        assertEquals("alice", assertNotNull(fake.boundAt(2)).value)
    }

    @Test
    fun generatesInsertStatementWithNamingStrategy() {
        val config = JdbcConfig.builder().namingStrategy(PropertyNamingStrategy.SNAKE_CASE).build()
        val plan = writer(config).insertStatement("accounts", linkedMapOf("userName" to "bob", "loginCount" to 3))

        assertEquals("INSERT INTO accounts (user_name, login_count) VALUES (?, ?)", plan.sql)
        assertEquals(listOf("userName", "loginCount"), plan.parameters)
    }

    @Test
    fun generatesInsertStatementWithExplicitColumnMapping() {
        val config = JdbcConfig.builder().columnMapping("ID_COL", "id").build()
        val plan = writer(config).insertStatement("users", linkedMapOf("id" to 5))

        assertEquals("INSERT INTO users (ID_COL) VALUES (?)", plan.sql)
        val fake = FakePreparedStatement()
        writer(config).bind(fake.asPreparedStatement(), plan, linkedMapOf("id" to 5))
        assertEquals(BigDecimal.valueOf(5), assertNotNull(fake.boundAt(1)).value)
    }

    @Test
    fun generatesInsertStatementOmittingNullColumns() {
        val config = JdbcConfig.builder().nullPolicy(JdbcNullPolicy.OMIT).build()
        val writer = writer(config)
        val plan = writer.insertStatement("users", linkedMapOf("id" to 1, "name" to null))

        assertEquals("INSERT INTO users (id) VALUES (?)", plan.sql)
        assertEquals(listOf("id"), plan.parameters)

        assertFailsWith<JdbcWriteException> {
            writer.insertStatement("users", linkedMapOf("name" to null))
        }
    }

    @Test
    fun generatesUpdateStatement() {
        val writer = writer()
        val value = linkedMapOf<String, Any?>("id" to 1, "name" to "alice", "note" to null)
        val plan = writer.updateStatement("users", value, listOf("id"))

        assertEquals("UPDATE users SET name = ?, note = ? WHERE id = ?", plan.sql)
        assertEquals(listOf("name", "note", "id"), plan.parameters)

        val fake = FakePreparedStatement()
        writer.bind(fake.asPreparedStatement(), plan, value)
        assertEquals("alice", assertNotNull(fake.boundAt(1)).value)
        assertEquals("setNull", assertNotNull(fake.boundAt(2)).method)
        assertEquals(BigDecimal.ONE, assertNotNull(fake.boundAt(3)).value)
    }

    @Test
    fun generatesUpdateStatementWithOmitNullPolicy() {
        val config = JdbcConfig.builder().nullPolicy(JdbcNullPolicy.OMIT).build()
        val writer = writer(config)
        val plan = writer.updateStatement(
            "users",
            linkedMapOf("id" to 1, "name" to "alice", "note" to null),
            listOf("id"),
        )

        assertEquals("UPDATE users SET name = ? WHERE id = ?", plan.sql)
        assertEquals(listOf("name", "id"), plan.parameters)
    }

    @Test
    fun generatesUpdateStatementWithExplicitSetColumns() {
        val plan = writer().updateStatement(
            "users",
            linkedMapOf("id" to 1, "name" to "alice", "note" to "n"),
            listOf("id"),
            listOf("name"),
        )

        assertEquals("UPDATE users SET name = ? WHERE id = ?", plan.sql)
        assertEquals(listOf("name", "id"), plan.parameters)
    }

    @Test
    fun rejectsInvalidUpdateStatements() {
        val writer = writer()
        assertFailsWith<JdbcWriteException> {
            writer.updateStatement("users", linkedMapOf("id" to 1), emptyList())
        }
        assertFailsWith<JdbcWriteException> {
            writer.updateStatement("users", linkedMapOf("id" to 1), listOf("id"), emptyList())
        }
        assertFailsWith<JdbcWriteException> {
            writer.insertStatement(" ", linkedMapOf("id" to 1))
        }
    }

    @Test
    fun bindsNamedParameters() {
        val sql = "INSERT INTO users (name, age) VALUES (:name, :age)"
        val writer = writer()
        val plan = writer.namedStatement(sql)

        assertEquals(listOf("name", "age"), plan.parameters)

        val fake = FakePreparedStatement()
        val count = writer.bindNamed(
            fake.asPreparedStatement(),
            sql,
            linkedMapOf("name" to "alice", "age" to 30),
        )

        assertEquals(2, count)
        assertEquals("alice", assertNotNull(fake.boundAt(1)).value)
        assertEquals(BigDecimal.valueOf(30), assertNotNull(fake.boundAt(2)).value)
    }

    @Test
    fun bindsNamedParametersWithColumnNaming() {
        val config = JdbcConfig.builder().namingStrategy(PropertyNamingStrategy.SNAKE_CASE).build()
        val fake = FakePreparedStatement()
        writer(config).bindNamed(
            fake.asPreparedStatement(),
            "INSERT INTO accounts (user_name) VALUES (:user_name)",
            linkedMapOf("userName" to "bob"),
        )

        assertEquals("bob", assertNotNull(fake.boundAt(1)).value)
    }

    @Test
    fun convertsJavaValuesToJdbcParameters() {
        val writer = writer()
        val value = linkedMapOf<String, Any?>(
            "flag" to true,
            "ratio" to BigDecimal("1.5"),
            "payload" to byteArrayOf(1, 2, 3),
            "name" to "alice",
        )
        val fake = FakePreparedStatement()

        writer.bind(fake.asPreparedStatement(), listOf("flag", "ratio", "payload", "name"), value)

        assertEquals(true, assertNotNull(fake.boundAt(1)).value)
        assertEquals(BigDecimal("1.5"), assertNotNull(fake.boundAt(2)).value)
        assertEquals("setBytes", assertNotNull(fake.boundAt(3)).method)
        assertContentEquals(byteArrayOf(1, 2, 3), assertNotNull(fake.boundAt(3)).value as ByteArray)
        assertEquals("alice", assertNotNull(fake.boundAt(4)).value)
    }

    @Test
    fun bindsNullUsingDefaultSqlType() {
        val writer = writer()
        val fake = FakePreparedStatement()
        writer.bind(fake.asPreparedStatement(), listOf("name"), linkedMapOf("name" to null))

        val bound = assertNotNull(fake.boundAt(1))
        assertEquals("setNull", bound.method)
        assertEquals(Types.NULL, bound.sqlType)
    }

    @Test
    fun bindsNullUsingConfiguredSqlType() {
        val config = JdbcConfig.builder().defaultNullSqlType(Types.VARCHAR).build()
        val fake = FakePreparedStatement()
        writer(config).bind(fake.asPreparedStatement(), listOf("name"), linkedMapOf("name" to null))

        assertEquals(Types.VARCHAR, assertNotNull(fake.boundAt(1)).sqlType)
    }

    @Test
    fun bindsNullUsingParameterMetadataType() {
        val fake = FakePreparedStatement(parameterTypes = listOf(Types.VARCHAR))
        writer().bind(fake.asPreparedStatement(), listOf("name"), linkedMapOf("name" to null))

        assertEquals(Types.VARCHAR, assertNotNull(fake.boundAt(1)).sqlType)
    }

    @Test
    fun bindsTemporalStringsUsingParameterMetadata() {
        val timestampFake = FakePreparedStatement(parameterTypes = listOf(Types.TIMESTAMP))
        writer().bind(
            timestampFake.asPreparedStatement(),
            listOf("created"),
            linkedMapOf("created" to "2024-01-02T03:04:05"),
        )
        assertEquals(
            Timestamp.valueOf("2024-01-02 03:04:05"),
            assertNotNull(timestampFake.boundAt(1)).value,
        )

        val dateFake = FakePreparedStatement(parameterTypes = listOf(Types.DATE))
        writer().bind(dateFake.asPreparedStatement(), listOf("birthday"), linkedMapOf("birthday" to "2024-01-02"))
        assertEquals(java.sql.Date.valueOf("2024-01-02"), assertNotNull(dateFake.boundAt(1)).value)

        val invalidFake = FakePreparedStatement(parameterTypes = listOf(Types.TIMESTAMP))
        writer().bind(
            invalidFake.asPreparedStatement(),
            listOf("created"),
            linkedMapOf("created" to "not-a-date"),
        )
        assertEquals("not-a-date", assertNotNull(invalidFake.boundAt(1)).value)
    }

    @Test
    fun narrowsNumbersUsingParameterMetadata() {
        val longFake = FakePreparedStatement(parameterTypes = listOf(Types.BIGINT))
        writer().bind(longFake.asPreparedStatement(), listOf("id"), linkedMapOf("id" to 42))
        assertEquals(42L, assertNotNull(longFake.boundAt(1)).value)

        val intFake = FakePreparedStatement(parameterTypes = listOf(Types.INTEGER))
        writer().bind(intFake.asPreparedStatement(), listOf("id"), linkedMapOf("id" to 7))
        assertEquals(7, assertNotNull(intFake.boundAt(1)).value)

        val decimalFake = FakePreparedStatement(parameterTypes = listOf(Types.INTEGER))
        writer().bind(decimalFake.asPreparedStatement(), listOf("ratio"), linkedMapOf("ratio" to BigDecimal("1.5")))
        assertEquals(BigDecimal("1.5"), assertNotNull(decimalFake.boundAt(1)).value)
    }

    @Test
    fun appliesConfiguredTypeMapping() {
        val config = JdbcConfig.builder().typeMapping(String::class.java, Types.CLOB).build()
        val fake = FakePreparedStatement()
        writer(config).bind(fake.asPreparedStatement(), listOf("content"), linkedMapOf("content" to "text"))

        val bound = assertNotNull(fake.boundAt(1))
        assertEquals("setObject", bound.method)
        assertEquals(Types.CLOB, bound.sqlType)
        assertEquals("text", bound.value)
    }

    @Test
    fun writesBeansThroughDocumentMapper() {
        val bean = UserBean().apply {
            id = 3
            name = "alice"
            active = true
            score = BigDecimal("2.5")
        }
        val writer = writer(documentMapper = TestDocumentMapper())
        val plan = writer.insertStatement("users", bean)

        assertEquals(setOf("id", "name", "active", "score"), plan.parameters.toSet())
        val fake = FakePreparedStatement()
        assertEquals(4, writer.bind(fake.asPreparedStatement(), plan, bean))
        val boundByName = plan.parameters.mapIndexed { index, name -> name to assertNotNull(fake.boundAt(index + 1)).value }.toMap()
        assertEquals(BigDecimal.valueOf(3), boundByName["id"])
        assertEquals("alice", boundByName["name"])
        assertEquals(true, boundByName["active"])
        assertEquals(BigDecimal("2.5"), boundByName["score"])
    }

    @Test
    fun bindsNodeInputDirectly() {
        val node = ObjectNode.from(
            linkedMapOf(
                "id" to NumberNode.of(9),
                "name" to com.mingliqiye.document.core.StringNode.of("zed"),
            ),
        )
        val fake = FakePreparedStatement()
        writer().bind(fake.asPreparedStatement(), listOf("id", "name"), node)

        assertEquals(BigDecimal.valueOf(9), assertNotNull(fake.boundAt(1)).value)
        assertEquals("zed", assertNotNull(fake.boundAt(2)).value)
    }

    @Test
    fun rejectsMissingFieldsAndInvalidValues() {
        val writer = writer()
        val fake = FakePreparedStatement()
        val missing = assertFailsWith<JdbcWriteException> {
            writer.bind(fake.asPreparedStatement(), listOf("missing"), linkedMapOf("id" to 1))
        }
        assertTrue(missing.message!!.contains("missing"))

        assertFailsWith<JdbcWriteException> {
            writer.bind(fake.asPreparedStatement(), emptyList(), null)
        }
        assertFailsWith<JdbcWriteException> {
            writer.insertStatement("users", NumberNode.of(1))
        }
        assertFailsWith<JdbcWriteException> {
            writer.insertStatement("users", UserBean())
        }
        assertFailsWith<JdbcWriteException> {
            writer.insertStatement("users", linkedMapOf<Any, Any?>(1 to "value"))
        }
        assertFailsWith<JdbcWriteException> {
            writer.bind(
                fake.asPreparedStatement(),
                listOf("payload"),
                linkedMapOf<String, Any?>("payload" to ObjectNode.of()),
            )
        }
    }

    @Test
    fun omitsNullPolicyRejectsExplicitNullBinding() {
        val config = JdbcConfig.builder().nullPolicy(JdbcNullPolicy.OMIT).build()
        val exception = assertFailsWith<JdbcWriteException> {
            writer(config).bind(FakePreparedStatement().asPreparedStatement(), listOf("name"), linkedMapOf("name" to null))
        }
        assertTrue(exception.message!!.contains("OMIT"))
    }

    @Test
    fun wrapsSqlException() {
        val fake = FakePreparedStatement(failAt = 1)
        val exception = assertFailsWith<JdbcWriteException> {
            writer().bind(fake.asPreparedStatement(), listOf("name"), linkedMapOf("name" to "alice"))
        }
        assertTrue(exception.cause is SQLException)
    }

    @Test
    fun bindsEmptyParameterList() {
        val fake = FakePreparedStatement()
        assertEquals(0, writer().bind(fake.asPreparedStatement(), emptyList(), linkedMapOf("id" to 1)))
    }
}
