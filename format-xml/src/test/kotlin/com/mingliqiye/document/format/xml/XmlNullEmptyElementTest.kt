package com.mingliqiye.document.format.xml

import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XmlNullEmptyElementTest {

    @Test
    fun emptyElementDefaultsToEmptyString() {
        val node = XmlReader("<root><age/></root>").read().asObject()

        assertEquals("", node["age"]?.asString())
    }

    @Test
    fun emptyElementCanBeReadAsNull() {
        val mapper = XmlMapper(
            XmlConfig.builder().enable(XmlReadFeature.EMPTY_ELEMENT_AS_NULL).build(),
        )
        val node = mapper.parse("<root><age/></root>", com.mingliqiye.document.core.Node::class.java).asObject()

        assertTrue(node["age"] is NullNode)
    }

    @Test
    fun xsiNilIsProcessedByDefault() {
        val mapper = XmlMapper()
        val node = mapper.parse(
            "<root xmlns:xsi=\"${XmlConfig.XSI_NAMESPACE}\"><age xsi:nil=\"true\"/></root>",
            com.mingliqiye.document.core.Node::class.java,
        ).asObject()

        assertTrue(node["age"] is NullNode)
    }

    @Test
    fun xsiNilCanBeIgnored() {
        val mapper = XmlMapper(
            XmlConfig.builder().disable(XmlReadFeature.PROCESS_XSI_NIL).build(),
        )
        val node = mapper.parse(
            "<root xmlns:xsi=\"${XmlConfig.XSI_NAMESPACE}\"><age xsi:nil=\"true\"/></root>",
            com.mingliqiye.document.core.Node::class.java,
        ).asObject()

        assertEquals("", node["age"]?.asString())
    }

    @Test
    fun nullDefaultsToXsiNil() {
        val xml = ObjectNode.of("age" to NullNode.INSTANCE).toXmlString()

        assertTrue(xml.contains("xmlns:xsi=\"${XmlConfig.XSI_NAMESPACE}\""))
        assertTrue(xml.contains("<age xsi:nil=\"true\"/>"))
    }

    @Test
    fun xsiNilOutputCanBeDisabled() {
        val xml = ObjectNode.of("age" to NullNode.INSTANCE).toXmlString(writeNullsAsXsiNil = false)

        assertFalse(xml.contains("xsi:nil"))
        assertTrue(xml.contains("<age/>"))
    }

    @Test
    fun disabledNilCanRoundTripWithEmptyElementAsNull() {
        val mapper = XmlMapper(
            XmlConfig.builder()
                .disable(XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL)
                .enable(XmlReadFeature.EMPTY_ELEMENT_AS_NULL)
                .build(),
        )
        val restored = mapper.parse(
            mapper.format(ObjectNode.of("age" to NullNode.INSTANCE)),
            com.mingliqiye.document.core.Node::class.java,
        ).asObject()

        assertTrue(restored["age"] is NullNode)
    }
}
