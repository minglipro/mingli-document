package com.mingliqiye.document.format.ion

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.ion.exception.IonException
import com.mingliqiye.document.format.ion.exception.IonParseException
import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 上游实现 Ion 配置对齐测试（特性、默认值、行为与通用配置转发）。
 */
class IonConfigFeatureTest {

    @Test
    fun defaultsAlignWithJackson() {
        val config = IonConfig.builder().build()

        assertEquals(IonConfig.DEFAULT_READ_FEATURES, config.readFeatures)
        assertEquals(IonConfig.DEFAULT_WRITE_FEATURES, config.writeFeatures)
        assertEquals(
            setOf(IonReadFeature.USE_NATIVE_TYPE_ID, IonReadFeature.READ_NULL_AS_IONVALUE),
            config.readFeatures,
        )
        assertEquals(setOf(IonWriteFeature.USE_NATIVE_TYPE_ID), config.writeFeatures)

        assertTrue(config.isEnabled(IonReadFeature.USE_NATIVE_TYPE_ID))
        assertTrue(config.isEnabled(IonReadFeature.READ_NULL_AS_IONVALUE))
        assertFalse(config.isEnabled(IonReadFeature.STRICT_DUPLICATE_DETECTION))
        assertTrue(config.isEnabled(IonWriteFeature.USE_NATIVE_TYPE_ID))
        assertFalse(config.isEnabled(IonWriteFeature.STRICT_DUPLICATE_DETECTION))
        assertFalse(config.preserveTypes)
    }

    @Test
    fun builderEnableDisableConfigure() {
        val config = IonConfig.builder()
            .disable(IonReadFeature.READ_NULL_AS_IONVALUE)
            .enable(IonReadFeature.STRICT_DUPLICATE_DETECTION)
            .configure(IonReadFeature.USE_NATIVE_TYPE_ID, false)
            .disable(IonWriteFeature.USE_NATIVE_TYPE_ID)
            .enable(IonWriteFeature.STRICT_DUPLICATE_DETECTION)
            .build()

        assertEquals(setOf(IonReadFeature.STRICT_DUPLICATE_DETECTION), config.readFeatures)
        assertEquals(setOf(IonWriteFeature.STRICT_DUPLICATE_DETECTION), config.writeFeatures)

        val reenabled = IonConfig.builder()
            .configure(IonReadFeature.USE_NATIVE_TYPE_ID, true)
            .configure(IonWriteFeature.USE_NATIVE_TYPE_ID, true)
            .build()
        assertTrue(reenabled.isEnabled(IonReadFeature.USE_NATIVE_TYPE_ID))
        assertTrue(reenabled.isEnabled(IonWriteFeature.USE_NATIVE_TYPE_ID))
    }

    @Test
    fun commonConvenienceOptionsAreForwarded() {
        val config = IonConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .fileCharset(Charsets.UTF_16LE)
            .atomicFileWrite(true)
            .createParentDirectories(true)
            .maxInputSize(1024L)
            .maxStringLength(128)
            .maxNodeDepth(16)
            .maxNodeCount(256L)
            .resolveReferences(true)
            .writeReferences(true)
            .failOnNullForPrimitives(true)
            .preserveTypes(true)
            .build()

        assertEquals(PropertyNamingStrategy.SNAKE_CASE, config.namingStrategy)
        assertEquals(Charsets.UTF_16LE, config.fileCharset)
        assertTrue(config.atomicFileWrite)
        assertTrue(config.createParentDirectories)
        assertEquals(1024L, config.maxInputSize)
        assertEquals(128, config.maxStringLength)
        assertEquals(16, config.maxNodeDepth)
        assertEquals(256L, config.maxNodeCount)
        assertTrue(config.resolveReferences)
        assertTrue(config.writeReferences)
        assertTrue(config.isEnabled(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES))
        assertTrue(config.preserveTypes)

        val mapper = IonMapper(IonConfig.builder().namingStrategy(PropertyNamingStrategy.SNAKE_CASE).build())
        assertEquals("小于", mapper.parse("""{user_name: "小于"}""", Person::class.java).userName)
    }

    @Test
    fun readNullAsIonValueDisabledPreservesContainerNulls() {
        val text = "{a: null.list, b: null.sexp, c: null.struct, d: null.int, e: null}"

        val defaultNode = IonReader(text).read().asObject()
        for (field in listOf("a", "b", "c", "d", "e")) {
            assertTrue(defaultNode[field]!!.isNull, "field $field should be NullNode by default")
        }

        val noIonNullFeatures = IonConfig.DEFAULT_READ_FEATURES - IonReadFeature.READ_NULL_AS_IONVALUE
        val node = IonReader(text, readFeatures = noIonNullFeatures).read().asObject()
        assertEquals(0, node["a"]?.asArray()?.size)
        assertEquals(0, node["b"]?.asArray()?.size)
        assertEquals(0, node["c"]?.asObject()?.size)
        assertTrue(node["d"]!!.isNull)
        assertTrue(node["e"]!!.isNull)

        val binaryNode = IonBinaryReader(text.toByteArray(Charsets.UTF_8), readFeatures = noIonNullFeatures)
            .read()
            .asObject()
        assertEquals(0, binaryNode["c"]?.asObject()?.size)
        assertTrue(binaryNode["d"]!!.isNull)

        val mapper = IonMapper(IonConfig.builder().disable(IonReadFeature.READ_NULL_AS_IONVALUE).build())
        val mapperNode = mapper.parse(text, Node::class.java).asObject()
        assertEquals(0, mapperNode["a"]?.asArray()?.size)
        assertEquals(0, mapperNode["c"]?.asObject()?.size)

        // 非默认读取特性会禁用 Token 快路径，二进制解析同样应用该语义。
        val mapperBinary = mapper.parse(text.toByteArray(Charsets.UTF_8), Node::class.java).asObject()
        assertEquals(0, mapperBinary["c"]?.asObject()?.size)
        assertTrue(mapperBinary["d"]!!.isNull)
    }

    @Test
    fun nativeTypeIdRoundTripsAnnotationsInPreserveTypesMode() {
        val mapper = IonMapper(IonConfig.builder().preserveTypes(true).build())
        val source = """'com.example.User'::{name: "小于", tags: ann::[1, 2]}"""

        val node = mapper.parse(source, Node::class.java)
        val wrapper = node.asObject()
        assertEquals(
            listOf("com.example.User"),
            wrapper["\$ionAnnotations"]?.asArray()?.asList()?.map { it.asString() },
        )
        val inner = wrapper["value"]!!.asObject()
        assertEquals("小于", inner["name"]?.asString())
        assertEquals(
            listOf("ann"),
            inner["tags"]?.asObject()?.get("\$ionAnnotations")?.asArray()?.asList()?.map { it.asString() },
        )
        assertEquals(2, inner["tags"]?.asObject()?.get("value")?.asArray()?.size)

        val text = mapper.format(node)
        assertTrue(text.contains("com.example.User"))
        assertTrue(text.contains("ann::"))
        assertEquals(node, mapper.parse(text, Node::class.java))

        val binary = mapper.formatBytes(node)
        assertEquals(node, mapper.parse(binary, Node::class.java))
    }

    @Test
    fun nativeTypeIdCanBeDisabled() {
        val readDropped = IonMapper(
            IonConfig.builder()
                .preserveTypes(true)
                .disable(IonReadFeature.USE_NATIVE_TYPE_ID)
                .build(),
        )
        val plainNode = readDropped.parse("ann::{a: 1}", Node::class.java)
        assertNull(plainNode.asObject()["\$ionAnnotations"])
        assertEquals(1, plainNode.asObject()["a"]?.asNumber()?.toInt())

        val mapper = IonMapper(
            IonConfig.builder()
                .preserveTypes(true)
                .disable(IonReadFeature.USE_NATIVE_TYPE_ID)
                .disable(IonWriteFeature.USE_NATIVE_TYPE_ID)
                .build(),
        )
        val annotationWrapper = annotationWrapper("ann", ObjectNode.of("a" to NumberNode.of(1)))
        val text = mapper.format(annotationWrapper)
        assertEquals(annotationWrapper, IonReader(text).read())

        val binary = mapper.formatBytes(annotationWrapper)
        assertEquals(annotationWrapper, IonBinaryReader(binary).read())
    }

    @Test
    fun annotationsAreIgnoredOutsidePreserveTypesMode() {
        val node = IonMapper().parse("ann::{a: 1}", Node::class.java)
        assertNull(node.asObject()["\$ionAnnotations"])
        assertEquals(1, node.asObject()["a"]?.asNumber()?.toInt())
    }

    @Test
    fun strictDuplicateDetectionOnRead() {
        val text = "{a: 1, a: 2}"
        assertEquals(2, IonReader(text).read().asObject()["a"]?.asNumber()?.toInt())

        val strictFeatures = IonConfig.DEFAULT_READ_FEATURES + IonReadFeature.STRICT_DUPLICATE_DETECTION
        assertFailsWith<IonParseException> {
            IonReader(text, readFeatures = strictFeatures).read()
        }

        val strictMapper = IonMapper(IonConfig.builder().enable(IonReadFeature.STRICT_DUPLICATE_DETECTION).build())
        assertFailsWith<IonParseException> {
            strictMapper.parse(text, Node::class.java)
        }

        val duplicateBinary = duplicateFieldBinary()
        assertEquals(2, IonBinaryReader(duplicateBinary).read().asObject()["a"]?.asNumber()?.toInt())
        assertFailsWith<IonParseException> {
            IonBinaryReader(duplicateBinary, readFeatures = strictFeatures).read()
        }
        // 非默认读取特性禁用 Token 快路径，二进制 Mapper 解析也会检测重复字段。
        assertFailsWith<IonParseException> {
            strictMapper.parse(duplicateBinary, Node::class.java)
        }
    }

    @Test
    fun strictDuplicateDetectionOnWrite() {
        val strictFeatures = IonConfig.DEFAULT_WRITE_FEATURES + IonWriteFeature.STRICT_DUPLICATE_DETECTION
        val strictWriter = IonBinaryTokenWriter(writeFeatures = strictFeatures)
        strictWriter.startObject()
        strictWriter.fieldName("a")
        strictWriter.writeNumber("1")
        assertFailsWith<IonException> {
            strictWriter.fieldName("a")
        }

        val output = ByteArrayOutputStream()
        val strictTextWriter = IonTextTokenWriter(
            output,
            pretty = false,
            charset = Charsets.UTF_8,
            writeFeatures = strictFeatures,
        )
        strictTextWriter.startObject()
        strictTextWriter.fieldName("a")
        strictTextWriter.writeNumber("1")
        assertFailsWith<IonException> {
            strictTextWriter.fieldName("a")
        }

        val lenientWriter = IonBinaryTokenWriter()
        lenientWriter.startObject()
        lenientWriter.fieldName("a")
        lenientWriter.writeNumber("1")
        lenientWriter.fieldName("a")
        lenientWriter.writeNumber("2")
        lenientWriter.endObject()
        val bytes = lenientWriter.toByteArray()
        assertEquals(2, IonBinaryReader(bytes).read().asObject()["a"]?.asNumber()?.toInt())
    }

    @Test
    fun annotationWrapperWritesNativelyOnDefaultPaths() {
        val wrapper = annotationWrapper("ann", ObjectNode.of("a" to NumberNode.of(1)))
        val mapper = IonMapper()

        val text = mapper.format(wrapper)
        assertTrue(text.contains("ann::"))
        assertEquals(wrapper, IonReader(text, preserveTypes = true).read())
        assertEquals(wrapper, wrapper.toIonString().let { IonReader(it, preserveTypes = true).read() })

        val binary = mapper.formatBytes(wrapper)
        assertEquals(wrapper, IonBinaryReader(binary, preserveTypes = true).read())
        assertEquals(wrapper, IonBinaryReader(wrapper.toIonBytes(), preserveTypes = true).read())

        // 文本流式写出路径同样识别注解包装对象。
        val output = ByteArrayOutputStream()
        mapper.format(wrapper, output)
        assertEquals(wrapper, IonReader(output.toString("UTF-8"), preserveTypes = true).read())
    }

    private fun annotationWrapper(annotation: String, value: Node): ObjectNode = ObjectNode.of(
        "\$ionAnnotations" to ArrayNode.of(StringNode.of(annotation)),
        "value" to value,
    )

    private fun duplicateFieldBinary(): ByteArray {
        val writer = IonBinaryTokenWriter()
        writer.startObject()
        writer.fieldName("a")
        writer.writeNumber("1")
        writer.fieldName("a")
        writer.writeNumber("2")
        writer.endObject()
        return writer.toByteArray()
    }

    private class Person {
        var userName: String? = null
    }
}
