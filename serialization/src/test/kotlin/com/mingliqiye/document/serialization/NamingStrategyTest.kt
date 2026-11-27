package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.format.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 字段命名策略测试。
 *
 * 覆盖内置策略、显式 [DocumentName] 优先、Java Bean 属性/public 字段、
 * 嵌套 Bean、集合以及 Kotlin data class。
 */
class NamingStrategyTest {

    @Test
    fun defaultStrategyIsAsIsAndExplicitAnnotationWins() {
        assertEquals(PropertyNamingStrategy.AS_IS, DocumentMapperConfig.empty().namingStrategy)
        assertEquals(PropertyNamingStrategy.AS_IS, DocumentMapperConfig.builder().build().namingStrategy)

        val node = JsonMapper().toNode(namingBean()).asObject()
        assertEquals("小于", node["firstName"]?.asString())
        assertEquals("ming", node["lastName"]?.asString())
        assertEquals(18, node["userAge"]?.asNumber()?.toInt())
        assertEquals("杭州", node["homeAddress"]?.asObject()?.get("childName")?.asString())
        assertEquals("field_value", node["public_field"]?.asString())
        assertEquals("acronym", node["URLValue"]?.asString())
        assertEquals("小明", node["explicit_name"]?.asString())
        assertEquals("getter value", node["getter_explicit"]?.asString())
        assertFalse(node.containsKey("displayName"))
    }

    @Test
    fun translateConvertsKnownStyles() {
        assertEquals("userName", PropertyNamingStrategy.AS_IS.translate("userName"))
        assertEquals("", PropertyNamingStrategy.AS_IS.translate(""))

        assertEquals("user_name", PropertyNamingStrategy.SNAKE_CASE.translate("userName"))
        assertEquals("url_value", PropertyNamingStrategy.SNAKE_CASE.translate("URLValue"))
        assertEquals("field1_name", PropertyNamingStrategy.SNAKE_CASE.translate("field1Name"))
        assertEquals("first_name", PropertyNamingStrategy.SNAKE_CASE.translate("first_name"))

        assertEquals("user-name", PropertyNamingStrategy.KEBAB_CASE.translate("userName"))
        assertEquals("url-value", PropertyNamingStrategy.KEBAB_CASE.translate("URLValue"))

        assertEquals("UserName", PropertyNamingStrategy.UPPER_CAMEL.translate("user_name"))
        assertEquals("UrlValue", PropertyNamingStrategy.UPPER_CAMEL.translate("URLValue"))
        assertEquals("Name", PropertyNamingStrategy.UPPER_CAMEL.translate("name"))

        assertEquals("userName", PropertyNamingStrategy.LOWER_CAMEL.translate("user_name"))
        assertEquals("urlValue", PropertyNamingStrategy.LOWER_CAMEL.translate("URLValue"))
        assertEquals("user", PropertyNamingStrategy.LOWER_CAMEL.translate("User"))
    }

    @Test
    fun snakeCaseAppliesToBeanPropertiesFieldsNestedAndCollections() {
        val node = mapper(PropertyNamingStrategy.SNAKE_CASE).toNode(namingBean()).asObject()

        assertEquals("小于", node["first_name"]?.asString())
        assertEquals("ming", node["last_name"]?.asString())
        assertEquals(18, node["user_age"]?.asNumber()?.toInt())
        assertEquals("field_value", node["public_field"]?.asString())
        assertEquals("acronym", node["url_value"]?.asString())

        val home = node["home_address"]?.asObject()
        assertEquals("杭州", home?.get("child_name")?.asString())
        assertEquals(310000, home?.get("zip_code")?.asNumber()?.toInt())

        val previous = node["previous_addresses"]?.asArray()
        assertEquals(2, previous?.size)
        assertEquals("上海", previous?.get(0)?.asObject()?.get("child_name")?.asString())

        assertEquals("小明", node["explicit_name"]?.asString())
        assertEquals("getter value", node["getter_explicit"]?.asString())

        assertFalse(node.containsKey("firstName"))
        assertFalse(node.containsKey("homeAddress"))
        assertFalse(node.containsKey("displayName"))
    }

    @Test
    fun kebabCaseAppliesToBeanAndNestedNames() {
        val node = mapper(PropertyNamingStrategy.KEBAB_CASE).toNode(namingBean()).asObject()

        assertEquals("小于", node["first-name"]?.asString())
        assertEquals("field_value", node["public-field"]?.asString())
        assertEquals("acronym", node["url-value"]?.asString())
        assertEquals("杭州", node["home-address"]?.asObject()?.get("child-name")?.asString())
        assertEquals(2, node["previous-addresses"]?.asArray()?.size)
        assertEquals("小明", node["explicit_name"]?.asString())

        assertFalse(node.containsKey("firstName"))
    }

    @Test
    fun upperCamelAppliesToBeanAndFieldNames() {
        val node = mapper(PropertyNamingStrategy.UPPER_CAMEL).toNode(namingBean()).asObject()

        assertEquals("小于", node["FirstName"]?.asString())
        assertEquals("ming", node["LastName"]?.asString())
        assertEquals("杭州", node["HomeAddress"]?.asObject()?.get("ChildName")?.asString())
        assertEquals("field_value", node["PublicField"]?.asString())
        assertEquals("acronym", node["UrlValue"]?.asString())
        assertEquals("小明", node["explicit_name"]?.asString())
        assertEquals("getter value", node["getter_explicit"]?.asString())

        assertFalse(node.containsKey("firstName"))
    }

    @Test
    fun lowerCamelAppliesToFieldAndSeparatedNames() {
        val node = mapper(PropertyNamingStrategy.LOWER_CAMEL).toNode(namingBean()).asObject()

        assertEquals("小于", node["firstName"]?.asString())
        assertEquals("field_value", node["publicField"]?.asString())
        assertEquals("acronym", node["urlValue"]?.asString())
        assertEquals("小明", node["explicit_name"]?.asString())
        assertFalse(node.containsKey("public_field"))
    }

    @Test
    fun namingStrategyRoundTripsThroughJson() {
        val mapper = mapper(PropertyNamingStrategy.SNAKE_CASE)
        val source = namingBean()

        val json = mapper.format(source)
        assertTrue(json.contains("\"first_name\""))
        assertTrue(json.contains("\"explicit_name\""))
        assertFalse(json.contains("\"firstName\""))

        val restored = mapper.parse(json, NamingBean::class.java)
        assertEquals(source.firstName, restored.firstName)
        assertEquals(source.lastName, restored.lastName)
        assertEquals(source.userAge, restored.userAge)
        assertEquals(source.homeAddress.childName, restored.homeAddress.childName)
        assertEquals(source.homeAddress.zipCode, restored.homeAddress.zipCode)
        assertEquals(source.previousAddresses.size, restored.previousAddresses.size)
        assertEquals(source.previousAddresses[0].childName, restored.previousAddresses[0].childName)
        assertEquals(source.displayName, restored.displayName)
        assertEquals(source.getterNamed, restored.getterNamed)
        assertEquals(source.public_field, restored.public_field)
        assertEquals(source.URLValue, restored.URLValue)
    }

    @Test
    fun builderConfigForwardsNamingStrategy() {
        val config = DocumentMapperConfig.builder()
            .namingStrategy(PropertyNamingStrategy.KEBAB_CASE)
            .build()
        val mapper = JsonMapper.builder().config(config).build()

        val node = mapper.toNode(namingBean()).asObject()
        assertEquals("小于", node["first-name"]?.asString())
        assertEquals("小明", node["explicit_name"]?.asString())
    }

    @Test
    fun documentPropertyAppliesStrategyAndPrefersAnnotation() {
        val firstNameField = NamingBean::class.java.getDeclaredField("firstName")
        val firstName = DefaultDocumentProperty(
            NamingBean::class.java,
            "firstName",
            String::class.java,
            listOf(firstNameField),
            emptyList(),
            PropertyNamingStrategy.SNAKE_CASE,
        )
        assertEquals("firstName", firstName.name)
        assertEquals("first_name", firstName.documentName)

        val displayNameField = NamingBean::class.java.getDeclaredField("displayName")
        val displayName = DefaultDocumentProperty(
            NamingBean::class.java,
            "displayName",
            String::class.java,
            listOf(displayNameField),
            emptyList(),
            PropertyNamingStrategy.SNAKE_CASE,
        )
        assertEquals("explicit_name", displayName.documentName)
    }

    @Test
    fun kotlinDataClassUsesNamingStrategy() {
        val mapper = mapper(PropertyNamingStrategy.SNAKE_CASE)
        val holder = KotlinNamingHolder("小于", 3, listOf(KotlinNamingChild("小明", "C1")))
        val node = mapper.toNode(holder).asObject()

        assertEquals("小于", node["user_name"]?.asString())
        assertEquals(3, node["item_count"]?.asNumber()?.toInt())
        val children = assertNotNull(node["children"]?.asArray())
        assertEquals("小明", children[0].asObject()["child_name"]?.asString())
        assertEquals("C1", children[0].asObject()["explicit_child"]?.asString())

        val restored = mapper.fromNode(node, KotlinNamingHolder::class.java)
        assertEquals(holder, restored)
    }

    private fun mapper(strategy: PropertyNamingStrategy): JsonMapper = JsonMapper(DocumentMapperConfig.builder().namingStrategy(strategy).build())

    private fun namingBean(): NamingBean = NamingBean().apply {
        firstName = "小于"
        lastName = "ming"
        userAge = 18
        homeAddress = NamingChild("杭州", 310000)
        previousAddresses = listOf(NamingChild("上海", 200000), NamingChild("北京", 100000))
        displayName = "小明"
        getterNamed = "getter value"
        public_field = "field_value"
        URLValue = "acronym"
    }
}

private data class KotlinNamingChild(
    var childName: String = "",
    @field:DocumentName("explicit_child")
    var childCode: String = "",
)

private data class KotlinNamingHolder(
    var userName: String = "",
    var itemCount: Int = 0,
    var children: List<KotlinNamingChild> = emptyList(),
)
