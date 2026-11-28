package com.mingliqiye.document.jdbc

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jdbc.exception.JdbcReadException
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import java.math.BigDecimal
import java.sql.SQLException
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class JdbcDocumentReaderTest {

    private fun reader(
        config: JdbcConfig = JdbcConfig.builder().build(),
    ): JdbcDocumentReader = JdbcDocumentReader(config)

    @Test
    fun readsNodesWithTypeInference() {
        val resultSet = FakeResultSet(
            listOf(
                FakeColumn("id", Types.INTEGER, "java.lang.Integer"),
                FakeColumn("name", Types.VARCHAR, "java.lang.String"),
                FakeColumn("active", Types.BOOLEAN, "java.lang.Boolean"),
                FakeColumn("ratio", Types.DECIMAL, "java.math.BigDecimal"),
                FakeColumn("payload", Types.VARBINARY, "[B"),
                FakeColumn("created", Types.TIMESTAMP, "java.sql.Timestamp"),
                FakeColumn("birthday", Types.DATE, "java.time.LocalDate"),
                FakeColumn("bit_flag", Types.BIT, "java.lang.Integer"),
            ),
            listOf(
                listOf(
                    1,
                    "alice",
                    true,
                    BigDecimal("1.50"),
                    byteArrayOf(1, 2, 3),
                    Timestamp.valueOf("2024-01-02 03:04:05"),
                    LocalDate.of(1990, 5, 6),
                    1,
                ),
                listOf(2, null, false, null, null, null, null, 0),
            ),
        ).asResultSet()

        val rows = reader().readNodes(resultSet)

        assertEquals(2, rows.size)
        val first = rows[0]
        assertEquals(1, assertNotNull(first["id"]).asNumber().toInt())
        assertEquals("alice", assertNotNull(first["name"]).asString())
        assertTrue(assertNotNull(first["active"]).asBoolean())
        assertEquals(BigDecimal("1.50"), assertNotNull(first["ratio"]).asNumber().toBigDecimal())
        assertContentEquals(byteArrayOf(1, 2, 3), assertNotNull(first["payload"]).asBinary())
        assertEquals("2024-01-02T03:04:05", assertNotNull(first["created"]).asString())
        assertEquals("1990-05-06", assertNotNull(first["birthday"]).asString())
        assertTrue(assertNotNull(first["bit_flag"]).asBoolean())

        val second = rows[1]
        assertEquals(2, assertNotNull(second["id"]).asNumber().toInt())
        assertTrue(assertNotNull(second["name"]).isNull)
        assertFalse(assertNotNull(second["active"]).asBoolean())
    }

    @Test
    fun appliesColumnNamingStrategyAndExplicitMapping() {
        val resultSet = FakeResultSet(
            listOf(
                FakeColumn("USER_NAME", Types.VARCHAR, "java.lang.String"),
                FakeColumn("LOGIN_COUNT", Types.INTEGER, "java.lang.Integer"),
            ),
            listOf(listOf("bob", 3)),
        ).asResultSet()

        val named = reader(JdbcConfig.builder().namingStrategy(PropertyNamingStrategy.SNAKE_CASE).build())
            .readNodes(resultSet)
        assertEquals("bob", assertNotNull(named[0]["userName"]).asString())
        assertEquals(3, assertNotNull(named[0]["loginCount"]).asNumber().toInt())

        val resultSet2 = FakeResultSet(
            listOf(FakeColumn("USER_NAME", Types.VARCHAR, "java.lang.String")),
            listOf(listOf("carol")),
        ).asResultSet()
        val mapped = reader(JdbcConfig.builder().columnMapping("USER_NAME", "login").build()).readNodes(resultSet2)
        assertEquals("carol", assertNotNull(mapped[0]["login"]).asString())
        assertFalse(mapped[0].containsKey("USER_NAME"))
    }

    @Test
    fun fallsBackToColumnNameWhenLabelIsEmpty() {
        val resultSet = FakeResultSet(
            listOf(FakeColumn("", Types.VARCHAR, "java.lang.String", columnName = "RAW_NAME")),
            listOf(listOf("value")),
        ).asResultSet()

        val rows = reader().readNodes(resultSet)

        assertEquals("value", assertNotNull(rows[0]["RAW_NAME"]).asString())
    }

    @Test
    fun nullPolicyIncludeAndOmit() {
        val resultSet = FakeResultSet(
            listOf(
                FakeColumn("id", Types.INTEGER, "java.lang.Integer"),
                FakeColumn("name", Types.VARCHAR, "java.lang.String"),
            ),
            listOf(listOf(1, null)),
        ).asResultSet()

        val included = reader().readNodes(resultSet)[0]
        assertTrue(included.containsKey("name"))
        assertTrue(assertNotNull(included["name"]).isNull)

        val resultSet2 = FakeResultSet(
            listOf(
                FakeColumn("id", Types.INTEGER, "java.lang.Integer"),
                FakeColumn("name", Types.VARCHAR, "java.lang.String"),
            ),
            listOf(listOf(1, null)),
        ).asResultSet()
        val omitted = reader(JdbcConfig.builder().nullPolicy(JdbcNullPolicy.OMIT).build()).readNodes(resultSet2)[0]
        assertFalse(omitted.containsKey("name"))
    }

    @Test
    fun readsBlobAndClobColumns() {
        val bytes = byteArrayOf(9, 8, 7)
        val resultSet = FakeResultSet(
            listOf(
                FakeColumn("payload", Types.BLOB, "java.sql.Blob"),
                FakeColumn("content", Types.CLOB, "java.sql.Clob"),
            ),
            listOf(listOf(blobOf(bytes), clobOf("hello clob"))),
        ).asResultSet()

        val row = reader().readNodes(resultSet)[0]

        assertContentEquals(bytes, assertNotNull(row["payload"]).asBinary())
        assertEquals("hello clob", assertNotNull(row["content"]).asString())
    }

    @Test
    fun usesCustomColumnConverter() {
        val converter = ColumnConverter { value, column ->
            assertEquals("status", column.label)
            assertEquals(Types.VARCHAR, column.sqlType)
            if (value == null) NullNode.INSTANCE else StringNode.of("状态:$value")
        }
        val resultSet = FakeResultSet(
            listOf(FakeColumn("status", Types.VARCHAR, "java.lang.String")),
            listOf(listOf("OK")),
        ).asResultSet()

        val row = reader(JdbcConfig.builder().columnConverter("status", converter).build()).readNodes(resultSet)[0]

        assertEquals("状态:OK", assertNotNull(row["status"]).asString())
    }

    @Test
    fun customConverterIsAppliedToSqlNullBeforeNullPolicy() {
        val converter = ColumnConverter { value, _ ->
            if (value == null) StringNode.of("(missing)") else null
        }
        val resultSet = FakeResultSet(
            listOf(FakeColumn("note", Types.VARCHAR, "java.lang.String")),
            listOf(listOf(null)),
        ).asResultSet()

        val row = reader(JdbcConfig.builder().columnConverter("note", converter).build()).readNodes(resultSet)[0]

        assertEquals("(missing)", assertNotNull(row["note"]).asString())
    }

    @Test
    fun customConverterFallsBackToBuiltInConversion() {
        val converter = ColumnConverter { _, _ -> null }
        val resultSet = FakeResultSet(
            listOf(FakeColumn("name", Types.VARCHAR, "java.lang.String")),
            listOf(listOf("raw")),
        ).asResultSet()

        val row = reader(JdbcConfig.builder().columnConverter("name", converter).build()).readNodes(resultSet)[0]

        assertEquals("raw", assertNotNull(row["name"]).asString())
    }

    @Test
    fun columnIndexConverterTakesPrecedenceOverLabelConverter() {
        val byLabel = ColumnConverter { _, _ -> StringNode.of("by-label") }
        val byIndex = ColumnConverter { _, _ -> StringNode.of("by-index") }
        val config = JdbcConfig.builder()
            .columnConverter("name", byLabel)
            .columnConverter(1, byIndex)
            .build()
        val resultSet = FakeResultSet(
            listOf(FakeColumn("name", Types.VARCHAR, "java.lang.String")),
            listOf(listOf("raw")),
        ).asResultSet()

        val row = reader(config).readNodes(resultSet)[0]

        assertEquals("by-index", assertNotNull(row["name"]).asString())
    }

    @Test
    fun wrapsSqlExceptionAndConversionFailure() {
        val nextFailure = FakeResultSet(
            listOf(FakeColumn("id", Types.INTEGER, "java.lang.Integer")),
            listOf(listOf(1)),
        )
        nextFailure.nextFailure = SQLException("next failed")
        val nextException = assertFailsWith<JdbcReadException> { reader().readNodes(nextFailure.asResultSet()) }
        assertTrue(nextException.message!!.contains("下一行"))
        assertNotNull(nextException.cause)

        val objectFailure = FakeResultSet(
            listOf(FakeColumn("id", Types.INTEGER, "java.lang.Integer")),
            listOf(listOf(1)),
        )
        objectFailure.objectFailure = SQLException("getObject failed")
        val objectException = assertFailsWith<JdbcReadException> { reader().readNodes(objectFailure.asResultSet()) }
        assertTrue(objectException.message!!.contains("'id'"))
        assertNotNull(objectException.cause)

        val metadataFailure = FakeResultSet(
            listOf(FakeColumn("id", Types.INTEGER, "java.lang.Integer")),
            listOf(listOf(1)),
        )
        metadataFailure.metadataFailure = SQLException("metadata failed")
        assertFailsWith<JdbcReadException> { reader().readNodes(metadataFailure.asResultSet()) }

        val invalidNumber = FakeResultSet(
            listOf(FakeColumn("ratio", Types.DOUBLE, "java.lang.Double")),
            listOf(listOf(Double.NaN)),
        ).asResultSet()
        val conversionException = assertFailsWith<JdbcReadException> { reader().readNodes(invalidNumber) }
        assertTrue(conversionException.message!!.contains("'ratio'"))
    }

    @Test
    fun wrapsConverterAndRowMapperFailures() {
        val converterFailure = ColumnConverter { _, _ -> throw IllegalStateException("converter broken") }
        val resultSet = FakeResultSet(
            listOf(FakeColumn("value", Types.VARCHAR, "java.lang.String")),
            listOf(listOf("x")),
        ).asResultSet()
        val converterException = assertFailsWith<JdbcReadException> {
            reader(JdbcConfig.builder().columnConverter("value", converterFailure).build()).readNodes(resultSet)
        }
        assertTrue(converterException.message!!.contains("'value'"))
        assertTrue(converterException.cause is IllegalStateException)

        val resultSet2 = FakeResultSet(
            listOf(FakeColumn("value", Types.VARCHAR, "java.lang.String")),
            listOf(listOf("x")),
        ).asResultSet()
        val mapperException = assertFailsWith<JdbcReadException> {
            reader().read(resultSet2) { throw IllegalArgumentException("row mapper broken") }
        }
        assertTrue(mapperException.cause is IllegalArgumentException)

        val marker = JdbcReadException("marker")
        val resultSet3 = FakeResultSet(
            listOf(FakeColumn("value", Types.VARCHAR, "java.lang.String")),
            listOf(listOf("x")),
        ).asResultSet()
        val propagated = assertFailsWith<JdbcReadException> {
            reader().read(resultSet3) { throw marker }
        }
        assertSame(marker, propagated)
    }

    @Test
    fun readsBeansWithDocumentMapper() {
        val mapper = TestDocumentMapper()
        val resultSet = FakeResultSet(
            listOf(
                FakeColumn("id", Types.INTEGER, "java.lang.Integer"),
                FakeColumn("name", Types.VARCHAR, "java.lang.String"),
                FakeColumn("active", Types.BOOLEAN, "java.lang.Boolean"),
                FakeColumn("score", Types.DECIMAL, "java.math.BigDecimal"),
            ),
            listOf(
                listOf(7, "bob", true, BigDecimal("3.5")),
                listOf(8, "carol", null, null),
            ),
        ).asResultSet()

        val users = reader().read(resultSet, mapper, UserBean::class.java)

        assertEquals(2, users.size)
        assertEquals(7, users[0].id)
        assertEquals("bob", users[0].name)
        assertEquals(true, users[0].active)
        assertEquals(0, BigDecimal("3.5").compareTo(assertNotNull(users[0].score)))
        assertEquals(8, users[1].id)
        assertEquals("carol", users[1].name)
    }

    @Test
    fun readsBeansWithTypeReference() {
        val resultSet = FakeResultSet(
            listOf(FakeColumn("id", Types.INTEGER, "java.lang.Integer")),
            listOf(listOf(11)),
        ).asResultSet()

        val user = reader().mapRow(
            resultSet.apply { next() },
            TestDocumentMapper(),
            DocumentTypeReference.of(UserBean::class.java),
        )

        assertEquals(11, user.id)
    }

    @Test
    fun readsBeansWithDocumentTypeReference() {
        val resultSet = FakeResultSet(
            listOf(
                FakeColumn("id", Types.INTEGER, "java.lang.Integer"),
                FakeColumn("name", Types.VARCHAR, "java.lang.String"),
            ),
            listOf(listOf(1, "alice"), listOf(2, "bob")),
        ).asResultSet()

        val users = reader().read(
            resultSet,
            TestDocumentMapper(),
            DocumentTypeReference.of(UserBean::class.java),
        )

        assertEquals(2, users.size)
        assertEquals("alice", users[0].name)
        assertEquals("bob", users[1].name)
    }

    @Test
    fun readsBeansWithSnakeCaseColumns() {
        val resultSet = FakeResultSet(
            listOf(
                FakeColumn("USER_NAME", Types.VARCHAR, "java.lang.String"),
                FakeColumn("LOGIN_COUNT", Types.INTEGER, "java.lang.Integer"),
            ),
            listOf(listOf("dave", 5)),
        ).asResultSet()
        val config = JdbcConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .build()

        val rows = reader(config).read(resultSet, TestDocumentMapper(), AccountBean::class.java)

        assertEquals("dave", rows[0].userName)
        assertEquals(5, rows[0].loginCount)
    }

    @Test
    fun mapsRowsToJavaMaps() {
        val resultSet = FakeResultSet(
            listOf(
                FakeColumn("id", Types.INTEGER, "java.lang.Integer"),
                FakeColumn("name", Types.VARCHAR, "java.lang.String"),
            ),
            listOf(listOf(1, "alice")),
        ).asResultSet()

        val rows = reader().read(resultSet, DocumentRowMapper.maps())

        assertEquals(1, rows.size)
        assertEquals(0, BigDecimal.ONE.compareTo(rows[0]["id"] as BigDecimal))
        assertEquals("alice", rows[0]["name"])
    }

    @Test
    fun readsCurrentRowWithReadRowAndMapRow() {
        val resultSet = FakeResultSet(
            listOf(FakeColumn("id", Types.INTEGER, "java.lang.Integer")),
            listOf(listOf(1), listOf(2)),
        ).asResultSet()
        assertTrue(resultSet.next())

        val row = reader().readRow(resultSet)
        assertEquals(1, assertNotNull(row["id"]).asNumber().toInt())

        val mapped = reader().mapRow(resultSet) { current -> current["id"]!!.asNumber().toInt() }
        assertEquals(1, mapped)
    }

    @Test
    fun readsEmptyResultSet() {
        val resultSet = FakeResultSet(
            listOf(FakeColumn("id", Types.INTEGER, "java.lang.Integer")),
            emptyList(),
        ).asResultSet()

        assertTrue(reader().readNodes(resultSet).isEmpty())
    }
}
