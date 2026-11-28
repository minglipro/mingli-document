package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 只读属性（没有 setter 且没有可写构造参数）测试。
 *
 * 期望行为：序列化照常写出这些属性；反序列化时直接跳过，不当作未知字段报错，
 * 也不受 [MissingFieldPolicy.FAIL] 约束。存在 canonical / [DocumentCreator] 构造器时
 * 由构造参数注入（见 [ConstructorInjectionTest] 与 RecordMappingTest）。
 */
class ReadOnlyPropertyTest {

    private val json = JsonMapper()

    private val strictJson = JsonMapper(
        DocumentMapperConfig.builder()
            .failOnUnknownFields(true)
            .build(),
    )

    @Test
    fun getterOnlyPropertiesSerializeAndAreSkippedOnDeserialize() {
        val source = ReadOnlyBean().apply { name = "小于" }

        val node = json.toNode(source).asObject()
        assertEquals(7, node["id"]?.asNumber()?.toInt())
        assertEquals("小于", node["upperName"]?.asString())
        assertEquals("小于", node["name"]?.asString())

        val restored = json.fromNode(node, ReadOnlyBean::class.java)
        assertEquals("小于", restored.name)
        assertEquals(7, restored.id)
        assertEquals("小于", restored.upperName)
    }

    @Test
    fun readOnlyPropertiesAreNotUnknownFields() {
        val source = ReadOnlyBean().apply { name = "abc" }

        val node = strictJson.toNode(source)
        val restored = strictJson.fromNode(node, ReadOnlyBean::class.java)

        assertEquals("abc", restored.name)
        assertEquals(7, restored.id)
    }

    @Test
    fun readOnlyPropertiesAreNotRequiredByMissingFieldFailPolicy() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.FAIL)
                .build(),
        )

        val restored = mapper.fromNode(
            ObjectNode.of("name" to StringNode.of("abc")),
            ReadOnlyBean::class.java,
        )

        assertEquals("abc", restored.name)
        assertEquals(7, restored.id)
    }

    @Test
    fun creatorBeanReadOnlyGetterIsSerializedAndSkipped() {
        val source = CreatorReadOnlyModel("abc")

        val text = strictJson.format(source)
        assertTrue(text.contains("\"name\":\"abc\""), text)
        assertTrue(text.contains("\"read_only_label\":\"ABC\""), text)

        val restored = strictJson.parse(text, CreatorReadOnlyModel::class.java)
        assertEquals("abc", restored.name)
        assertEquals("ABC", restored.readOnlyLabel)
    }

    @Test
    fun creatorBeanReadOnlyGetterIsNotRequiredByMissingFieldFailPolicy() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.FAIL)
                .build(),
        )

        val restored = mapper.parse("""{"name":"abc"}""", CreatorReadOnlyModel::class.java)
        assertEquals("abc", restored.name)
        assertEquals("ABC", restored.readOnlyLabel)
    }

    @Test
    fun finalFieldWithoutSetterIsSkippedOnDeserialize() {
        val source = ReadOnlyFieldModel().apply { name = "n" }

        val node = strictJson.toNode(source).asObject()
        assertEquals("fixed", node["tag"]?.asString())
        assertEquals("n", node["name"]?.asString())

        val restored = strictJson.fromNode(node, ReadOnlyFieldModel::class.java)
        assertEquals("n", restored.name)
        assertEquals("fixed", restored.tag)
    }
}

/**
 * 测试用只读 Bean：`id`/`upperName` 只有 getter，没有 setter。
 */
private class ReadOnlyBean {
    var name: String? = null

    val id: Int = 7

    val upperName: String
        get() = name.orEmpty().uppercase()
}
