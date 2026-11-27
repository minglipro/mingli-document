package com.mingliqiye.document.format.xml

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.xml.exception.XmlParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * XML 读写测试。
 */
class XmlCodecTest {

    @Test
    fun documentTreeRoundTrip() {
        val node = ObjectNode.from(
            linkedMapOf(
                "name" to StringNode.of("小于 & <xml>"),
                "age" to NumberNode.of(18),
                "active" to BooleanNode.of(true),
                "missing" to NullNode.INSTANCE,
                "tags" to ArrayNode.from(listOf(StringNode.of("a"), StringNode.of("b"))),
                "address" to ObjectNode.from(mapOf("city" to StringNode.of("杭州"))),
            ),
        )

        val xml = node.toXmlString(pretty = true)
        assertTrue(xml.startsWith("<root type=\"object\">"))
        assertTrue(xml.contains("&amp;"))
        assertTrue(xml.contains("&lt;xml&gt;"))

        val restored = XmlReader(xml).read()
        assertEquals("小于 & <xml>", restored.asObject()["name"]?.asString())
        assertEquals(18, restored.asObject()["age"]?.asNumber()?.toInt())
        assertEquals(true, restored.asObject()["active"]?.asBoolean())
        assertEquals("a", restored.asObject()["tags"]?.asArray()?.get(0)?.asString())
        assertEquals("杭州", restored.asObject()["address"]?.asObject()?.get("city")?.asString())
    }

    @Test
    fun emptyContainersAndScalars() {
        val emptyObject = ObjectNode.from(emptyMap())
        assertEquals(0, XmlReader(emptyObject.toXmlString()).read().asObject().size)

        val emptyArray = ArrayNode.from(emptyList())
        assertEquals(0, XmlReader(emptyArray.toXmlString()).read().asArray().size)

        assertEquals("", XmlReader(StringNode.of("").toXmlString()).read().asString())
        assertEquals(42, XmlReader(NumberNode.of(42).toXmlString()).read().asNumber().toInt())
    }

    @Test
    fun invalidXmlThrowsParseException() {
        assertFailsWith<XmlParseException> {
            XmlReader("<root><a></root>").read()
        }
    }

    @Test
    fun namesWithInvalidXmlCharactersRoundTrip() {
        val node = ObjectNode.from(mapOf("user name" to StringNode.of("value")))
        val xml = node.toXmlString()
        val restored = XmlReader(xml).read()

        assertEquals("value", restored.asObject()["user name"]?.asString())
    }
}
