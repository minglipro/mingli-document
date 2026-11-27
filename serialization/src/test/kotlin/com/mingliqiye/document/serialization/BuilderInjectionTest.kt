package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [com.mingliqiye.document.annotation.DocumentBuilder] /
 * [com.mingliqiye.document.annotation.DocumentBuild] Builder 模式实例化测试。
 *
 * 覆盖 Builder Bean 读写往返、未标注 `@DocumentBuild` 时回退到 `build()`、
 * 嵌套 Builder Bean、与 `@DocumentCreator` 的优先级、缺少工厂 / 缺少构建方法的
 * 报错信息，以及普通 Bean 无参构造器路径不回归。
 */
class BuilderInjectionTest {

    private val json = JsonMapper()

    @Test
    fun builderBeanRoundTrip() {
        val source = BuilderUser.builder()
            .setName("小于")
            .setAge(18)
            .setAddress(BuilderAddress.builder().setCity("杭州").setZipCode(310000).build())
            .setTags(listOf("a", "b"))
            .setFormerAddresses(
                listOf(BuilderAddress.builder().setCity("上海").setZipCode(200000).build()),
            )
            .build()

        val text = json.format(source)
        assertTrue(text.contains("\"name\":\"小于\""), text)
        assertTrue(text.contains("\"zipCode\":310000"), text)

        val restored = json.parse(text, BuilderUser::class.java)
        assertEquals("小于", restored.name)
        assertEquals(18, restored.age)
        assertNotNull(restored.address)
        assertEquals("杭州", restored.address.city)
        assertEquals(310000, restored.address.zipCode)
        assertEquals(listOf("a", "b"), restored.tags)
        assertNotNull(restored.formerAddresses)
        assertEquals(1, restored.formerAddresses.size)
        assertEquals("上海", restored.formerAddresses[0].city)
        assertEquals(200000, restored.formerAddresses[0].zipCode)
    }

    @Test
    fun builderBeanFromNode() {
        val node = ObjectNode.of(
            "name" to StringNode.of("小于"),
            "age" to NumberNode.of(18),
            "address" to ObjectNode.of(
                "city" to StringNode.of("北京"),
                "zipCode" to NumberNode.of(100000),
            ),
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
        )

        val user = json.fromNode(node, BuilderUser::class.java)
        assertEquals("小于", user.name)
        assertEquals(18, user.age)
        assertNotNull(user.address)
        assertEquals("北京", user.address.city)
        assertEquals(100000, user.address.zipCode)
        assertEquals(listOf("a", "b"), user.tags)
        assertNull(user.formerAddresses)
    }

    @Test
    fun buildMethodFallback() {
        val user = json.parse("""{"value":"x"}""", BuildFallbackUser::class.java)
        assertEquals("x", user.value)

        val text = json.format(user)
        assertTrue(text.contains("\"value\":\"x\""), text)
        assertEquals("x", json.parse(text, BuildFallbackUser::class.java).value)
    }

    @Test
    fun nestedBuilderBean() {
        val address = json.parse("""{"city":"杭州","zipCode":310000}""", BuilderAddress::class.java)
        assertEquals("杭州", address.city)
        assertEquals(310000, address.zipCode)

        val user = json.parse(
            """{"name":"小于","address":{"city":"杭州","zipCode":310000},""" +
                """"formerAddresses":[{"city":"上海","zipCode":200000}]}""",
            BuilderUser::class.java,
        )
        assertEquals("小于", user.name)
        assertNotNull(user.address)
        assertEquals("杭州", user.address.city)
        assertNotNull(user.formerAddresses)
        assertEquals(1, user.formerAddresses.size)
        assertEquals("上海", user.formerAddresses[0].city)
    }

    @Test
    fun missingFieldsKeepBuilderDefaults() {
        val user = json.parse("""{"name":"小于"}""", BuilderUser::class.java)
        assertEquals("小于", user.name)
        assertEquals(0, user.age)
        assertNull(user.address)
        assertNull(user.tags)
        assertNull(user.formerAddresses)
    }

    @Test
    fun creatorTakesPrecedenceOverBuilder() {
        val parsed = json.parse("""{"value":"x"}""", CreatorAndBuilderModel::class.java)
        assertEquals("x", parsed.value)

        val roundTrip = json.parse(json.format(parsed), CreatorAndBuilderModel::class.java)
        assertEquals("x", roundTrip.value)
    }

    @Test
    fun missingBuilderFactoryReportsClass() {
        val exception = assertFailsWith<DocumentSerializationException> {
            json.parse("""{"value":"x"}""", NoBuilderFactoryModel::class.java)
        }
        val message = exception.message.orEmpty()
        assertTrue(message.contains("NoBuilderFactoryModel"), message)
        assertTrue(message.contains("@DocumentBuilder"), message)
    }

    @Test
    fun missingBuildMethodReportsClasses() {
        val exception = assertFailsWith<DocumentSerializationException> {
            json.parse("""{"value":"x"}""", MissingBuildMethodModel::class.java)
        }
        val message = exception.message.orEmpty()
        assertTrue(message.contains("MissingBuildMethodModel"), message)
        assertTrue(message.contains("Builder"), message)
        assertTrue(message.contains("build"), message)
        assertTrue(message.contains("@DocumentBuild"), message)
    }

    @Test
    fun plainBeanStillUsesNoArgConstructor() {
        val user = json.parse(
            """{"name":"小于","age":18,"address":{"city":"杭州","zipCode":310000},"tags":["a"]}""",
            User::class.java,
        )
        assertEquals("小于", user.name)
        assertEquals(18, user.age)
        assertNotNull(user.address)
        assertEquals("杭州", user.address.city)
        assertEquals(listOf("a"), user.tags)

        val text = json.format(user)
        assertTrue(text.contains("\"name\":\"小于\""), text)
    }
}
