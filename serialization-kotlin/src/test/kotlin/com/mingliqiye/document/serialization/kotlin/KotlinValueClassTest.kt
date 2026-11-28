package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.annotation.DocumentDateTimeFormat
import com.mingliqiye.document.annotation.DocumentIgnore
import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kotlin value class 支持测试。
 *
 * 覆盖：data class 直接构造参数、可空、嵌套 data class、List/Map/Array、
 * 根对象、@DocumentName/@DocumentIgnore/@DocumentDateTimeFormat、未注册编解码器
 * 时的显式失败，以及不受支持的泛型 value class。
 */
class KotlinValueClassTest {

    private val mapper = JsonMapper.builder()
        .addModule(kotlinModule(UserId::class, Price::class, Score::class, DateLabel::class))
        .build()

    private val plainMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .build()

    @Test
    fun directValueClassParametersUseUnderlyingValue() {
        val value = ValueUser(UserId("u-1"), Price(1234L), Score(7))

        val json = mapper.format(value)
        assertEquals("""{"id":"u-1","price":1234,"score":7}""", json)
        assertEquals(value, mapper.parse(json, ValueUser::class.java))
        assertEquals(value, mapper.fromNode(mapper.toNode(value), ValueUser::class.java))
    }

    @Test
    fun directValueClassParametersNeedNoCodecRegistration() {
        val value = ValueUser(UserId("plain"), Price(9L), Score(3))

        val json = plainMapper.format(value)
        assertEquals("""{"id":"plain","price":9,"score":3}""", json)
        assertEquals(value, plainMapper.parse(json, ValueUser::class.java))
    }

    @Test
    fun nullableValueClassParameters() {
        val value = NullableValueUser(UserId("u-2"), score = null)
        val json = mapper.format(value)
        assertEquals("""{"id":"u-2","score":null}""", json)
        assertEquals(value, mapper.parse(json, NullableValueUser::class.java))

        val missing = mapper.parse("""{"id":"u-3"}""", NullableValueUser::class.java)
        assertNull(missing.score)
        assertEquals(UserId("u-3"), missing.id)
    }

    @Test
    fun valueClassInCollectionsMapAndArray() {
        val value = ValueCollections(
            ids = listOf(UserId("a"), UserId("b")),
            prices = mapOf("x" to Price(100L), "y" to Price(200L)),
            scores = arrayOf(Score(1), Score(2)),
        )

        val json = mapper.format(value)
        assertEquals("""{"ids":["a","b"],"prices":{"x":100,"y":200},"scores":[1,2]}""", json)

        val restored = mapper.parse(json, ValueCollections::class.java)
        assertEquals(value.ids, restored.ids)
        assertEquals(value.prices, restored.prices)
        assertTrue(value.scores.contentEquals(restored.scores))
        assertEquals(value, mapper.fromNode(mapper.toNode(value), ValueCollections::class.java))
    }

    @Test
    fun topLevelValueClassRoundTrip() {
        val id = UserId("root")
        assertEquals("\"root\"", mapper.format(id))
        assertEquals(id, mapper.parse("\"root\"", UserId::class.java))
        assertEquals(id, mapper.fromNode(mapper.toNode(id), UserId::class.java))
        assertEquals(id, mapper.convert(mapper.toNode(id), UserId::class.java))
    }

    @Test
    fun topLevelGenericCollectionsOfValueClasses() {
        val ids = listOf(UserId("a"), UserId("b"))
        val node = mapper.toNode(ids)
        assertEquals("""["a","b"]""", mapper.format(ids))

        val restored: List<UserId> = mapper.fromNode(node, DocumentTypeReference.listType(UserId::class.java))
        assertEquals(ids, restored)

        val taken: List<UserId> = mapper.parse("""["a","b"]""", DocumentTypeReference.listType(UserId::class.java))
        assertEquals(ids, taken)

        val scored: List<Score> = mapper.parse("""[1,2,3]""", DocumentTypeReference.listType(Score::class.java))
        assertEquals(listOf(Score(1), Score(2), Score(3)), scored)
    }

    @Test
    fun nestedDataClassesKeepValueClassSupport() {
        val value = ValueNested(inner = ValueUser(UserId("n"), Price(1L), Score(2)))
        val json = mapper.format(value)
        assertEquals("""{"inner":{"id":"n","price":1,"score":2}}""", json)
        assertEquals(value, mapper.parse(json, ValueNested::class.java))
    }

    @Test
    fun documentNameOnValueClassParameterIsSymmetric() {
        val value = Renamed(UserId("renamed"))
        val node = mapper.toNode(value).asObject()
        assertEquals("uid", node.keys.single())
        assertEquals("renamed", node["uid"]?.asString())

        assertEquals(value, mapper.parse("""{"uid":"renamed"}""", Renamed::class.java))
        assertEquals(value, mapper.fromNode(ObjectNode.from(mapOf("uid" to node["uid"]!!)), Renamed::class.java))
    }

    @Test
    fun documentIgnoreOnValueClassParameter() {
        val value = Ignored(UserId("keep"), UserId("drop"))
        val node = mapper.toNode(value).asObject()
        assertEquals(setOf("id"), node.keys)
        assertEquals("keep", node["id"]?.asString())

        val restored = mapper.parse("""{"id":"keep"}""", Ignored::class.java)
        assertEquals(Ignored(UserId("keep"), UserId("default")), restored)
    }

    @Test
    fun dateTimeFormatOnValueClassParameter() {
        val value = Dated(DateLabel(LocalDate.of(2024, 5, 1)))
        val node = mapper.toNode(value).asObject()
        assertEquals("2024/05/01", node["date"]?.asString())

        assertEquals(value, mapper.parse("""{"date":"2024/05/01"}""", Dated::class.java))
    }

    @Test
    fun yamlMapperUsesValueClassSupport() {
        val yamlMapper = YamlMapper.builder()
            .addModule(kotlinModule(UserId::class, Price::class, Score::class, DateLabel::class))
            .build()
        val value = ValueUser(UserId("y"), Price(7L), Score(1))
        val yaml = yamlMapper.format(value)
        assertEquals(value, yamlMapper.parse(yaml, ValueUser::class.java))
    }

    @Test
    fun boxedValueClassWithoutCodecFailsExplicitly() {
        val json = """{"ids":["a"],"prices":{},"scores":[]}"""
        assertFailsWith<DocumentSerializationException> {
            plainMapper.parse(json, ValueCollections::class.java)
        }
        val listJson = """["a"]"""
        assertFailsWith<DocumentSerializationException> {
            plainMapper.parse(listJson, DocumentTypeReference.listType(UserId::class.java))
        }
    }

    @Test
    fun nullablePrimitiveValueClassParameterNeedsCodec() {
        val json = """{"id":"x","score":5}"""
        assertFailsWith<DocumentSerializationException> {
            plainMapper.parse(json, NullableValueUser::class.java)
        }
    }

    @Test
    fun nullablePrimitiveValueClassParameterRoundTripWithCodec() {
        val value = NullableValueUser(UserId("n"), Score(5))
        val json = mapper.format(value)
        assertEquals("""{"id":"n","score":5}""", json)
        assertEquals(value, mapper.parse(json, NullableValueUser::class.java))
    }

    @Test
    fun valueClassParametersWithDefaults() {
        assertEquals(ValueDefaults(), mapper.parse("{}", ValueDefaults::class.java))
        assertEquals(
            ValueDefaults(UserId("custom"), Score(2)),
            mapper.parse("""{"id":"custom","score":2}""", ValueDefaults::class.java),
        )
        val value = ValueDefaults()
        assertEquals(value, mapper.fromNode(mapper.toNode(value), ValueDefaults::class.java))
    }

    @Test
    fun nestedGenericValueClassCollections() {
        val value = NestedValueCollections(
            groups = listOf(listOf(UserId("a"), UserId("b")), emptyList()),
            priceMap = mapOf("p" to listOf(Price(1L))),
        )
        val json = mapper.format(value)
        assertEquals("""{"groups":[["a","b"],[]],"priceMap":{"p":[1]}}""", json)
        assertEquals(value, mapper.parse(json, NestedValueCollections::class.java))
    }

    @Test
    fun valueClassMapKeyIsNotSupported() {
        val exception = assertFailsWith<DocumentSerializationException> {
            mapper.parse(
                """{"k":"v"}""",
                DocumentTypeReference.mapType(UserId::class.java, String::class.java),
            )
        }
        assertTrue(exception.message.orEmpty().contains("map key"), exception.message)
    }

    @Test
    fun rootValueClassWithoutCodecFailsExplicitly() {
        assertFailsWith<DocumentSerializationException> {
            plainMapper.parse("\"x\"", UserId::class.java)
        }
        assertFailsWith<DocumentSerializationException> {
            plainMapper.fromNode(mapper.toNode(UserId("y")), UserId::class.java)
                .let { error("expected failure but was $it") }
        }
    }

    @Test
    fun genericValueClassIsRejectedAtRegistration() {
        val exception = assertFailsWith<DocumentSerializationException> {
            kotlinModule(GenericValue::class)
        }
        assertTrue(exception.message.orEmpty().contains("Generic value class"), exception.message)
    }

    @Test
    fun nonValueClassRegistrationIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            kotlinModule(ValueUser::class)
        }
    }
}

data class ValueUser(
    val id: UserId,
    val price: Price,
    val score: Score,
)

data class NullableValueUser(
    val id: UserId?,
    val score: Score? = null,
)

data class ValueDefaults(
    val id: UserId = UserId("default"),
    val score: Score = Score(1),
)

data class ValueCollections(
    val ids: List<UserId>,
    val prices: Map<String, Price>,
    val scores: Array<Score>,
) {
    override fun equals(other: Any?): Boolean = other is ValueCollections &&
        ids == other.ids &&
        prices == other.prices &&
        scores.contentEquals(other.scores)

    override fun hashCode(): Int = 31 * (31 * ids.hashCode() + prices.hashCode()) + scores.contentHashCode()
}

data class ValueNested(val inner: ValueUser)

data class NestedValueCollections(
    val groups: List<List<UserId>>,
    val priceMap: Map<String, List<Price>>,
)

data class Renamed(@DocumentName("uid") val id: UserId)

data class Ignored(
    val id: UserId,
    @DocumentIgnore val dropped: UserId = UserId("default"),
)

data class Dated(
    @field:DocumentDateTimeFormat("yyyy/MM/dd")
    val date: DateLabel,
)

@JvmInline
value class UserId(val value: String)

@JvmInline
value class Price(val cents: Long)

@JvmInline
value class Score(val points: Int)

@JvmInline
value class DateLabel(val value: LocalDate)

@JvmInline
value class GenericValue<T>(val value: T)
