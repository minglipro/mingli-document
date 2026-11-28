package com.mingliqiye.document.format.properties

import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import com.mingliqiye.document.serialization.DocumentTokenType
import com.mingliqiye.document.serialization.NodeTokenReader
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [PropertiesTokenReader] 与 Mapper 原生 Token 路径测试。
 */
class PropertiesTokenReaderTest {

    @Test
    fun nestedObjectAndArrayTokens() {
        val source = "user.age=18\nuser.name=小于\ntags[0]=a\ntags[1]=b"

        val tokens = readTokens(PropertiesTokenReader(source))

        assertEquals(
            listOf(
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.STRING,
                DocumentTokenType.STRING,
                DocumentTokenType.END_ARRAY,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.NUMBER,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.STRING,
                DocumentTokenType.END_OBJECT,
                DocumentTokenType.END_OBJECT,
            ),
            tokens.map { it.type },
        )
        assertEquals(listOf("tags", "a", "b", "user", "age", "18", "name", "小于"), tokens.mapNotNull { it.text })
    }

    @Test
    fun scalarTypesAreInferred() {
        val source = "active=true\ncount=42\nname=小于\nnotNumber=NaN\nratio=3.5\nscientific=1e3"

        val tokens = readTokens(PropertiesTokenReader(source))

        assertEquals(14, tokens.size)
        assertEquals(DocumentTokenType.BOOLEAN, tokens[2].type)
        assertEquals(true, tokens[2].booleanValue)
        assertEquals(DocumentTokenType.NUMBER, tokens[4].type)
        assertEquals("42", tokens[4].text)
        assertEquals(DocumentTokenType.STRING, tokens[6].type)
        assertEquals("小于", tokens[6].text)
        assertEquals(DocumentTokenType.STRING, tokens[8].type)
        assertEquals("NaN", tokens[8].text)
        assertEquals(DocumentTokenType.NUMBER, tokens[10].type)
        assertEquals("3.5", tokens[10].text)
        assertEquals(DocumentTokenType.NUMBER, tokens[12].type)
        assertEquals("1e3", tokens[12].text)
    }

    @Test
    fun emptyInputBecomesEmptyObject() {
        val reader = PropertiesTokenReader("")

        assertNull(reader.currentToken)
        assertEquals(DocumentTokenType.START_OBJECT, reader.nextToken())
        assertEquals(DocumentTokenType.END_OBJECT, reader.nextToken())
        assertNull(reader.nextToken())
        assertNull(reader.currentToken)
        assertNull(reader.textValue)
    }

    @Test
    fun percentEscapedFieldNamesAreDecoded() {
        val source = "list%5B0%5D=x\nuser%2Ename=小于"

        val tokens = readTokens(PropertiesTokenReader(source))

        assertEquals(
            listOf(
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.STRING,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.STRING,
                DocumentTokenType.END_OBJECT,
            ),
            tokens.map { it.type },
        )
        assertEquals(listOf("list[0]", "x", "user.name", "小于"), tokens.mapNotNull { it.text })
    }

    @Test
    fun rootArrayTokens() {
        val tokens = readTokens(PropertiesTokenReader("[0]=a\n[1]=b"))

        assertEquals(
            listOf(
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.STRING,
                DocumentTokenType.STRING,
                DocumentTokenType.END_ARRAY,
            ),
            tokens.map { it.type },
        )
        assertEquals(listOf("a", "b"), tokens.mapNotNull { it.text })
    }

    @Test
    fun arraysAreDenseByIndexOrder() {
        // 上游实现 语义：下标只用于排序，不补空洞；tags[2] 是唯一元素。
        val tokens = readTokens(PropertiesTokenReader("tags[2]=c"))

        assertEquals(
            listOf(
                DocumentTokenType.START_OBJECT,
                DocumentTokenType.FIELD_NAME,
                DocumentTokenType.START_ARRAY,
                DocumentTokenType.STRING,
                DocumentTokenType.END_ARRAY,
                DocumentTokenType.END_OBJECT,
            ),
            tokens.map { it.type },
        )
        assertEquals(listOf("tags", "c"), tokens.mapNotNull { it.text })
    }

    @Test
    fun tokenSequenceMatchesNodeTokenReader() {
        val samples = listOf(
            "",
            "name=小于\nage=18\nactive=true",
            "user.age=18\nuser.name=小于\ntags[0]=a\ntags[1]=b",
            "[0]=a\n[1]=b",
            "tags[2]=c",
            "[2]=c",
            "a.b[2]=x",
            "a.b.c=1\na.b.d=2\na.e[0]=x",
            "list%5B0%5D=x\nuser%2Ename=小于",
            "p.1.x=1\np.2.x=2",
            "str=first\nstr.11=third\nstr.2=second",
            "a=1\na.b=2",
        )

        for (source in samples) {
            val expected = readTokens(NodeTokenReader(PropertiesReader(source).read()))
            val actual = readTokens(PropertiesTokenReader(source))
            assertEquals(expected, actual, "Token 序列与 Node 路径不一致：$source")
        }
    }

    @Test
    fun invalidPropertiesTextThrowsSameParseExceptionAsNodeReader() {
        val source = "a=\\uZZZZ"
        val nativeError = assertFailsWith<com.mingliqiye.document.format.properties.exception.PropertiesParseException> {
            PropertiesTokenReader(source)
        }
        val nodeError = assertFailsWith<com.mingliqiye.document.format.properties.exception.PropertiesParseException> {
            PropertiesReader(source).read()
        }

        assertEquals(nodeError.message, nativeError.message)
    }

    @Test
    fun mapperParsesBeanThroughNativeReader() {
        val mapper = ProbePropertiesMapper()
        val text = "name=小于\nage=18\ntags[0]=a\ntags[1]=b\naddress.city=杭州"

        val user = mapper.parse(text, User::class.java)

        assertTrue(mapper.readerUsed is PropertiesTokenReader)
        assertEquals("小于", user.name)
        assertEquals(18, user.age)
        assertEquals(listOf("a", "b"), user.tags)
        assertEquals("杭州", user.address?.city)
    }

    @Test
    fun mapperParsesMapThroughNativeReader() {
        val mapper = PropertiesMapper()

        val map = mapper.parse("name=小于\nage=18\nnested.flag=true", Map::class.java)

        assertEquals("小于", (map["name"] as StringNode).value)
        assertEquals(18, (map["age"] as NumberNode).toInt())
        val nested = map["nested"] as ObjectNode
        assertEquals(true, (nested["flag"] as BooleanNode).value)
    }

    @Test
    fun mapperUsesCustomPropertiesConfig() {
        val config = PropertiesConfig.builder()
            .pathSeparator("->")
            .writeIndexUsingMarkers(true)
            .build()
        val mapper = PropertiesMapper(config)

        val user = mapper.parse(
            "name=小于\nage=18\ntags[1]=a\ntags[2]=b",
            User::class.java,
        )

        assertEquals("小于", user.name)
        assertEquals(listOf("a", "b"), user.tags)
        val text = mapper.format(user)
        assertTrue(text.contains("tags[1]=a"), text)
    }

    @Test
    fun nodeLimitsAreEnforcedOnTokenPath() {
        val depthMapper = PropertiesMapper(DocumentMapperConfig.builder().maxNodeDepth(2).build())
        val depthError = assertFailsWith<DocumentSerializationException> {
            depthMapper.parse("a.b.c=1", Map::class.java)
        }
        assertTrue(depthError.message.orEmpty().contains("maxNodeDepth 2"))

        val stringMapper = PropertiesMapper(DocumentMapperConfig.builder().maxStringLength(3).build())
        assertFailsWith<DocumentSerializationException> {
            stringMapper.parse("name=abcd", Map::class.java)
        }
        assertFailsWith<DocumentSerializationException> {
            stringMapper.parse("abcd=1", Map::class.java)
        }
        stringMapper.parse("a=abc", Map::class.java)

        val countMapper = PropertiesMapper(DocumentMapperConfig.builder().maxNodeCount(2).build())
        assertFailsWith<DocumentSerializationException> {
            countMapper.parse("a=1\nb=2", Map::class.java)
        }
        countMapper.parse("a=1", Map::class.java)
    }

    private fun readTokens(reader: DocumentTokenReader): List<TokenSnapshot> {
        val tokens = ArrayList<TokenSnapshot>()
        while (true) {
            val type = reader.nextToken() ?: break
            tokens.add(TokenSnapshot(type, reader.textValue, reader.booleanValue))
        }
        return tokens
    }

    private class ProbePropertiesMapper : PropertiesMapper() {
        var readerUsed: DocumentTokenReader? = null

        override fun createTextTokenReader(source: String): DocumentTokenReader = super.createTextTokenReader(source).also { readerUsed = it }
    }

    private data class TokenSnapshot(
        val type: DocumentTokenType,
        val text: String?,
        val booleanValue: Boolean?,
    )

    private class User {
        var name: String? = null
        var age: Int = 0
        var tags: List<String> = emptyList()
        var address: Address? = null
    }

    private class Address {
        var city: String? = null
    }
}
