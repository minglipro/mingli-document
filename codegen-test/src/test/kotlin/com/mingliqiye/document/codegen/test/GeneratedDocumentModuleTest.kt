package com.mingliqiye.document.codegen.test

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.generated.GeneratedDocumentModule
import com.mingliqiye.document.generated.PetDocumentConverter
import com.mingliqiye.document.generated.PriorityDocumentConverter
import com.mingliqiye.document.generated.custom.ReviewCodec
import com.mingliqiye.document.serialization.DeserializationContext
import com.mingliqiye.document.serialization.DocumentMapperApi
import com.mingliqiye.document.serialization.DocumentProperty
import com.mingliqiye.document.serialization.SerializationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeneratedDocumentModuleTest {

    private val mapper: JsonMapper = JsonMapper.builder()
        .addModule(GeneratedDocumentModule())
        .build()

    @Test
    fun generatedConvertersExposeEnumTypes() {
        assertEquals(Pet::class.java, PetDocumentConverter().getType().getRawType())
        assertEquals(Priority::class.java, PriorityDocumentConverter().getType().getRawType())
        assertEquals(Review::class.java, ReviewCodec().getType().getRawType())
    }

    @Test
    fun generatedConverterSerializesEnumName() {
        val converter = PetDocumentConverter()
        val node: Node = converter.serialize(Pet.DOG, TestSerializationContext(mapper))
        assertEquals("DOG", node.asString())
    }

    @Test
    fun generatedConverterDeserializesKnownNameAndRejectsUnknownName() {
        val converter = PetDocumentConverter()
        val context = TestDeserializationContext(mapper)
        assertEquals(Pet.CAT, converter.deserialize(StringNode.of("CAT"), context))
        assertNull(converter.deserialize(StringNode.of("UNKNOWN"), context))
    }

    @Test
    fun generatedModuleRegistersEveryConverter() {
        val module = GeneratedDocumentModule()
        val context = RecordingModuleSetupContext()
        module.setupModule(context)

        assertEquals("GeneratedDocumentModule", module.moduleName)
        assertEquals(
            setOf(Pet::class.java, Priority::class.java, Review::class.java),
            context.converters.map { it.getType().getRawType() }.toSet(),
        )
        assertEquals(3, context.serializers.size)
        assertEquals(3, context.deserializers.size)
    }

    @Test
    fun jsonRoundTripUsesGeneratedConverters() {
        val petJson = mapper.format(Pet.DOG)
        assertEquals("\"DOG\"", petJson)
        assertEquals(Pet.DOG, mapper.parse(petJson, Pet::class.java))

        val priorityJson = mapper.format(Priority.HIGH)
        assertEquals("\"HIGH\"", priorityJson)
        assertEquals(Priority.HIGH, mapper.parse(priorityJson, Priority::class.java))

        val reviewJson = mapper.format(Review.REJECTED)
        assertEquals("\"REJECTED\"", reviewJson)
        assertEquals(Review.REJECTED, mapper.parse(reviewJson, Review::class.java))
    }

    @Test
    fun invalidEnumNameDeserializesToNull() {
        assertNull(mapper.fromNode(StringNode.of("UNKNOWN"), Pet::class.java))
        assertNull(mapper.parse("\"UNKNOWN\"", Pet::class.java))
    }
}

private class TestSerializationContext(
    override val mapper: DocumentMapperApi,
) : SerializationContext {

    override val property: DocumentProperty? = null

    override val path: String = "$"
}

private class TestDeserializationContext(
    override val mapper: DocumentMapperApi,
) : DeserializationContext {

    override val property: DocumentProperty? = null

    override val path: String = "$"
}
