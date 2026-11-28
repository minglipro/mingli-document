package com.mingliqiye.document.format.cbor

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.cbor.exception.CborParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentTokenReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * CBOR tag 保真测试。
 */
class CborTagTest {

    @Test
    fun preserveTagsIsDisabledByDefault() {
        assertEquals(StringNode.of("abc"), CborReader(hex("d86463616263")).read())
        assertEquals(ObjectNode.of("a" to NumberNode.of(1)), CborReader(hex("d864a1616101")).read())
        assertEquals(NumberNode.of(5), CborReader(hex("d86405")).read())
    }

    @Test
    fun readsAndWritesTaggedString() {
        // tag(100) "abc"
        val bytes = hex("d86463616263")
        val node = CborReader(bytes, preserveTags = true).read()

        assertEquals(wrapper(100, StringNode.of("abc")), node)
        assertEquals("d86463616263", node.toCborBytes(preserveTags = true).toHex())
        assertEquals(node, CborReader(node.toCborBytes(preserveTags = true), preserveTags = true).read())

        // 不识别 tag 时包装对象会被当作普通对象写出/读回。
        assertEquals(node, CborReader(node.toCborBytes()).read())
    }

    @Test
    fun readsAndWritesTag1000() {
        // tag(1000) "abc"，tag 1000 使用两字节 tag 编码。
        val bytes = hex("d903e863616263")
        val node = CborReader(bytes, preserveTags = true).read()

        assertEquals(wrapper(1000, StringNode.of("abc")), node)
        assertEquals("d903e863616263", node.toCborBytes(preserveTags = true).toHex())
    }

    @Test
    fun preservesTagOnObject() {
        // tag(100) {"a":1}
        val bytes = hex("d864a1616101")
        val node = CborReader(bytes, preserveTags = true).read()

        assertEquals(wrapper(100, ObjectNode.of("a" to NumberNode.of(1))), node)
        assertEquals("d864a1616101", node.toCborBytes(preserveTags = true).toHex())
        assertEquals(node, CborReader("d864a1616101".hexToBytes(), preserveTags = true).read())
    }

    @Test
    fun preservesTagOnArrayAndScalars() {
        val cases = listOf(
            // tag(100) [1,2]
            "d864820102" to wrapper(100, ArrayNode.of(NumberNode.of(1), NumberNode.of(2))),
            // tag(100) 5
            "d86405" to wrapper(100, NumberNode.of(5)),
            // tag(100) true
            "d864f5" to wrapper(100, BooleanNode.of(true)),
            // tag(100) null
            "d864f6" to wrapper(100, NullNode.INSTANCE),
            // tag(100) h'010203'
            "d86443010203" to wrapper(100, BinaryNode.of(byteArrayOf(1, 2, 3))),
        )
        for ((encoded, expected) in cases) {
            val node = CborReader(hex(encoded), preserveTags = true).read()
            assertEquals(expected, node, "read $encoded")
            assertEquals(encoded, node.toCborBytes(preserveTags = true).toHex(), "write $encoded")
        }
    }

    @Test
    fun preservesNestedTags() {
        // {"a": tag(100) "b", "c": tag(1000) {"d": 1}}
        val expectedBytes = "a26161d86461626163d903e8a1616401"
        val expected = ObjectNode.of(
            "a" to wrapper(100, StringNode.of("b")),
            "c" to wrapper(1000, ObjectNode.of("d" to NumberNode.of(1))),
        )

        assertEquals(expectedBytes, expected.toCborBytes(preserveTags = true).toHex())
        assertEquals(expected, CborReader(hex(expectedBytes), preserveTags = true).read())

        // tag(100) {"x": tag(1000) "y"}
        val taggedContainer = "d864a16178d903e86179"
        val expectedTaggedContainer = wrapper(
            100,
            ObjectNode.of("x" to wrapper(1000, StringNode.of("y"))),
        )
        assertEquals(taggedContainer, expectedTaggedContainer.toCborBytes(preserveTags = true).toHex())
        assertEquals(expectedTaggedContainer, CborReader(hex(taggedContainer), preserveTags = true).read())
    }

    @Test
    fun untaggedDocumentBehaviorIsUnchanged() {
        val node = ObjectNode.of(
            "a" to NumberNode.of(1),
            "b" to ArrayNode.of(StringNode.of("x"), BooleanNode.of(true)),
        )
        val expectedBytes = "a26161016162826178f5"

        assertEquals(expectedBytes, node.toCborBytes(preserveTags = true).toHex())
        assertEquals(node, CborReader(hex(expectedBytes), preserveTags = true).read())
        assertEquals(node, CborReader(hex(expectedBytes)).read())
        assertEquals(
            node.toCborBytes().toHex(),
            node.toCborBytes(preserveTags = true).toHex(),
        )

        // 保真模式对无 tag 文档的类型映射与默认模式一致。
        val mixed = ObjectNode.of(
            "i" to NumberNode.of(1),
            "f" to NumberNode.of(1.5),
            "b" to BinaryNode.of(byteArrayOf(1, 2, 3)),
            "l" to ArrayNode.of(NullNode.INSTANCE, BooleanNode.of(true)),
            "1" to StringNode.of("int-key"),
        )
        assertEquals(mixed, CborReader(mixed.toCborBytes(), preserveTags = true).read())
        assertEquals(
            ObjectNode.of("1" to StringNode.of("a")),
            CborReader(hex("a1016161"), preserveTags = true).read(),
        )
        assertEquals(
            NumberNode.of(1.5),
            CborReader(hex("fb3ff8000000000000"), preserveTags = true).read(),
        )
    }

    @Test
    fun preserveTagsFalseStillIgnoresInputTags() {
        // tag(100) {"name":"x"}
        val tagged = "d864a1646e616d656178"

        assertEquals(
            ObjectNode.of("name" to StringNode.of("x")),
            CborReader(hex(tagged), preserveTags = false).read(),
        )

        // 默认 Mapper 的 Token 快路径同样忽略 tag，可直接映射 Bean。
        val mapper = CborMapper()
        assertEquals("x", mapper.parse(hex(tagged), TagBean::class.java).name)
        assertEquals("x", mapper.parse(hex(tagged), Node::class.java).asObject()["name"]?.asString())
    }

    @Test
    fun invalidWrapperIsTreatedAsPlainObject() {
        val invalidWrappers = listOf(
            // tag 非数字
            ObjectNode.of(
                CborConfig.TAG_FIELD to StringNode.of("100"),
                CborConfig.VALUE_FIELD to NumberNode.of(1),
            ),
            // value 缺失
            ObjectNode.of(CborConfig.TAG_FIELD to NumberNode.of(100)),
            // 多于两个字段
            ObjectNode.of(
                CborConfig.TAG_FIELD to NumberNode.of(100),
                CborConfig.VALUE_FIELD to NumberNode.of(1),
                "extra" to NumberNode.of(2),
            ),
            // 负数 tag
            ObjectNode.of(
                CborConfig.TAG_FIELD to NumberNode.of(-1),
                CborConfig.VALUE_FIELD to NumberNode.of(1),
            ),
            // 超出 Int 范围的 tag
            ObjectNode.of(
                CborConfig.TAG_FIELD to NumberNode.ofRaw("99999999999999999999"),
                CborConfig.VALUE_FIELD to NumberNode.of(1),
            ),
            // 小数 tag
            ObjectNode.of(
                CborConfig.TAG_FIELD to NumberNode.ofRaw("1.0"),
                CborConfig.VALUE_FIELD to NumberNode.of(1),
            ),
        )
        for (node in invalidWrappers) {
            assertEquals(
                node.toCborBytes().toHex(),
                node.toCborBytes(preserveTags = true).toHex(),
                "plain object bytes for $node",
            )
            assertEquals(node, CborReader(node.toCborBytes(preserveTags = true), preserveTags = true).read())
        }
    }

    @Test
    fun specialTagsAreNormalizedByJacksonDecoding() {
        // tag 2 bignum 2^64：上游实现 已解码为整数，保真模式下不单独包装；
        // 写出时整数策略会重新编码为等价 bignum（tag 2）。
        val bignumBytes = "c249010000000000000000"
        val bignum = CborReader(hex(bignumBytes), preserveTags = true).read()
        assertEquals(NumberNode.ofRaw("18446744073709551616"), bignum)
        assertEquals(bignumBytes, bignum.toCborBytes(preserveTags = true).toHex())

        // tag 4 十进制分数：上游实现 已解码为小数；写出时重新编码为 tag 4，字节一致。
        val decimalBytes = "c4822119013a"
        val decimal = CborReader(hex(decimalBytes), preserveTags = true).read()
        assertEquals(NumberNode.ofRaw("3.14"), decimal)
        assertEquals(decimalBytes, decimal.toCborBytes(preserveTags = true).toHex())

        // 小 bignum（tag 2 + 1 字节）会被规范化为普通整数：值不变，tag 丢失。
        val smallBignum = CborReader(hex("c24101"), preserveTags = true).read()
        assertEquals(NumberNode.ofRaw("1"), smallBignum)
        assertEquals("01", smallBignum.toCborBytes(preserveTags = true).toHex())
    }

    @Test
    fun mapperPreserveModeDisablesTokenFastPathAndKeepsTags() {
        val preserveMapper = PreserveRecordingMapper()
        // tag(100) {"name":"x"}
        val tagged = "d864a1646e616d656178"

        val node = preserveMapper.parse(hex(tagged), Node::class.java)
        assertEquals(0, preserveMapper.tokenReaderCalls)
        assertEquals(
            wrapper(100, ObjectNode.of("name" to StringNode.of("x"))),
            node,
        )
        assertEquals(tagged, preserveMapper.formatBytes(node).toHex())

        // 保真模式 formatBytes 走 Node 写出（定长 map），默认模式走 Token 直写（不定长 map）。
        val bean = TagBean().apply { name = "x" }
        assertEquals("a1646e616d656178", preserveMapper.formatBytes(bean).toHex())
        assertEquals(0xBF, CborMapper().formatBytes(bean).first().toInt() and 0xFF)

        // 保真模式下 Bean 解析仍可用（无 tag 文档走 Node fallback）。
        assertEquals("x", preserveMapper.parse(hex("a1646e616d656178"), TagBean::class.java).name)
        assertEquals(0, preserveMapper.tokenReaderCalls)
    }

    @Test
    fun defaultMapperStillUsesTokenFastPath() {
        val mapper = DefaultRecordingMapper()
        // tag(100) {"name":"x"}：Token 快路径忽略 tag 后直接映射 Bean。
        val parsed = mapper.parse(hex("d864a1646e616d656178"), TagBean::class.java)

        assertEquals(1, mapper.tokenReaderCalls)
        assertEquals("x", parsed.name)
    }

    @Test
    fun preserveModeKeepsCanonicalTagWriting() {
        val mapper = CborMapper(CborConfig.builder().preserveTags(true).build())
        // tag(100) {"b":2,"a":1}，canonical 只对包装对象内部的 map key 排序。
        val node = wrapper(
            100,
            ObjectNode.of("b" to NumberNode.of(2), "a" to NumberNode.of(1)),
        )
        val bytes = mapper.formatCanonicalBytes(node)
        val expected = wrapper(100, ObjectNode.of("a" to NumberNode.of(1), "b" to NumberNode.of(2)))

        assertEquals("d864a2616101616202", bytes.toHex())
        assertEquals(expected, CborReader(bytes, preserveTags = true).read())
    }

    @Test
    fun preserveModeHonorsMaxDepth() {
        val bytes = ArrayNode.of(ArrayNode.of(StringNode.of("x"))).toCborBytes()

        assertFailsWith<CborParseException> {
            CborReader(bytes, maxDepth = 1, preserveTags = true).read()
        }
        assertEquals(
            ArrayNode.of(ArrayNode.of(StringNode.of("x"))),
            CborReader(bytes, maxDepth = 2, preserveTags = true).read(),
        )
    }

    @Test
    fun preserveModeRejectsInvalidCbor() {
        val invalidValues = listOf("ff", "8201", "6261")
        for (encoded in invalidValues) {
            assertFailsWith<CborParseException> {
                CborReader(hex(encoded), preserveTags = true).read()
            }
        }
        assertFailsWith<CborParseException> {
            CborReader(ByteArray(0), preserveTags = true).read()
        }
        assertFailsWith<CborParseException> {
            CborReader(hex("0102"), preserveTags = true).read()
        }
    }

    @Test
    fun configBuilderAndConstructorAreAvailable() {
        val config = CborConfig.builder().preserveTags(true).build()
        assertTrue(config.preserveTags)

        val defaultConfig = CborConfig.builder().build()
        assertFalse(defaultConfig.preserveTags)

        // CborConfig 构造器与 builder 均可开启保真模式。
        val taggedString = wrapper(100, StringNode.of("abc")).toCborBytes(preserveTags = true)
        assertEquals("d86463616263", taggedString.toHex())
        assertEquals(
            "d86463616263",
            CborMapper(config).formatBytes(wrapper(100, StringNode.of("abc"))).toHex(),
        )
        assertEquals(
            "d86463616263",
            CborMapper.builder().config(config).build()
                .formatBytes(wrapper(100, StringNode.of("abc"))).toHex(),
        )

        // 与通用配置组合。
        val composed = CborConfig.builder()
            .base(DocumentMapperConfig.empty())
            .preserveTags(true)
            .build()
        assertTrue(composed.preserveTags)
        // 默认配置的 Mapper 仍忽略 tag。
        assertEquals("abc", CborMapper().parse(hex("d86463616263"), Node::class.java).asString())
    }

    @Test
    fun extensionsSupportPreserveTags() {
        val node = hex("d86463616263").toCborNode(preserveTags = true)
        assertEquals(wrapper(100, StringNode.of("abc")), node)
        assertEquals("d86463616263", node.toCborBytes(preserveTags = true).toHex())
        assertEquals(100, node.asObject()[CborConfig.TAG_FIELD]?.asNumber()?.toInt())
        assertEquals("abc", node.asObject()[CborConfig.VALUE_FIELD]?.asString())
    }

    private fun wrapper(tag: Int, value: Node): ObjectNode = ObjectNode.of(
        CborConfig.TAG_FIELD to NumberNode.of(tag),
        CborConfig.VALUE_FIELD to value,
    )

    private fun hex(value: String): ByteArray {
        val normalized = value.replace(" ", "")
        check(normalized.length % 2 == 0) { "Hex string must have an even length" }
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }

    private fun String.hexToBytes(): ByteArray = hex(this)

    private class TagBean {
        var name: String? = null
    }

    private class DefaultRecordingMapper : CborMapper() {
        var tokenReaderCalls: Int = 0
            private set

        override fun createTokenReader(source: ByteArray): DocumentTokenReader? {
            val reader = super.createTokenReader(source)
            if (reader != null) {
                tokenReaderCalls++
            }
            return reader
        }
    }

    private class PreserveRecordingMapper : CborMapper(CborConfig.builder().preserveTags(true).build()) {
        var tokenReaderCalls: Int = 0
            private set

        override fun createTokenReader(source: ByteArray): DocumentTokenReader? {
            val reader = super.createTokenReader(source)
            if (reader != null) {
                tokenReaderCalls++
            }
            return reader
        }
    }
}
