package com.mingliqiye.document.format.xml

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.BooleanNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 上游实现 XML 配置对齐测试：读/写特性、Builder 选项、名称处理与公共配置便捷方法。
 */
class XmlJacksonAlignmentTest {

    @Test
    fun modernDefaultsMatchJacksonAndJackson2Switch() {
        val defaults = XmlConfig.builder().build()
        assertEquals(XmlConfig.DEFAULT_READ_FEATURES, defaults.readFeatures)
        assertEquals(XmlConfig.DEFAULT_WRITE_FEATURES, defaults.writeFeatures)
        assertTrue(defaults.autoDetectXsiType)
        assertTrue(defaults.processXsiNil)
        assertFalse(defaults.emptyElementAsNull)
        assertTrue(defaults.writeNullsAsXsiNil)
        assertTrue(defaults.unwrapRootObjectNode)
        assertTrue(defaults.writeAutoDetectXsiType)
        assertTrue(defaults.writeSchemaConformingFloats)
        assertFalse(defaults.writeXmlDeclaration)
        assertFalse(defaults.writeXml11)
        assertFalse(defaults.writeStandaloneYesToXmlDeclaration)

        val jackson2 = XmlConfig.builder().legacy2Defaults().build()
        assertFalse(jackson2.autoDetectXsiType)
        assertFalse(jackson2.writeNullsAsXsiNil)
        assertFalse(jackson2.unwrapRootObjectNode)
        assertFalse(jackson2.writeAutoDetectXsiType)
        assertFalse(jackson2.writeSchemaConformingFloats)
        assertFalse(jackson2.writeXmlDeclaration)

        val restored = XmlConfig.builder().legacy2Defaults().modernDefaults().build()
        assertEquals(XmlConfig.DEFAULT_READ_FEATURES, restored.readFeatures)
        assertEquals(XmlConfig.DEFAULT_WRITE_FEATURES, restored.writeFeatures)
    }

    @Test
    fun xmlDeclarationAndStandaloneFeature() {
        val node = ObjectNode.of("name" to StringNode.of("x"))

        val plain = node.toXmlString()
        assertFalse(plain.startsWith("<?xml"))

        val declared = node.toXmlString(
            writeFeatures = XmlConfig.DEFAULT_WRITE_FEATURES + XmlWriteFeature.WRITE_XML_DECLARATION,
        )
        assertTrue(declared.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(declared.endsWith("<name>x</name>"))

        val standalone = node.toXmlString(
            writeFeatures = XmlConfig.DEFAULT_WRITE_FEATURES + setOf(
                XmlWriteFeature.WRITE_XML_DECLARATION,
                XmlWriteFeature.WRITE_STANDALONE_YES_TO_XML_DECLARATION,
            ),
        )
        assertTrue(standalone.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"))

        val standaloneOnly = node.toXmlString(
            writeFeatures = XmlConfig.DEFAULT_WRITE_FEATURES + XmlWriteFeature.WRITE_STANDALONE_YES_TO_XML_DECLARATION,
        )
        assertFalse(standaloneOnly.contains("<?xml"))
        assertFalse(standaloneOnly.contains("standalone"))
    }

    @Test
    fun xml11DeclarationAndControlCharacters() {
        val node = ObjectNode.of("text" to StringNode.of("a\u0001b"))

        val xml11 = node.toXmlString(
            writeFeatures = XmlConfig.DEFAULT_WRITE_FEATURES + XmlWriteFeature.WRITE_XML_1_1,
        )
        assertTrue(xml11.startsWith("<?xml version=\"1.1\" encoding=\"UTF-8\"?>"))
        assertTrue(xml11.contains("a&#x1;b"))

        val xml10 = node.toXmlString(
            writeFeatures = XmlConfig.DEFAULT_WRITE_FEATURES + XmlWriteFeature.WRITE_XML_DECLARATION,
        )
        assertTrue(xml10.startsWith("<?xml version=\"1.0\""))
        assertTrue(xml10.contains("a\u0001b"))
    }

    @Test
    fun mapperUsesFileCharsetInXmlDeclaration() {
        val mapper = XmlMapper(
            XmlConfig.builder()
                .fileCharset(Charsets.UTF_16)
                .enable(XmlWriteFeature.WRITE_XML_DECLARATION)
                .build(),
        )

        val xml = mapper.format(ObjectNode.of("name" to StringNode.of("x")))
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-16\"?>"))
    }

    @Test
    fun autoDetectXsiTypeWritesOnlyWhenInferenceWouldLoseType() {
        // 看起来像数字的字符串：需要 xs:string 才能保真。
        val numericString = ObjectNode.of("value" to StringNode.of("123"))
        val stringXml = numericString.toXmlString(writeFeatures = ROUND_TRIP_FEATURES)
        assertTrue(stringXml.contains("xmlns:xsi=\"${XmlConfig.XSI_NAMESPACE}\""))
        assertTrue(stringXml.contains("xmlns:xs=\"${XmlConfig.XSD_NAMESPACE}\""))
        assertTrue(stringXml.contains("xsi:type=\"xs:string\""))
        val stringBack = XmlReader(stringXml).read().asObject()["value"]
        assertTrue(stringBack is StringNode)
        assertEquals("123", stringBack.asString())

        // 二进制无法从 Base64 文本推断。
        val binary = ObjectNode.of("data" to BinaryNode.of(byteArrayOf(1, 2, 3)))
        val binaryXml = binary.toXmlString(writeFeatures = ROUND_TRIP_FEATURES)
        assertTrue(binaryXml.contains("xsi:type=\"xs:base64Binary\""))
        val binaryBack = XmlReader(binaryXml).read().asObject()["data"]
        assertTrue(binaryBack is BinaryNode)
        assertTrue(binaryBack.value.contentEquals(byteArrayOf(1, 2, 3)))

        // 普通字符串/数字/布尔不需要 xsi:type。
        val plain = ObjectNode.of(
            "name" to StringNode.of("x"),
            "number" to NumberNode.of(42),
            "flag" to BooleanNode.of(true),
        )
        assertFalse(plain.toXmlString().contains("xsi:type"))
    }

    @Test
    fun autoDetectXsiTypeIsSkippedWithTypeAttributes() {
        val node = ObjectNode.of("value" to StringNode.of("123"))

        val xml = node.toXmlString(typeAttributes = true)
        assertFalse(xml.contains("xsi:type"))
        assertTrue(xml.contains("type=\"string\""))
    }

    @Test
    fun autoDetectXsiTypeCanBeDisabledAndTypeIsThenInferred() {
        val features = XmlConfig.DEFAULT_WRITE_FEATURES - XmlWriteFeature.AUTO_DETECT_XSI_TYPE
        val xml = ObjectNode.of("value" to StringNode.of("123")).toXmlString(writeFeatures = features)
        assertFalse(xml.contains("xsi:type"))

        val typedXml = """<root><value xsi:type="xs:string">123</value></root>"""
        val node = XmlReader(typedXml, autoDetectXsiType = false).read()
        assertTrue(node.asObject()["value"] is NumberNode)
    }

    @Test
    fun readsXsiTypeScalars() {
        val xml = """
            <root xmlns:xsi="${XmlConfig.XSI_NAMESPACE}" xmlns:xs="${XmlConfig.XSD_NAMESPACE}">
              <text xsi:type="xs:string">42</text>
              <count xsi:type="xs:int">42</count>
              <ratio xsi:type="xs:double">INF</ratio>
              <bytes xsi:type="xs:base64Binary">AQID</bytes>
              <hex xsi:type="xs:hexBinary">0102</hex>
              <ok xsi:type="xs:boolean">true</ok>
              <unknown xsi:type="xs:custom">123</unknown>
            </root>
        """.trimIndent()

        val node = XmlReader(xml).read().asObject()

        assertTrue(node["text"] is StringNode)
        assertEquals("42", node["text"]?.asString())
        assertEquals(42, node["count"]?.asNumber()?.toInt())
        assertEquals("INF", node["ratio"]?.asNumber()?.raw)
        assertTrue(node["bytes"] is BinaryNode)
        assertTrue(node["bytes"]?.asBinary()?.contentEquals(byteArrayOf(1, 2, 3)) == true)
        assertTrue(node["hex"]?.asBinary()?.contentEquals(byteArrayOf(1, 2)) == true)
        assertEquals(true, node["ok"]?.asBoolean())
        // 未知 XSD 类型回退结构推断。
        assertEquals(123, node["unknown"]?.asNumber()?.toInt())
    }

    @Test
    fun readsXsiAttributesWithCustomPrefix() {
        // 非命名空间感知解析下通过 xmlns:前缀 声明解析 XSI 前缀。
        val xml = """<root xmlns:i="${XmlConfig.XSI_NAMESPACE}">""" +
            """<nilValue i:nil="true"/><count i:type="i:int">42</count></root>"""
        val node = XmlReader(xml).read().asObject()

        assertTrue(node["nilValue"] is NullNode)
        assertEquals(42, node["count"]?.asNumber()?.toInt())

        // 命名空间感知解析同样支持任意前缀。
        val awareNode = XmlReader(xml, namespaceAware = true, attributePrefix = "@").read().asObject()
        assertTrue(awareNode["nilValue"] is NullNode)
        assertEquals(42, awareNode["count"]?.asNumber()?.toInt())
    }

    @Test
    fun xsiTypeAttributeIsNotMappedAsFieldWhenAutoDetectEnabled() {
        val mapper = XmlMapper(XmlConfig.builder().attributePrefix("@").typeAttributes(true).build())
        val xml = """<root xmlns:xsi="${XmlConfig.XSI_NAMESPACE}" xmlns:xs="${XmlConfig.XSD_NAMESPACE}">""" +
            """<value xsi:type="xs:string">42</value></root>"""
        val node = mapper.parse(xml, Node::class.java).asObject()

        assertEquals("42", node["value"]?.asString())
        assertFalse(node.containsKey("@xsi:type"))
    }

    @Test
    fun autoDetectXsiTypeRoundTripsSpecialFloats() {
        val node = ObjectNode.of("ratio" to NumberNode.ofRaw("INF"))
        val xml = node.toXmlString(writeFeatures = ROUND_TRIP_FEATURES)

        assertTrue(xml.contains(">INF<"))
        assertTrue(xml.contains("xsi:type=\"xs:double\""))
        assertEquals("INF", XmlReader(xml).read().asObject()["ratio"]?.asNumber()?.raw)

        val withoutSchemaFloats = XmlConfig.DEFAULT_WRITE_FEATURES - XmlWriteFeature.WRITE_XML_SCHEMA_CONFORMING_FLOATS
        val rawXml = ObjectNode.of("ratio" to NumberNode.ofRaw("Infinity")).toXmlString(writeFeatures = withoutSchemaFloats)
        assertTrue(rawXml.contains(">Infinity<"))
        assertFalse(rawXml.contains("xsi:type"))
    }

    @Test
    fun schemaConformingFloatFeature() {
        val node = ObjectNode.of(
            "inf" to NumberNode.ofRaw("Infinity"),
            "negativeInf" to NumberNode.ofRaw("-Infinity"),
            "nan" to NumberNode.ofRaw("NaN"),
        )

        val xml = node.toXmlString()
        assertTrue(xml.contains(">INF<"))
        assertTrue(xml.contains(">-INF<"))
        assertTrue(xml.contains(">NaN<"))

        val disabled = XmlConfig.DEFAULT_WRITE_FEATURES - XmlWriteFeature.WRITE_XML_SCHEMA_CONFORMING_FLOATS
        val raw = node.toXmlString(writeFeatures = disabled)
        assertTrue(raw.contains(">Infinity<"))
        assertTrue(raw.contains(">-Infinity<"))
    }

    @Test
    fun unwrapRootObjectNodeDefaultsToOnForTreeModelAndOffForBeans() {
        val node = ObjectNode.of("user" to ObjectNode.of("name" to StringNode.of("x")))

        val xml = XmlMapper(XmlConfig.builder().typeAttributes(true).build()).format(node)
        assertTrue(xml.startsWith("<user type=\"object\">"))

        val wrapped = XmlMapper(
            XmlConfig.builder().typeAttributes(true).disable(XmlWriteFeature.UNWRAP_ROOT_OBJECT_NODE).build(),
        ).format(node)
        assertTrue(wrapped.startsWith("<root type=\"object\"><user type=\"object\">"))

        // Bean 序列化不展开根对象（对齐 上游实现 3.x 的 root ObjectNode 语义）。
        val beanXml = XmlMapper(XmlConfig.builder().typeAttributes(true).build())
            .format(SingleFieldBean().apply { name = "x" })
        assertTrue(beanXml.startsWith("<root type=\"object\">"))

        // 单字段根是属性字段时保持根元素（无法用属性名作元素名）。
        val attributeOnly = XmlMapper(
            XmlConfig.builder().attributePrefix("@").build(),
        ).format(ObjectNode.of("@id" to NumberNode.of(1)))
        assertTrue(attributeOnly.startsWith("<root"))
    }

    @Test
    fun defaultUseWrapperControlsCollectionWrapping() {
        val bean = WrapperBean().apply { tags = listOf("a", "b") }

        val wrappedXml = XmlMapper().format(bean)
        assertTrue(wrappedXml.contains("<tags><item>a</item><item>b</item></tags>"))

        val unwrappedMapper = XmlMapper(XmlConfig.builder().defaultUseWrapper(false).build())
        val unwrappedXml = unwrappedMapper.format(bean)
        assertTrue(unwrappedXml.contains("<tags>a</tags><tags>b</tags>"))
        assertFalse(unwrappedXml.contains("<item"))

        val restored = unwrappedMapper.parse(unwrappedXml, WrapperBean::class.java)
        assertEquals(listOf("a", "b"), restored.tags)

        // 空集合在无包装模式下不写出。
        assertFalse(unwrappedMapper.format(WrapperBean()).contains("<tags"))
    }

    @Test
    fun nameForTextElementIsAliasForTextKey() {
        val config = XmlConfig.builder()
            .attributePrefix("@")
            .nameForTextElement("value")
            .build()
        assertEquals("value", config.textKey)

        val mapper = XmlMapper(config)
        val node = mapper.parse("<price currency=\"CNY\">100</price>", Node::class.java).asObject()
        assertEquals("CNY", node["@currency"]?.asString())
        assertEquals(100, node["value"]?.asNumber()?.toInt())

        val bean = mapper.parse(
            mapper.format(
                TextBean().apply {
                    currency = "CNY"
                    value = 100
                },
            ),
            TextBean::class.java,
        )
        assertEquals("CNY", bean.currency)
        assertEquals(100, bean.value)
    }

    @Test
    fun nameProcessorsReplaceAndEncodeNames() {
        val node = ObjectNode.of("user name" to StringNode.of("x"))

        val replaced = node.toXmlString(
            typeAttributes = true,
            writeFeatures = ROUND_TRIP_FEATURES,
            nameProcessor = XmlNameProcessors.replacement(),
        )
        assertTrue(replaced.contains("<user_name type=\"string\">x</user_name>"))

        val base64 = node.toXmlString(
            typeAttributes = true,
            writeFeatures = ROUND_TRIP_FEATURES,
            nameProcessor = XmlNameProcessors.base64(),
        )
        assertTrue(base64.contains("<base64_"))
        val decoded = XmlReader(base64, nameProcessor = XmlNameProcessors.base64()).read().asObject()
        assertEquals("x", decoded["user name"]?.asString())

        val alwaysOn = node.toXmlString(
            typeAttributes = true,
            writeFeatures = ROUND_TRIP_FEATURES,
            nameProcessor = XmlNameProcessors.alwaysOnBase64(),
        )
        assertTrue(alwaysOn.contains("<base64_"))
        val alwaysOnDecoded = XmlReader(alwaysOn, nameProcessor = XmlNameProcessors.alwaysOnBase64())
            .read()
            .asObject()
        assertEquals("x", alwaysOnDecoded["user name"]?.asString())

        // 默认 passthrough 保持旧行为（_xHHHH_ 转义）。
        assertTrue(node.toXmlString().contains("user_x0020_name"))
    }

    @Test
    fun mapperAppliesNameProcessorToAttributes() {
        val mapper = XmlMapper(
            XmlConfig.builder()
                .attributePrefix("@")
                .nameProcessor(XmlNameProcessors.base64())
                .build(),
        )
        val node = ObjectNode.of(
            "user name" to StringNode.of("x"),
            "@user id" to NumberNode.of(7),
        )

        val xml = mapper.format(node)
        val reparsed = mapper.parse(xml, Node::class.java).asObject()
        assertEquals("x", reparsed["user name"]?.asString())
        assertEquals(7, reparsed["@user id"]?.asNumber()?.toInt())
    }

    @Test
    fun commonDocumentMapperConfigConvenienceMethods() {
        val config = XmlConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .fileCharset(Charsets.UTF_16)
            .atomicFileWrite(true)
            .createParentDirectories(true)
            .maxInputSize(1024)
            .maxStringLength(64)
            .maxNodeDepth(32)
            .maxNodeCount(128)
            .remoteReferenceResolver { NullNode.INSTANCE }
            .beanValidator { _ -> }
            .failOnNullForPrimitives(true)
            .build()

        assertEquals(PropertyNamingStrategy.SNAKE_CASE, config.namingStrategy)
        assertEquals(Charsets.UTF_16, config.fileCharset)
        assertTrue(config.atomicFileWrite)
        assertTrue(config.createParentDirectories)
        assertEquals(1024L, config.maxInputSize)
        assertEquals(64, config.maxStringLength)
        assertEquals(32, config.maxNodeDepth)
        assertEquals(128L, config.maxNodeCount)
        assertNotNull(config.remoteReferenceResolver)
        assertNotNull(config.beanValidator)
        assertTrue(config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
        assertEquals(PropertyNamingStrategy.AS_IS, DocumentMapperConfig.empty().namingStrategy)
    }

    @Test
    fun featureEnableDisableConfigureAndFeatureDefaults() {
        val config = XmlConfig.builder()
            .disable(XmlReadFeature.AUTO_DETECT_XSI_TYPE)
            .enable(XmlReadFeature.EMPTY_ELEMENT_AS_NULL)
            .configure(XmlReadFeature.PROCESS_XSI_NIL, false)
            .enable(XmlWriteFeature.WRITE_XML_DECLARATION)
            .configure(XmlWriteFeature.WRITE_NULLS_AS_XSI_NIL, false)
            .build()

        assertFalse(config.autoDetectXsiType)
        assertTrue(config.emptyElementAsNull)
        assertFalse(config.processXsiNil)
        assertTrue(config.writeXmlDeclaration)
        assertFalse(config.writeNullsAsXsiNil)
    }

    private class SingleFieldBean {
        var name: String? = null
    }

    private class WrapperBean {
        var tags: List<String> = emptyList()
    }

    private class TextBean {
        @field:DocumentName("@currency")
        var currency: String? = null

        @field:DocumentName("value")
        var value: Int = 0
    }

    private companion object {

        /** 关闭根对象展开，便于 Node 树写入后按 `<root>` 重新读取。 */
        private val ROUND_TRIP_FEATURES: Set<XmlWriteFeature> =
            XmlConfig.DEFAULT_WRITE_FEATURES - XmlWriteFeature.UNWRAP_ROOT_OBJECT_NODE
    }
}
