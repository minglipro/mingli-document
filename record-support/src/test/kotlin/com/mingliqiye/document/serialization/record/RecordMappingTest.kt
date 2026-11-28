package com.mingliqiye.document.serialization.record

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MergePolicy
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Java record Bean 映射测试（从 `serialization` 模块迁出）。
 *
 * 本模块以 JDK 17 编译与运行，测试直接使用 `src/test/java` 下的真实 record 模型；
 * record 反射能力由本模块通过 [com.mingliqiye.document.serialization.JavaRecordSupport]
 * SPI 提供，`JsonMapper` 经 `ServiceLoader` 自动加载。
 *
 * 覆盖：组件访问器 `name()` 识别、`@DocumentName`/`@DocumentIgnore`、
 * canonical constructor 注入、显式 `@DocumentCreator` 优先、嵌套 record、
 * 文本与 Node 双路径往返、缺失字段策略、null 基本类型特性、命名策略与 merge。
 */
class RecordMappingTest {

    private val json = JsonMapper()

    @Test
    fun recordComponentsAreSerialized() {
        val node = json.toNode(recordUser()).asObject()

        assertEquals("小于", node["display_name"]?.asString())
        assertEquals(18, node["age"]?.asNumber()?.toInt())
        assertEquals("杭州", node["address"]?.asObject()?.get("city")?.asString())
        assertEquals(310000, node["address"]?.asObject()?.get("zipCode")?.asNumber()?.toInt())
        assertEquals(2, node["tags"]?.asArray()?.size)
        assertEquals(95, node["scores"]?.asObject()?.get("math")?.asNumber()?.toInt())
        assertFalse(node.containsKey("internalTag"), node.toString())
        assertTrue(node.containsKey("nickname"), node.toString())
        assertEquals(NodeType.NULL, node["nickname"]?.type)
    }

    @Test
    fun recordRoundTripThroughText() {
        val source = recordUser()

        val restored = json.parse(json.format(source), RecordUser::class.java)

        assertEquals("小于", restored.name())
        assertEquals(18, restored.age())
        assertEquals("杭州", restored.address().city())
        assertEquals(listOf("a", "b"), restored.tags())
        assertEquals(mapOf("math" to 95), restored.scores())
        assertNull(restored.nickname())
    }

    @Test
    fun recordRoundTripThroughNode() {
        val source = recordUser()

        val restored = json.fromNode(json.toNode(source), RecordUser::class.java)

        assertEquals("小于", restored.name())
        assertEquals(310000, restored.address().zipCode())
    }

    @Test
    fun recordMissingFieldsKeepDefault() {
        val restored = fromRecordNode(ObjectNode.of("display_name" to StringNode.of("x")))

        assertEquals("x", restored.name())
        assertEquals(0, restored.age())
        assertNull(restored.address())
    }

    @Test
    fun recordMissingFieldsFailPolicy() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.FAIL)
                .build(),
        )

        val exception = assertFailsWith<DocumentSerializationException> {
            mapper.parse("""{"display_name":"x"}""", RecordUser::class.java)
        }
        assertTrue(exception.message.orEmpty().contains("Missing field 'age'"), exception.message.orEmpty())
    }

    @Test
    fun recordNullPrimitiveUsesDefaultWhenFeatureDisabled() {
        val fromJson = json.parse("""{"display_name":"x","age":null}""", RecordUser::class.java)
        assertEquals("x", fromJson.name())
        assertEquals(0, fromJson.age())

        val fromNode = fromRecordNode(
            ObjectNode.of(
                "display_name" to StringNode.of("x"),
                "age" to NullNode.INSTANCE,
            ),
        )
        assertEquals(0, fromNode.age())
    }

    @Test
    fun recordNullPrimitiveFailsWhenFeatureEnabled() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .failOnNullForPrimitives(true)
                .build(),
        )

        val fromJson = assertFailsWith<DocumentSerializationException> {
            mapper.parse("""{"display_name":"x","age":null}""", RecordUser::class.java)
        }
        assertTrue(fromJson.message.orEmpty().contains("$.age"), fromJson.message.orEmpty())
        assertTrue(fromJson.message.orEmpty().contains("int"), fromJson.message.orEmpty())

        val fromNode = assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(
                ObjectNode.of(
                    "display_name" to StringNode.of("x"),
                    "age" to NullNode.INSTANCE,
                ),
                RecordUser::class.java,
            )
        }
        assertTrue(fromNode.message.orEmpty().contains("$.age"), fromNode.message.orEmpty())
        assertTrue(fromNode.message.orEmpty().contains("int"), fromNode.message.orEmpty())
    }

    @Test
    fun explicitDocumentCreatorRecordNullPrimitiveFailsWhenFeatureEnabled() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .failOnNullForPrimitives(true)
                .build(),
        )

        val exception = assertFailsWith<DocumentSerializationException> {
            mapper.parse("""{"record_id":"r1","record_count":null}""", AnnotatedRecord::class.java)
        }
        assertTrue(exception.message.orEmpty().contains("$.record_count"), exception.message.orEmpty())
        assertTrue(exception.message.orEmpty().contains("int"), exception.message.orEmpty())

        val disabled = json.parse("""{"record_id":"r1","record_count":null}""", AnnotatedRecord::class.java)
        assertEquals(0, disabled.count())
    }

    @Test
    fun ignoredRecordComponentIsNeverRequired() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.FAIL)
                .build(),
        )

        val restored = mapper.parse(
            """{"display_name":"x","age":1,"address":null,"tags":[],"scores":{},"nickname":null}""",
            RecordUser::class.java,
        )

        assertEquals("x", restored.name())
        assertNull(restored.internalTag())
    }

    @Test
    fun ignoredRecordComponentIsSkippedWithFailOnUnknownFields() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .failOnUnknownFields(true)
                .build(),
        )

        val restored = mapper.parse("""{"display_name":"x","age":1,"internalTag":"secret"}""", RecordUser::class.java)

        assertEquals("x", restored.name())
        assertNull(restored.internalTag())
        assertFalse(mapper.format(restored).contains("internalTag"))
    }

    @Test
    fun recordUsesExplicitDocumentCreatorConstructor() {
        val restored = json.parse("""{"record_id":"r1","record_count":3}""", AnnotatedRecord::class.java)

        assertEquals("r1", restored.id())
        assertEquals(3, restored.count())

        val text = json.format(restored)
        assertTrue(text.contains("\"record_id\":\"r1\""), text)
        assertTrue(text.contains("\"record_count\":3"), text)
    }

    @Test
    fun emptyRecordRoundTrip() {
        val node = json.toNode(EmptyRecord()).asObject()
        assertTrue(node.keys.isEmpty())

        val restored = json.fromNode(ObjectNode.EMPTY, EmptyRecord::class.java)
        assertEquals(EmptyRecord::class.java, restored.javaClass)
    }

    @Test
    fun recordComponentsUseNamingStrategy() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .build(),
        )
        val address = RecordAddress("杭州", 310000)

        val node = mapper.toNode(address).asObject()
        assertEquals("杭州", node["city"]?.asString())
        assertEquals(310000, node["zip_code"]?.asNumber()?.toInt())

        val restored = mapper.fromNode(node, RecordAddress::class.java)
        assertEquals(310000, restored.zipCode())
    }

    @Test
    fun recordMergePreservesAbsentComponents() {
        val source = recordUser()

        val merged = json.merge(ObjectNode.of("age" to NumberNode.of(20)), source)

        assertNotSame(source, merged)
        assertEquals(20, merged.age())
        assertEquals("小于", merged.name())
        assertEquals("杭州", merged.address().city())
        assertEquals(18, source.age())
    }

    @Test
    fun deepMergePreservesNestedRecordComponents() {
        val source = recordUser()

        val merged = json.merge(
            ObjectNode.of(
                "address" to ObjectNode.of("city" to StringNode.of("北京")),
            ),
            source,
            MergePolicy.DEEP,
        )

        assertEquals("北京", merged.address().city())
        assertEquals(310000, merged.address().zipCode())
        assertEquals(18, merged.age())
    }

    private fun recordUser(): RecordUser = RecordUser(
        "小于",
        18,
        RecordAddress("杭州", 310000),
        listOf("a", "b"),
        mapOf("math" to 95),
        "secret",
        null,
    )

    private fun fromRecordNode(node: Node): RecordUser = json.fromNode(node, RecordUser::class.java)
}
