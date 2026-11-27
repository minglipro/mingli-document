package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.annotation.DocumentTypeInfo
import com.mingliqiye.document.annotation.DocumentTypeName
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.json.toJsonString
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 对象身份引用序列化（`writeReferences`）测试。
 *
 * 覆盖：默认关闭、同一对象再次出现写 `$ref`、循环引用、数组/Map 内重复对象、
 * JSON Pointer 转义、关闭时循环检测保持原行为、嵌套 Bean、命名策略、多态、
 * 构造器注入/Builder 注入组合，以及与 `resolveReferences` 的读回组合。
 */
class ReferenceWriteTest {

    private fun referencingMapper(): JsonMapper = JsonMapper(
        DocumentMapperConfig.builder()
            .writeReferences(true)
            .build(),
    )

    private fun resolvingMapper(): JsonMapper = JsonMapper(
        DocumentMapperConfig.builder()
            .writeReferences(true)
            .resolveReferences(true)
            .build(),
    )

    @Test
    fun writeReferencesIsDisabledByDefault() {
        assertFalse(DocumentMapperConfig.builder().build().writeReferences)
        assertFalse(DocumentMapperConfig.empty().writeReferences)
        assertTrue(DocumentMapperConfig.builder().writeReferences(true).build().writeReferences)

        val shared = RefAddress("上海", 200000)
        val node = JsonMapper().toNode(
            RefOwner().apply {
                first = shared
                second = shared
            },
        ).asObject()

        assertEquals("上海", objectNode(node["first"])["city"]?.asString())
        assertEquals("上海", objectNode(node["second"])["city"]?.asString())
        assertNull(refValue(node["second"]))
    }

    @Test
    fun repeatedBeanBecomesReference() {
        val shared = RefAddress("上海", 200000)
        val mapper = referencingMapper()
        val owner = RefOwner().apply {
            first = shared
            second = shared
        }
        val node = mapper.toNode(owner).asObject()

        val first = objectNode(node["first"])
        assertEquals("上海", first["city"]?.asString())
        assertEquals(200000, first["zip"]?.asNumberOrNull()?.toInt())
        assertEquals("#/first", refValue(node["second"]))
    }

    @Test
    fun writtenTreeUsesJsonReferenceObjects() {
        val shared = RefAddress("上海", 200000)
        val repeated = referencingMapper().toNode(
            RefOwner().apply {
                first = shared
                second = shared
            },
        )
        assertEquals(
            """{"first":{"city":"上海","zip":200000},"second":{"${'$'}ref":"#/first"}}""",
            repeated.toJsonString(),
        )

        assertEquals(
            """{"b":{"a":{"${'$'}ref":"#"},"name":"B"},"name":"A"}""",
            referencingMapper().toNode(cycle()).toJsonString(),
        )
    }

    @Test
    fun reusedMapperDoesNotLeakReferenceStateBetweenCalls() {
        val shared = RefAddress("上海", 200000)
        val mapper = referencingMapper()
        val owner = RefOwner().apply {
            first = shared
            second = shared
        }
        val first = mapper.toNode(owner)
        val second = mapper.toNode(owner)

        assertEquals(first, second)
        assertEquals("#/first", refValue(second.asObject()["second"]))
    }

    @Test
    fun identicalButDistinctObjectsAreNotReferenced() {
        val first = EqualAddress("same", 1)
        val second = EqualAddress("same", 1)
        assertEquals(first, second)

        val node = referencingMapper()
            .toNode(linkedMapOf<String, Any>("left" to first, "right" to second))
            .asObject()

        assertNull(refValue(node["right"]))
        assertEquals("same", objectNode(node["right"])["city"]?.asString())
    }

    @Test
    fun repeatedObjectsInsideArrayAndMapUseReferences() {
        val mapper = referencingMapper()
        val shared = RefAddress("上海", 200000)

        val arrayNode = mapper.toNode(listOf(shared, shared)).asArray()
        assertEquals("上海", objectNode(arrayNode[0])["city"]?.asString())
        assertEquals("#/0", refValue(arrayNode[1]))

        val beanArray = mapper.toNode(arrayOf(shared, shared)).asArray()
        assertEquals("#/0", refValue(beanArray[1]))

        val intArray = intArrayOf(1, 2)
        val primitiveArrays = mapper.toNode(listOf<Any>(intArray, intArray)).asArray()
        assertEquals("#/0", refValue(primitiveArrays[1]))

        val mapNode = mapper
            .toNode(linkedMapOf<String, Any>("first" to shared, "second" to shared))
            .asObject()
        assertEquals("#/first", refValue(mapNode["second"]))
    }

    @Test
    fun jsonPointerTokensAreEscaped() {
        val shared = RefAddress("北京", 100000)
        val nested = linkedMapOf<String, Any>("x~y" to shared, "again" to shared)
        val node = referencingMapper().toNode(linkedMapOf<String, Any>("a/b" to nested)).asObject()

        val nestedNode = objectNode(node["a/b"])
        assertEquals("北京", objectNode(nestedNode["x~y"])["city"]?.asString())
        assertEquals("#/a~1b/x~0y", refValue(nestedNode["again"]))

        val resolved = resolvingMapper().fromNode(node, Map::class.java) as Map<*, *>
        val resolvedNested = objectNode(resolved["a/b"] as Node)
        assertEquals("北京", objectNode(resolvedNested["again"])["city"]?.asString())
    }

    @Test
    fun escapedBeanFieldNamesUseDocumentNamesInPointers() {
        val shared = RefAddress("广州", 510000)
        val node = referencingMapper()
            .toNode(
                EscapingOwner().apply {
                    slashName = shared
                    tildeName = shared
                },
            )
            .asObject()

        assertEquals("广州", objectNode(node["a/b"])["city"]?.asString())
        assertEquals("#/a~1b", refValue(node["x~y"]))

        val restored = resolvingMapper().fromNode(node, EscapingOwner::class.java)
        assertEquals("广州", restored.slashName?.city)
        assertEquals("广州", restored.tildeName?.city)
    }

    @Test
    fun scalarsEnumsAndBinaryDoNotParticipateInReferenceTracking() {
        val text = String(charArrayOf('t', 'e', 'x', 't'))
        val number = java.lang.Long.valueOf(123456789L)
        val binary = byteArrayOf(1, 2, 3)
        val node = referencingMapper()
            .toNode(
                listOf<Any>(
                    text,
                    text,
                    number,
                    number,
                    Status.ACTIVE,
                    Status.ACTIVE,
                    binary,
                    binary,
                ),
            )
            .asArray()

        assertEquals("text", node[0].asString())
        assertEquals("text", node[1].asString())
        assertEquals(123456789L, node[2].asNumber().toLong())
        assertEquals(123456789L, node[3].asNumber().toLong())
        assertEquals("ACTIVE", node[5].asString())
        assertTrue(node[6].asBinary().contentEquals(binary))
        assertTrue(node[7].asBinary().contentEquals(binary))
        for (index in 0 until node.size) {
            assertNull(refValue(node[index]), "index $index must not be a \$ref")
        }
    }

    @Test
    fun cyclicBeanGraphBecomesReferenceInsteadOfFailing() {
        val cyclic = cycle()

        assertFailsWith<DocumentSerializationException> { JsonMapper().toNode(cyclic) }

        val node = referencingMapper().toNode(cyclic).asObject()
        assertEquals("A", node["name"]?.asString())
        val bNode = objectNode(node["b"])
        assertEquals("B", bNode["name"]?.asString())
        assertEquals("#", refValue(bNode["a"]))
    }

    @Test
    fun cyclicReferencesResolveIntoFiniteStructure() {
        val cyclic = cycle()
        val mapper = resolvingMapper()
        val resolved = mapper.fromNode(mapper.toNode(cyclic), Map::class.java) as Map<*, *>

        assertEquals("A", (resolved["name"] as? Node)?.asString())
        val bNode = objectNode(resolved["b"] as Node)
        assertEquals("B", bNode["name"]?.asString())
        val expandedA = objectNode(bNode["a"])
        assertEquals("A", expandedA["name"]?.asString())
        val expandedB = objectNode(expandedA["b"])
        assertEquals("B", expandedB["name"]?.asString())
        assertEquals("#", refValue(expandedB["a"]))
    }

    @Test
    fun embeddedCyclicBeanReadsBackWithFiniteDepth() {
        val cyclic = cycle()
        val mapper = resolvingMapper()
        val restored = mapper.fromNode(mapper.toNode(cyclic), CycleA::class.java)

        assertEquals("A", restored.name)
        assertEquals("B", restored.b?.name)
        assertEquals("A", restored.b?.a?.name)
        assertEquals("B", restored.b?.a?.b?.name)
        assertNull(restored.b?.a?.b?.a?.name)
    }

    @Test
    fun selfReferencingContainerResolvesWithCycleMarker() {
        val selfMap = LinkedHashMap<String, Any?>()
        selfMap["name"] = "root"
        selfMap["self"] = selfMap
        val mapper = resolvingMapper()
        val node = mapper.toNode(selfMap).asObject()

        assertEquals("root", node["name"]?.asString())
        assertEquals("#", refValue(node["self"]))

        val resolved = mapper.fromNode(node, Map::class.java) as Map<*, *>
        val expanded = objectNode(resolved["self"] as Node)
        assertEquals("root", expanded["name"]?.asString())
        assertEquals("#", refValue(expanded["self"]))

        val selfArray = arrayOfNulls<Any>(1)
        selfArray[0] = selfArray
        val arrayNode = referencingMapper().toNode(selfArray).asArray()
        val element = objectNode(arrayNode[0])
        assertEquals("#", refValue(element))
    }

    @Test
    fun streamingWritePathKeepsOriginalCycleDetection() {
        val cyclic = cycle()
        val mapper = referencingMapper()

        assertFailsWith<DocumentSerializationException> { mapper.format(cyclic) }
        assertNotNull(mapper.toNode(cyclic))
    }

    @Test
    fun nestedBeanAndNamingStrategyKeepPointerConsistent() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .writeReferences(true)
                .resolveReferences(true)
                .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .build(),
        )
        val shared = RefAddress("深圳", 518000)
        val node = mapper.toNode(
            NamingOwner().apply {
                primaryAddress = shared
                secondaryAddress = shared
            },
        ).asObject()

        assertEquals("深圳", objectNode(node["primary_address"])["city"]?.asString())
        assertEquals("#/primary_address", refValue(node["secondary_address"]))

        val restored = mapper.fromNode(node, NamingOwner::class.java)
        assertEquals("深圳", restored.primaryAddress?.city)
        assertEquals("深圳", restored.secondaryAddress?.city)
    }

    @Test
    fun polymorphicRepeatedSubtypeResolvesBack() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .writeReferences(true)
                .resolveReferences(true)
                .subtype(RefPet::class.java, "cat", RefCat::class.java)
                .build(),
        )
        val cat = RefCat().apply {
            name = "Tom"
            lives = 8
        }
        val node = mapper.toNode(
            RefPetOwner().apply {
                pets = listOf(cat, cat, RefCat().apply { name = "Kit" })
            },
        )
        val pets = node.asObject()["pets"]!!.asArray()

        assertEquals("cat", objectNode(pets[0])["kind"]?.asString())
        assertEquals("Tom", objectNode(pets[0])["name"]?.asString())
        assertEquals("#/pets/0", refValue(pets[1]))
        assertEquals("Kit", objectNode(pets[2])["name"]?.asString())

        val restored = mapper.fromNode(node, RefPetOwner::class.java)
        val restoredPets = assertNotNull(restored.pets)
        assertEquals(3, restoredPets.size)
        assertTrue(restoredPets[1] is RefCat)
        assertEquals("Tom", (restoredPets[1] as RefCat).name)
        assertEquals(8, (restoredPets[1] as RefCat).lives)
    }

    @Test
    fun constructorInjectionBeanComposesWithReferences() {
        val shared = ImmutableAddress("杭州", 310000)
        val other = ImmutableAddress("南京", 210000)
        val user = ImmutableUser(
            "u",
            1,
            shared,
            emptyList(),
            emptyMap(),
            null,
            listOf(shared, other),
        )
        val node = resolvingMapper().toNode(user).asObject()

        assertEquals("杭州", objectNode(node["address"])["city"]?.asString())
        val formerAddresses = node["former_addresses"]!!.asArray()
        assertEquals("#/address", refValue(formerAddresses[0]))
        assertEquals("南京", objectNode(formerAddresses[1])["city"]?.asString())

        val restored = resolvingMapper().fromNode(node, ImmutableUser::class.java)
        assertEquals("杭州", restored.address?.city)
        assertEquals(2, restored.formerAddresses?.size)
        assertEquals("杭州", restored.formerAddresses?.get(0)?.city)
        assertEquals("南京", restored.formerAddresses?.get(1)?.city)
    }

    @Test
    fun builderInjectionBeanComposesWithReferences() {
        val shared = BuilderAddress.builder().setCity("成都").setZipCode(610000).build()
        val user = BuilderUser.builder()
            .setName("u")
            .setAge(2)
            .setAddress(shared)
            .setTags(listOf("t"))
            .setFormerAddresses(listOf(shared))
            .build()
        val node = resolvingMapper().toNode(user).asObject()

        assertEquals("成都", objectNode(node["address"])["city"]?.asString())
        assertEquals("#/address", refValue(node["formerAddresses"]!!.asArray()[0]))

        val restored = resolvingMapper().fromNode(node, BuilderUser::class.java)
        assertEquals("成都", restored.address?.city)
        assertEquals("成都", restored.formerAddresses?.get(0)?.city)
    }

    private fun cycle(): CycleA {
        val a = CycleA().apply { name = "A" }
        val b = CycleB().apply { name = "B" }
        a.b = b
        b.a = a
        return a
    }

    private fun objectNode(node: Node?): ObjectNode = assertNotNull(node as? ObjectNode)

    private fun refValue(node: Node?): String? = (node as? ObjectNode)?.get(REF_PROPERTY)?.asStringOrNull()

    private companion object {
        private const val REF_PROPERTY = "\$ref"
    }

    class RefAddress {
        var city: String? = null
        var zip: Int = 0

        constructor()

        constructor(city: String?, zip: Int) {
            this.city = city
            this.zip = zip
        }
    }

    class RefOwner {
        var first: RefAddress? = null
        var second: RefAddress? = null
    }

    data class EqualAddress(val city: String?, val zip: Int)

    class CycleA {
        var name: String? = null
        var b: CycleB? = null
    }

    class CycleB {
        var name: String? = null
        var a: CycleA? = null
    }

    class NamingOwner {
        var primaryAddress: RefAddress? = null
        var secondaryAddress: RefAddress? = null
    }

    class EscapingOwner {
        @field:DocumentName("a/b")
        var slashName: RefAddress? = null

        @field:DocumentName("x~y")
        var tildeName: RefAddress? = null
    }

    @DocumentTypeInfo(property = "kind")
    interface RefPet {
        var name: String?
    }

    @DocumentTypeName("cat")
    class RefCat : RefPet {
        override var name: String? = null
        var lives: Int = 0
    }

    class RefPetOwner {
        var pets: List<RefPet>? = null
    }
}
