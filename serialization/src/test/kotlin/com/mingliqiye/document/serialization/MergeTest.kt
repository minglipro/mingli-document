package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * [DocumentMapperApi.merge] 与 [MergePolicy] 测试。
 *
 * 覆盖：保留文档未出现的字段、嵌套 Bean 浅/深合并、`null` 覆盖与跳过、
 * 只读属性跳过、未知字段策略、不可变 Bean（构造器注入）重建、非法目标与补丁。
 */
class MergeTest {

    private val json = JsonMapper()

    @Test
    fun replacePolicyPreservesAbsentFields() {
        val target = user()

        val merged: User = json.merge(
            ObjectNode.of(
                "age" to NumberNode.of(20),
                "address" to ObjectNode.of("city" to StringNode.of("北京")),
            ),
            target,
        )

        assertSame(target, merged)
        assertEquals("小于", merged.name)
        assertEquals(20, merged.age)
        assertNotNull(merged.address)
        assertEquals("北京", merged.address.city)
        assertEquals(0, merged.address.zipCode)
        assertEquals(listOf("a", "b"), merged.tags)
    }

    @Test
    fun deepPolicyMergesNestedBeanInPlace() {
        val target = user()
        val address = target.address

        val merged = json.merge(
            ObjectNode.of(
                "address" to ObjectNode.of("city" to StringNode.of("北京")),
                "age" to NumberNode.of(20),
            ),
            target,
            MergePolicy.DEEP,
        )

        assertNotNull(merged.address)
        assertSame(address, merged.address)
        assertEquals("北京", merged.address.city)
        assertEquals(310000, merged.address.zipCode)
        assertEquals(20, merged.age)
    }

    @Test
    fun skipNullsPolicyKeepsExistingValues() {
        val target = user()

        json.merge(
            ObjectNode.of(
                "name" to NullNode.INSTANCE,
                "age" to NumberNode.of(21),
            ),
            target,
            MergePolicy.SKIP_NULLS,
        )

        assertEquals("小于", target.name)
        assertEquals(21, target.age)
    }

    @Test
    fun replacePolicyAppliesNull() {
        val target = user()

        json.merge(
            ObjectNode.of("name" to NullNode.INSTANCE),
            target,
            MergePolicy.REPLACE,
        )

        assertNull(target.name)
        assertEquals(18, target.age)
    }

    @Test
    fun deepSkipNullsPolicyCombinesBothBehaviors() {
        val target = user()

        json.merge(
            ObjectNode.of(
                "address" to ObjectNode.of(
                    "city" to NullNode.INSTANCE,
                    "zipCode" to NumberNode.of(100000),
                ),
            ),
            target,
            MergePolicy.DEEP_SKIP_NULLS,
        )

        assertNotNull(target.address)
        assertEquals("杭州", target.address.city)
        assertEquals(100000, target.address.zipCode)
    }

    @Test
    fun customPolicyCombinesFlags() {
        val target = user()
        val policy = MergePolicy.of(deepMerge = true, skipNulls = true)

        json.merge(
            ObjectNode.of(
                "name" to NullNode.INSTANCE,
                "address" to ObjectNode.of("city" to StringNode.of("北京")),
            ),
            target,
            policy,
        )

        assertEquals("小于", target.name)
        assertNotNull(target.address)
        assertEquals("北京", target.address.city)
        assertEquals(310000, target.address.zipCode)
    }

    @Test
    fun collectionsAndMapsAreReplaced() {
        val target = user()

        json.merge(
            ObjectNode.of(
                "tags" to ArrayNode.of(StringNode.of("c")),
                "scores" to ObjectNode.of("eng" to NumberNode.of(80)),
            ),
            target,
            MergePolicy.DEEP,
        )

        assertEquals(listOf("c"), target.tags)
        assertEquals(mapOf("eng" to 80), target.scores)
    }

    @Test
    fun readOnlyPropertiesAreSkipped() {
        val target = MergeReadOnlyBean().apply { name = "a" }

        json.merge(
            ObjectNode.of(
                "id" to NumberNode.of(9),
                "name" to StringNode.of("b"),
            ),
            target,
        )

        assertEquals("b", target.name)
        assertEquals(5, target.id)
    }

    @Test
    fun unknownFieldFailsWhenConfigured() {
        val strict = JsonMapper(
            DocumentMapperConfig.builder()
                .failOnUnknownFields(true)
                .build(),
        )

        assertFailsWith<DocumentSerializationException> {
            strict.merge(ObjectNode.of("unknown" to NumberNode.of(1)), user())
        }
    }

    @Test
    fun unknownFieldIsIgnoredByDefault() {
        val target = user()

        json.merge(ObjectNode.of("unknown" to NumberNode.of(1), "age" to NumberNode.of(20)), target)

        assertEquals(20, target.age)
    }

    @Test
    fun missingFieldFailPolicyDoesNotRejectPreservedFields() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.FAIL)
                .build(),
        )
        val target = user()

        val merged = mapper.merge(ObjectNode.of("age" to NumberNode.of(20)), target)

        assertEquals("小于", merged.name)
        assertEquals(20, merged.age)
    }

    @Test
    fun immutableCreatorBeanReturnsMergedCopy() {
        val target = immutableUser()

        val merged = json.merge(ObjectNode.of("age" to NumberNode.of(20)), target)

        assertNotSame(target, merged)
        assertEquals("小于", merged.name)
        assertEquals(20, merged.age)
        assertNotNull(merged.address)
        assertEquals("杭州", merged.address.city)
        assertEquals(listOf("a", "b"), merged.tags)
        assertEquals(18, target.age)
    }

    @Test
    fun deepPolicyMergesNestedCreatorBean() {
        val target = immutableUser()

        val merged = json.merge(
            ObjectNode.of("address" to ObjectNode.of("city" to StringNode.of("北京"))),
            target,
            MergePolicy.DEEP,
        )

        assertNotSame(target, merged)
        assertNotNull(merged.address)
        assertEquals("北京", merged.address.city)
        assertEquals(310000, merged.address.zipCode)
        assertEquals(18, merged.age)
    }

    @Test
    fun mergePatchMustBeObjectNode() {
        assertFailsWith<DocumentSerializationException> {
            json.merge(NumberNode.of(1), user())
        }
    }

    @Test
    fun mergeTargetMustNotBeNull() {
        assertFailsWith<DocumentSerializationException> {
            json.merge<Any?>(ObjectNode.EMPTY, null)
        }
    }

    @Test
    fun mergeNonBeanTargetFails() {
        assertFailsWith<DocumentSerializationException> {
            json.merge(ObjectNode.EMPTY, "text")
        }
        assertFailsWith<DocumentSerializationException> {
            json.merge(ObjectNode.EMPTY, mapOf("a" to 1))
        }
    }

    private fun user(): User = User().apply {
        name = "小于"
        age = 18
        address = Address("杭州", 310000)
        tags = listOf("a", "b")
        scores = mapOf("math" to 95)
        status = Status.ACTIVE
    }

    private fun immutableUser(): ImmutableUser = ImmutableUser(
        "小于",
        18,
        ImmutableAddress("杭州", 310000),
        listOf("a", "b"),
        mapOf("math" to 95),
        null,
        null,
    )
}

/**
 * 测试用只读 Bean：`id` 只有 getter，没有 setter。
 */
private class MergeReadOnlyBean {
    var name: String? = null

    val id: Int = 5
}
