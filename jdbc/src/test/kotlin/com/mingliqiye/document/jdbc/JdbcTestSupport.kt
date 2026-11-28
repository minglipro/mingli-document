package com.mingliqiye.document.jdbc

import com.mingliqiye.document.serialization.DocumentMapper
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.math.BigDecimal
import java.sql.Blob
import java.sql.Clob
import java.sql.ParameterMetaData
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLFeatureNotSupportedException

/** 测试用文档映射器：只支持 Node/Bean 转换，不支持文本解析。 */
internal class TestDocumentMapper :
    DocumentMapper(
        "test",
        { error("test mapper 不支持文本解析") },
        { node, _ -> node.toString() },
    )

/** 测试用 Bean。 */
internal class UserBean {
    var id: Int = 0
    var name: String? = null
    var active: Boolean? = null
    var score: BigDecimal? = null

    override fun toString(): String = "UserBean(id=$id, name=$name, active=$active, score=$score)"
}

/** 测试用列名与字段名不一致的 Bean。 */
internal class AccountBean {
    var userName: String? = null
    var loginCount: Int = 0

    override fun toString(): String = "AccountBean(userName=$userName, loginCount=$loginCount)"
}

/** 测试用结果集列描述。 */
internal class FakeColumn(
    val label: String,
    val sqlType: Int,
    val columnClassName: String? = null,
    val columnName: String? = null,
)

/**
 * 用 `java.lang.reflect.Proxy` 模拟的 ResultSet；测试环境不需要真实数据库。
 */
internal class FakeResultSet(
    private val columns: List<FakeColumn>,
    private val rows: List<List<Any?>>,
) {

    var nextFailure: java.sql.SQLException? = null
    var objectFailure: java.sql.SQLException? = null
    var metadataFailure: java.sql.SQLException? = null

    private var cursor = -1

    fun asResultSet(): ResultSet = jdbcProxy(arrayOf(ResultSet::class.java)) { method, arguments ->
        when (method.name) {
            "next" -> {
                nextFailure?.let { throw it }
                cursor++
                cursor < rows.size
            }

            "getMetaData" -> metadata()
            "getObject" -> {
                objectFailure?.let { throw it }
                rows[cursor][(arguments[0] as Int) - 1]
            }

            "getBytes" -> when (val value = rows[cursor][(arguments[0] as Int) - 1]) {
                is ByteArray -> value
                is Blob -> blobBytes(value)
                else -> null
            }

            "getString" -> when (val value = rows[cursor][(arguments[0] as Int) - 1]) {
                is Clob -> clobText(value)
                is java.sql.SQLXML -> value.getString()
                else -> value?.toString()
            }

            "wasNull" -> false
            "isClosed" -> false
            else -> defaultValue(method.returnType)
        }
    }

    private fun metadata(): ResultSetMetaData {
        metadataFailure?.let { throw it }
        return jdbcProxy(arrayOf(ResultSetMetaData::class.java)) { method, arguments ->
            when (method.name) {
                "getColumnCount" -> columns.size
                "getColumnLabel" -> columns[(arguments[0] as Int) - 1].label
                "getColumnName" -> columns[(arguments[0] as Int) - 1].columnName
                    ?: columns[(arguments[0] as Int) - 1].label

                "getColumnType" -> columns[(arguments[0] as Int) - 1].sqlType
                "getColumnClassName" -> columns[(arguments[0] as Int) - 1].columnClassName
                else -> defaultValue(method.returnType)
            }
        }
    }
}

/** 一次参数绑定记录。 */
internal data class BoundValue(
    val index: Int,
    val method: String,
    val value: Any?,
    val sqlType: Int?,
)

/**
 * 用 `java.lang.reflect.Proxy` 模拟的 PreparedStatement，记录所有参数绑定调用。
 *
 * @param parameterTypes 非 null 时模拟驱动支持 ParameterMetaData；null 时
 * `getParameterMetaData` 抛 [SQLFeatureNotSupportedException]
 * @param failAt 指定参数下标（1 起始）绑定失败，模拟 SQLException
 */
internal class FakePreparedStatement(
    private val parameterTypes: List<Int>? = null,
    private val failAt: Int? = null,
) {

    val bound: MutableList<BoundValue> = ArrayList()

    fun boundAt(index: Int): BoundValue? = bound.firstOrNull { it.index == index }

    fun asPreparedStatement(): PreparedStatement = jdbcProxy(arrayOf(PreparedStatement::class.java)) { method, arguments ->
        val index = arguments.firstOrNull() as? Int
        if (failAt != null && index == failAt && method.name.startsWith("set")) {
            throw java.sql.SQLException("模拟绑定失败：参数 $index")
        }
        when (method.name) {
            "setObject" -> {
                bound.add(
                    BoundValue(
                        arguments[0] as Int,
                        "setObject",
                        arguments[1],
                        arguments.getOrNull(2) as? Int,
                    ),
                )
                null
            }

            "setNull" -> {
                bound.add(BoundValue(arguments[0] as Int, "setNull", null, arguments[1] as Int))
                null
            }

            "setBytes" -> {
                bound.add(BoundValue(arguments[0] as Int, "setBytes", arguments[1], null))
                null
            }

            "getParameterMetaData" -> parameterTypes?.let { parameterMetadata(it) }
                ?: throw SQLFeatureNotSupportedException("测试驱动不支持 ParameterMetaData")

            "executeUpdate" -> 1
            "isClosed" -> false
            else -> defaultValue(method.returnType)
        }
    }

    private fun parameterMetadata(types: List<Int>): ParameterMetaData = jdbcProxy(arrayOf(ParameterMetaData::class.java)) { method, arguments ->
        when (method.name) {
            "getParameterCount" -> types.size
            "getParameterType" -> types[(arguments[0] as Int) - 1]
            else -> defaultValue(method.returnType)
        }
    }
}

/** 创建 Proxy 实例并处理 Object 方法。 */
internal fun <T> jdbcProxy(interfaces: Array<Class<*>>, handler: (Method, Array<Any?>) -> Any?): T {
    val invocationHandler = InvocationHandler { proxy, method, arguments ->
        when (method.name) {
            "equals" -> proxy === arguments?.getOrNull(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "JdbcTestProxy"
            else -> handler(method, arguments ?: emptyArray())
        }
    }
    @Suppress("UNCHECKED_CAST")
    return Proxy.newProxyInstance(TestDocumentMapper::class.java.classLoader, interfaces, invocationHandler) as T
}

/** 接口方法未实现时返回的默认值。 */
internal fun defaultValue(type: Class<*>): Any? = when (type) {
    java.lang.Boolean.TYPE -> false
    java.lang.Byte.TYPE -> 0.toByte()
    java.lang.Short.TYPE -> 0.toShort()
    java.lang.Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0f
    java.lang.Double.TYPE -> 0.0
    java.lang.Character.TYPE -> '\u0000'
    else -> null
}

/** 创建模拟 Clob。 */
internal fun clobOf(text: String): Clob = jdbcProxy(arrayOf(Clob::class.java)) { method, arguments ->
    when (method.name) {
        "length" -> text.length.toLong()
        "getSubString" -> {
            val start = (arguments[0] as Long).toInt() - 1
            val length = arguments[1] as Int
            text.substring(start, start + length)
        }

        "getCharacterStream" -> StringReader(text)
        else -> defaultValue(method.returnType)
    }
}

/** 创建模拟 Blob。 */
internal fun blobOf(bytes: ByteArray): Blob = jdbcProxy(arrayOf(Blob::class.java)) { method, arguments ->
    when (method.name) {
        "length" -> bytes.size.toLong()
        "getBytes" -> {
            val start = (arguments[0] as Long).toInt() - 1
            val length = arguments[1] as Int
            bytes.copyOfRange(start, start + length)
        }

        "getBinaryStream" -> ByteArrayInputStream(bytes)
        else -> defaultValue(method.returnType)
    }
}

private fun clobText(clob: Clob): String = clob.characterStream.use { it.readText() }

private fun blobBytes(blob: Blob): ByteArray = blob.binaryStream.use { it.readBytes() }
