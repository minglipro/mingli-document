package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentInclude
import com.mingliqiye.document.annotation.DocumentIncludePolicy
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.json.JsonReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 类级/字段级 [DocumentInclude] 测试。
 *
 * 覆盖：ALWAYS / NON_NULL / NON_EMPTY、字段级覆盖类级、USE_DEFAULTS 回退全局
 * `writeNulls`、Node 路径与流式写出路径一致、嵌套对象与集合元素按各自类型策略处理、
 * Kotlin data class 的 `@field:` target，以及反序列化不受影响。
 */
class DocumentIncludeTest {

    private val json = JsonMapper()

    @Test
    fun classLevelNonEmptyOmitsNullAndEmptyValues() {
        val node = json.toNode(fullIncludeBean()).asObject()

        assertEquals(
            setOf("text", "tags", "numbers", "alwaysNull", "child", "children"),
            node.keys,
            node.toString(),
        )
        assertEquals(NodeType.NULL, node["alwaysNull"]?.type)
        assertFalse(node.containsKey("nullText"))
        assertFalse(node.containsKey("emptyText"))
        assertFalse(node.containsKey("emptyList"))
        assertFalse(node.containsKey("emptyMap"))
        assertFalse(node.containsKey("emptyArray"))
        assertFalse(node.containsKey("nonNullField"))
        assertFalse(node.containsKey("defaultsField"))
    }

    @Test
    fun fieldLevelPoliciesOverrideClassLevelAndUseDefaultsDefers() {
        val bean = IncludeBean().apply {
            nonNullField = "value"
            defaultsField = null
            alwaysNull = null
        }

        val node = json.toNode(bean).asObject()
        assertEquals("value", node["nonNullField"]?.asString())
        assertTrue(node.containsKey("alwaysNull"), "字段级 ALWAYS 应覆盖类级 NON_EMPTY 写出 null")
        assertEquals(NodeType.NULL, node["alwaysNull"]?.type)
        assertFalse(node.containsKey("defaultsField"), "USE_DEFAULTS 在无更高级策略时回退类级/全局策略")

        val defaults = IncludeDefaultsBean().apply { name = null }
        assertTrue(json.toNode(defaults).asObject().containsKey("name"))
        val withoutNulls = JsonMapper(
            DocumentMapperConfig.builder().writeNulls(false).build(),
        )
        assertFalse(withoutNulls.toNode(defaults).asObject().containsKey("name"))
    }

    @Test
    fun classLevelAlwaysOverridesGlobalWriteNullsFalse() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder().writeNulls(false).build(),
        )
        val node = mapper.toNode(IncludeAlwaysBean().apply { text = "t" }).asObject()

        assertTrue(node.containsKey("nullValue"), "类级 ALWAYS 应覆盖全局 writeNulls=false")
        assertEquals(NodeType.NULL, node["nullValue"]?.type)
        assertEquals("t", node["text"]?.asString())

        val plain = mapper.toNode(User()).asObject()
        assertFalse(plain.containsKey("name"), "未标注类型的 null 属性仍按全局配置省略")
    }

    @Test
    fun streamingWriterPathAppliesSamePolicy() {
        val text = json.format(fullIncludeBean())
        val node = JsonReader.parse(text).asObject()

        assertEquals(
            setOf("text", "tags", "numbers", "alwaysNull", "child", "children"),
            node.keys,
            text,
        )
        assertTrue(text.contains("\"alwaysNull\":null"), text)
        assertFalse(text.contains("\"nullText\""), text)
        assertFalse(text.contains("\"emptyList\""), text)
        assertFalse(text.contains("\"emptyMap\""), text)
    }

    @Test
    fun nestedObjectsAndCollectionElementsUseOwnClassPolicy() {
        val bean = IncludeBean().apply {
            child = IncludeChild().apply { emptyText = "" }
            children = listOf(
                IncludeChild().apply { emptyText = "" },
                IncludeChild().apply {
                    name = "n"
                    nickname = "nick"
                    emptyText = ""
                },
            )
        }

        val node = json.toNode(bean).asObject()
        val child = assertNotNull(node["child"]?.asObject())
        assertEquals(setOf("emptyText"), child.keys, "嵌套 Bean 使用自身类级 NON_NULL")
        assertEquals("", child["emptyText"]?.asString())

        val children = assertNotNull(node["children"]?.asArray())
        assertEquals(2, children.size)
        assertEquals(setOf("emptyText"), children[0].asObject().keys)
        assertEquals(setOf("name", "nickname", "emptyText"), children[1].asObject().keys)
    }

    @Test
    fun kotlinDataClassFieldTargetAndLists() {
        assertEquals(
            setOf("name"),
            json.toNode(KotlinIncludeModel(name = "a", tags = emptyList())).asObject().keys,
        )
        assertEquals(
            setOf("name", "tags"),
            json.toNode(KotlinIncludeModel(name = "a", tags = listOf("x"))).asObject().keys,
        )

        val array = json.toNode(
            listOf(
                KotlinIncludeModel(name = null, tags = emptyList()),
                KotlinIncludeModel(name = "a", tags = listOf("x")),
            ),
        ).asArray()
        assertEquals(2, array.size)
        assertTrue(array[0].asObject().keys.isEmpty(), array[0].toString())
        assertEquals(setOf("name", "tags"), array[1].asObject().keys)
    }

    @Test
    fun deserializationIsNotAffected() {
        val bean = json.parse(
            """{"nullText":null,"text":"t","alwaysNull":null,"emptyText":""}""",
            IncludeBean::class.java,
        )
        assertNull(bean.nullText)
        assertEquals("t", bean.text)
        assertNull(bean.alwaysNull)
        assertEquals("", bean.emptyText)
    }

    private fun fullIncludeBean(): IncludeBean = IncludeBean().apply {
        nullText = null
        emptyText = ""
        text = "t"
        emptyList = emptyList()
        tags = listOf("a")
        emptyMap = emptyMap()
        emptyArray = intArrayOf()
        numbers = intArrayOf(1, 2)
        alwaysNull = null
        nonNullField = null
        defaultsField = null
        child = IncludeChild().apply {
            name = null
            nickname = null
            emptyText = ""
        }
        children = listOf(
            IncludeChild().apply {
                name = "n"
                emptyText = ""
            },
        )
    }
}

@DocumentInclude(DocumentIncludePolicy.NON_NULL)
private data class KotlinIncludeModel(
    val name: String? = null,
    @field:DocumentInclude(DocumentIncludePolicy.NON_EMPTY)
    val tags: List<String> = emptyList(),
)
