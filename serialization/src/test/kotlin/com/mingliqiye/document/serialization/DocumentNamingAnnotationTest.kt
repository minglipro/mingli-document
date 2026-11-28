package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.annotation.DocumentNaming
import com.mingliqiye.document.annotation.DocumentNamingStrategy
import com.mingliqiye.document.format.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 类级/字段级 [DocumentNaming] 测试。
 *
 * 覆盖：类级命名、字段级覆盖、getter 级覆盖、`@DocumentName` 最高优先级、
 * 嵌套 Bean、Kotlin `@field:` / `@get:` 常见 target、Builder 模式（封闭类注解继承）
 * 与 `@DocumentCreator` 构造参数。
 */
class DocumentNamingAnnotationTest {

    private val json = JsonMapper()

    @Test
    fun classLevelAndFieldLevelStrategiesApply() {
        val bean = NamingOverrideBean().apply {
            userName = "小于"
            lastLoginAt = "2026-01-01"
            displayName = "小明"
            explicitValue = "raw"
            getterNamed = "getter value"
            child = NamingOverrideChild().apply {
                childName = "小红"
                zipCode = 310000
            }
        }

        val node = json.toNode(bean).asObject()
        assertEquals("小于", node["user_name"]?.asString())
        assertEquals("2026-01-01", node["last_login_at"]?.asString())
        assertEquals("小明", node["display-name"]?.asString())
        assertEquals("raw", node["explicit_name"]?.asString())
        assertEquals("getter value", node["GetterNamed"]?.asString())
        assertFalse(node.containsKey("userName"))
        assertFalse(node.containsKey("displayName"))

        val child = assertNotNull(node["child"]?.asObject())
        assertEquals("小红", child["child-name"]?.asString())
        assertEquals(310000, child["zip-code"]?.asNumber()?.toInt())
    }

    @Test
    fun explicitDocumentNameHasHighestPriority() {
        val node = json.toNode(
            NamingOverrideBean().apply {
                explicitValue = "v"
                displayName = "d"
            },
        ).asObject()

        assertEquals("v", node["explicit_name"]?.asString())
        assertFalse(node.containsKey("explicitValue"))
        assertFalse(node.containsKey("explicit_value"))
        assertEquals("d", node["display-name"]?.asString())
    }

    @Test
    fun roundTripThroughNodeAndJson() {
        val source = NamingOverrideBean().apply {
            userName = "小于"
            lastLoginAt = "2026-01-01"
            displayName = "小明"
            explicitValue = "raw"
            getterNamed = "getter value"
            child = NamingOverrideChild().apply {
                childName = "小红"
                zipCode = 310000
            }
        }

        val fromNode = json.fromNode(json.toNode(source), NamingOverrideBean::class.java)
        assertEquals(source.userName, fromNode.userName)
        assertEquals(source.lastLoginAt, fromNode.lastLoginAt)
        assertEquals(source.displayName, fromNode.displayName)
        assertEquals(source.explicitValue, fromNode.explicitValue)
        assertEquals(source.getterNamed, fromNode.getterNamed)
        assertEquals(source.child?.childName, fromNode.child?.childName)
        assertEquals(source.child?.zipCode, fromNode.child?.zipCode)

        val text = json.format(source)
        assertTrue(text.contains("\"user_name\""), text)
        val fromJson = json.parse(text, NamingOverrideBean::class.java)
        assertEquals(source.userName, fromJson.userName)
        assertEquals(source.displayName, fromJson.displayName)
        assertEquals(source.getterNamed, fromJson.getterNamed)
    }

    @Test
    fun kotlinFieldAndGetterTargetsApply() {
        val model = KotlinNamingModel().apply {
            userName = "小于"
            displayName = "小明"
            explicitValue = "raw"
            getterNamed = "getter value"
        }

        val node = json.toNode(model).asObject()
        assertEquals("小于", node["user_name"]?.asString())
        assertEquals("小明", node["display-name"]?.asString())
        assertEquals("raw", node["raw_name"]?.asString())
        assertEquals("getter value", node["GetterNamed"]?.asString())
        assertFalse(node.containsKey("userName"))
        assertFalse(node.containsKey("displayName"))

        val restored = json.fromNode(node, KotlinNamingModel::class.java)
        assertEquals(model.userName, restored.userName)
        assertEquals(model.displayName, restored.displayName)
        assertEquals(model.explicitValue, restored.explicitValue)
        assertEquals(model.getterNamed, restored.getterNamed)
    }

    @Test
    fun builderInheritsEnclosingClassAnnotation() {
        val source = NamingBuilderBean.builder()
            .setUserName("小于")
            .setItemCount(3)
            .build()

        val text = json.format(source)
        assertTrue(text.contains("\"user_name\""), text)
        assertTrue(text.contains("\"item_count\""), text)
        assertFalse(text.contains("\"userName\""), text)

        val restored = json.parse(text, NamingBuilderBean::class.java)
        assertEquals("小于", restored.userName)
        assertEquals(3, restored.itemCount)
    }

    @Test
    fun creatorParametersInheritClassLevelNaming() {
        val restored = json.parse(
            """{"user_name":"小于","item_count":2}""",
            NamingCreatorBean::class.java,
        )
        assertEquals("小于", restored.userName)
        assertEquals(2, restored.itemCount)

        val text = json.format(restored)
        assertTrue(text.contains("\"user_name\""), text)
        assertTrue(text.contains("\"item_count\""), text)
        assertEquals("小于", json.parse(text, NamingCreatorBean::class.java).userName)
    }

    @Test
    fun creatorParameterNamingAnnotationOverridesClassAndConfig() {
        val restored = json.parse(
            """{"user-name":"小于","ItemCount":2}""",
            NamingParamBean::class.java,
        )
        assertEquals("小于", restored.userName)
        assertEquals(2, restored.itemCount)

        val text = json.format(restored)
        assertTrue(text.contains("\"user-name\""), text)
        assertTrue(text.contains("\"ItemCount\""), text)
        assertFalse(text.contains("\"userName\""), text)
    }
}

@DocumentNaming(DocumentNamingStrategy.SnakeCaseStrategy)
private class KotlinNamingModel {
    var userName: String = ""

    @field:DocumentNaming(DocumentNamingStrategy.KebabCaseStrategy)
    var displayName: String = ""

    @field:DocumentName("raw_name")
    var explicitValue: String = ""

    @get:DocumentNaming(DocumentNamingStrategy.UpperCamelStrategy)
    var getterNamed: String = ""
}
