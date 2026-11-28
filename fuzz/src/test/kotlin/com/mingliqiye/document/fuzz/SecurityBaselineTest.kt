package com.mingliqiye.document.fuzz

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.format.cbor.exception.CborParseException
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.xml.exception.XmlParseException
import com.mingliqiye.document.format.yaml.YamlReader
import com.mingliqiye.document.format.yaml.exception.YamlParseException
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * 安全测试基线：覆盖外部实体、alias 膨胀、深度/长度/输入大小限制。
 */
class SecurityBaselineTest {

    @Test
    fun xmlExternalEntityIsRejected() {
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <root>&xxe;</root>
        """.trimIndent()
        assertFailsWith<XmlParseException> {
            XmlMapper().parse(xml, Node::class.java)
        }
    }

    @Test
    fun yamlAliasBombIsRejected() {
        val yaml = """
            a: &a ["x","x","x"]
            b: &b [*a,*a,*a]
            c: &c [*b,*b,*b]
            d: &d [*c,*c,*c]
            e: [*d,*d,*d]
        """.trimIndent()
        assertFailsWith<YamlParseException> {
            YamlReader(yaml, maxAliases = 3).read()
        }
    }

    @Test
    fun jsonNodeDepthAndStringLimits() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .maxNodeDepth(2)
                .maxStringLength(3)
                .build(),
        )
        val deep = JsonMapper().parse("""{"a":{"b":{"c":1}}}""", Node::class.java)
        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(deep, Map::class.java)
        }
        val long = JsonMapper().parse("""{"a":"long"}""", Node::class.java)
        assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(long, Map::class.java)
        }
    }

    @Test
    fun maxInputSizeIsEnforced() {
        val mapper = JsonMapper(DocumentMapperConfig.builder().maxInputSize(4).build())
        assertFailsWith<DocumentSerializationException> {
            mapper.parse("""{"a":1}""", Map::class.java)
        }
    }

    @Test
    fun cborDepthLimitIsEnforced() {
        val nested = byteArrayOf(0x81.toByte(), 0x81.toByte(), 0x81.toByte(), 0x01)
        assertFailsWith<CborParseException> {
            CborMapper(DocumentMapperConfig.builder().maxNodeDepth(2).build()).parse(nested, Node::class.java)
        }
    }
}
