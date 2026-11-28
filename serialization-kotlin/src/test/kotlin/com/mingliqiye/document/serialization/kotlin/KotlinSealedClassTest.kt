package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.annotation.DocumentTypeInfo
import com.mingliqiye.document.annotation.DocumentTypeName
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.yaml.YamlMapper
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Kotlin sealed class / sealed interface 多态测试。
 *
 * 覆盖：sealed 子类自动注册、@DocumentTypeName/@DocumentTypeInfo、data class/
 * data object/object 子类、嵌套 sealed 层级、sealed interface、List 多态元素、
 * 根对象多态、value class 字段、多格式以及不受支持层级的显式失败。
 */
class KotlinSealedClassTest {

    private fun newMapper(): JsonMapper = JsonMapper.builder()
        .addModule(kotlinModule())
        .config(
            DocumentMapperConfig.builder()
                .kotlinSealedSubtypes(Payload::class, Marker::class, CustomPropertyBase::class)
                .build(),
        )
        .build()

    @Test
    fun sealedSubtypeBindingsDiscoverAllLeaves() {
        val bindings = kotlinSealedSubtypeBindings(Payload::class)
        val names = bindings.associate { binding -> binding.subtypeClass.simpleName to binding.typeName }

        assertEquals("text", names["TextPayload"])
        assertEquals("count", names["CountPayload"])
        assertEquals("EmptyPayload", names["EmptyPayload"])
        assertEquals("PingPayload", names["PingPayload"])
        assertEquals("NestedPayload", names["NestedPayload"])
        assertTrue(bindings.all { binding -> binding.baseClass == Payload::class.java })
    }

    @Test
    fun sealedDataClassSubtypesRoundTrip() {
        val mapper = newMapper()
        val payload: Payload = TextPayload("hello")

        val json = mapper.format(payload)
        assertTrue(json.contains("\"kind\":\"text\""), json)
        assertEquals(TextPayload("hello"), mapper.parse(json, Payload::class.java))

        val envelope = Envelope(payload = CountPayload(3, UserId("c-1")))
        val envelopeJson = mapper.format(envelope)
        assertTrue(envelopeJson.contains("\"kind\":\"count\""), envelopeJson)
        assertEquals(envelope, mapper.parse(envelopeJson, Envelope::class.java))
    }

    @Test
    fun sealedListElementsArePolymorphic() {
        val mapper = newMapper()
        val envelope = Envelope(
            payloads = listOf(
                TextPayload("a"),
                CountPayload(2, UserId("id")),
                EmptyPayload,
                PingPayload,
                NestedPayload("nested"),
            ),
        )

        val json = mapper.format(envelope)
        assertTrue(json.contains("\"kind\":\"text\""), json)
        assertTrue(json.contains("\"kind\":\"count\""), json)

        val restored = mapper.parse(json, Envelope::class.java)
        assertEquals(5, assertNotNull(restored.payloads).size)
        assertIs<TextPayload>(restored.payloads[0])
        assertIs<CountPayload>(restored.payloads[1])
        assertSame(EmptyPayload, restored.payloads[2])
        assertSame(PingPayload, restored.payloads[3])
        assertIs<NestedPayload>(restored.payloads[4])
        assertNull(restored.payload)
        assertNull(restored.marker)
    }

    @Test
    fun topLevelSealedSubtypeRoundTrip() {
        val mapper = newMapper()
        val node = mapper.toNode(TextPayload("root"))
        assertEquals("text", node.asObject()["kind"]?.asString())

        val json = mapper.format(TextPayload("root"))
        val restored = mapper.parse(json, Payload::class.java)
        assertEquals(TextPayload("root"), restored)
    }

    @Test
    fun sealedInterfaceSubtypesRoundTrip() {
        val mapper = newMapper()
        val node = mapper.toNode(MarkerA("a")).asObject()
        assertEquals("marker-a", node["kind"]?.asString())

        assertEquals(MarkerA("a"), mapper.parse("""{"kind":"marker-a","a":"a"}""", Marker::class.java))
        assertSame(MarkerNone, mapper.parse("""{"kind":"MarkerNone"}""", Marker::class.java))
    }

    @Test
    fun documentTypeInfoPropertyNameIsHonored() {
        val mapper = newMapper()
        val json = mapper.format(CustomPropertyImpl("v"))
        assertTrue(json.contains("\"payloadType\":\"CustomPropertyImpl\""), json)
        assertEquals(CustomPropertyImpl("v"), mapper.parse(json, CustomPropertyBase::class.java))
    }

    @Test
    fun missingAndUnknownTypePropertiesFailExplicitly() {
        val mapper = newMapper()

        val missing = assertFailsWith<DocumentSerializationException> {
            mapper.parse("""{"payload":{"text":"x"}}""", Envelope::class.java)
        }
        val missingMessage = assertNotNull(missing.message)
        assertTrue(missingMessage.contains("kind"), missingMessage)

        val unknown = assertFailsWith<DocumentSerializationException> {
            mapper.parse("""{"payload":{"kind":"nope","text":"x"}}""", Envelope::class.java)
        }
        val unknownMessage = assertNotNull(unknown.message)
        assertTrue(unknownMessage.contains("nope"), unknownMessage)
        assertTrue(unknownMessage.contains("text"), unknownMessage)
    }

    @Test
    fun unregisteredSubtypeFailsSerialization() {
        val mapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .config(
                DocumentMapperConfig.builder()
                    .subtype(Payload::class.java, "text", TextPayload::class.java)
                    .build(),
            )
            .build()

        val exception = assertFailsWith<DocumentSerializationException> {
            mapper.format(Envelope(payload = CountPayload(1, UserId("x"))))
        }
        assertTrue(
            assertNotNull(exception.message).contains(CountPayload::class.java.name),
            exception.message,
        )
    }

    @Test
    fun yamlMapperSupportsSealedSubtypes() {
        val mapper = YamlMapper.builder()
            .addModule(kotlinModule())
            .config(
                DocumentMapperConfig.builder()
                    .kotlinSealedSubtypes(Payload::class)
                    .build(),
            )
            .build()

        val yaml = mapper.format(TextPayload("yaml"))
        val restored = mapper.parse(yaml, Payload::class.java)
        assertEquals(TextPayload("yaml"), restored)
    }

    @Test
    fun sealedBuilderExtensionReturnsSameBuilder() {
        val builder = DocumentMapperConfig.builder()
        assertSame(builder, builder.kotlinSealedSubtypes(Payload::class))
    }

    @Test
    fun nestedSealedSubtypesAreDiscovered() {
        val mapper = newMapper()
        val bindings = kotlinSealedSubtypeBindings(Payload::class)
        assertTrue(bindings.any { it.subtypeClass == Payload.Bodyless::class.java && it.typeName == "Bodyless" })
        assertTrue(bindings.any { it.subtypeClass == Payload.Inner::class.java && it.typeName == "inner" })

        assertSame(Payload.Bodyless, mapper.parse("""{"kind":"Bodyless"}""", Payload::class.java))
        assertEquals(Payload.Inner("i"), mapper.parse("""{"kind":"inner","label":"i"}""", Payload::class.java))
    }

    @Test
    fun sealedBaseWithoutDocumentTypeInfoUsesDefaultTypeProperty() {
        val mapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .config(DocumentMapperConfig.builder().kotlinSealedSubtypes(PlainBase::class).build())
            .build()

        val json = mapper.format(PlainLeaf("x"))
        assertTrue(json.contains("\"type\":\"PlainLeaf\""), json)
        assertEquals(PlainLeaf("x"), mapper.parse(json, PlainBase::class.java))
    }

    @Test
    fun duplicateSimpleNamesFallBackToQualifiedNames() {
        val bindings = kotlinSealedSubtypeBindings(DuplicateBase::class)
        assertEquals(2, bindings.size)
        assertTrue(bindings.all { binding -> binding.typeName.contains('$') }, bindings.toString())

        val mapper = JsonMapper.builder()
            .addModule(kotlinModule())
            .config(DocumentMapperConfig.builder().kotlinSealedSubtypes(DuplicateBase::class).build())
            .build()
        val json = mapper.format(DuplicateHolderA.Leaf("a"))
        val restored = mapper.parse(json, DuplicateBase::class.java)
        assertEquals(DuplicateHolderA.Leaf("a"), restored)
    }

    @Test
    fun privateObjectSubtypeKeepsSingleton() {
        val mapper = newMapper()
        assertSame(HiddenPayload, mapper.parse("""{"kind":"HiddenPayload"}""", Payload::class.java))
    }

    @Test
    fun nonSealedBaseIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            kotlinSealedSubtypeBindings(ValueUser::class)
        }
    }

    @Test
    fun valueClassSubtypeIsRejectedExplicitly() {
        val exception = assertFailsWith<DocumentSerializationException> {
            kotlinSealedSubtypeBindings(ValueSealed::class)
        }
        assertTrue(exception.message.orEmpty().contains("value class"), exception.message)
    }

    @Test
    fun enumSubtypeIsRejectedExplicitly() {
        val exception = assertFailsWith<DocumentSerializationException> {
            kotlinSealedSubtypeBindings(EnumSealed::class)
        }
        assertTrue(exception.message.orEmpty().contains("enum"), exception.message)
    }

    @Test
    fun nonSealedAbstractIntermediateIsRejectedExplicitly() {
        val exception = assertFailsWith<DocumentSerializationException> {
            kotlinSealedSubtypeBindings(AbstractRoot::class)
        }
        assertTrue(exception.message.orEmpty().contains("cannot be discovered"), exception.message)
    }
}

@DocumentTypeInfo(property = "kind")
sealed class Payload {

    @DocumentTypeName("inner")
    data class Inner(val label: String) : Payload()

    data object Bodyless : Payload()
}

@DocumentTypeName("text")
data class TextPayload(val text: String) : Payload()

@DocumentTypeName("count")
data class CountPayload(val count: Int, val id: UserId) : Payload()

data object EmptyPayload : Payload()

data object PingPayload : Payload()

private object HiddenPayload : Payload()

sealed class NestedGroup : Payload()

data class NestedPayload(val value: String) : NestedGroup()

@DocumentTypeInfo(property = "kind")
sealed interface Marker

@DocumentTypeName("marker-a")
data class MarkerA(val a: String) : Marker

data object MarkerNone : Marker

@DocumentTypeInfo(property = "payloadType")
sealed interface CustomPropertyBase

data class CustomPropertyImpl(val v: String) : CustomPropertyBase

data class Envelope(
    val payload: Payload? = null,
    val payloads: List<Payload> = emptyList(),
    val marker: Marker? = null,
)

sealed interface ValueSealed

@JvmInline
value class ValueSealedImpl(val value: String) : ValueSealed

sealed interface EnumSealed

enum class EnumImpl : EnumSealed {
    A,
}

sealed class AbstractRoot

abstract class AbstractMiddle : AbstractRoot()

data class AbstractLeaf(val value: String) : AbstractMiddle()

sealed class PlainBase

data class PlainLeaf(val value: String) : PlainBase()

sealed class DuplicateBase

class DuplicateHolderA {
    data class Leaf(val a: String) : DuplicateBase()
}

class DuplicateHolderB {
    data class Leaf(val b: String) : DuplicateBase()
}
