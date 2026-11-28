package com.mingliqiye.document.format.protobuf

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ProtobufMapperFactoryTest {

    @Test
    fun reusesMapperPerDescriptor() {
        val factory = ProtobufMapperFactory()
        val sample = ProtobufTestFixtures.proto3Sample

        val first = factory.mapper(sample)
        val second = factory.mapper(sample)
        assertSame(first, second)
        assertNotSame(first, factory.mapper(ProtobufTestFixtures.extendedBase))
    }

    @Test
    fun sharedMapperRoundTrips() {
        val factory = ProtobufMapperFactory()
        val mapper = factory.mapper(ProtobufTestFixtures.extendedExtra)
        val node = ObjectNode.of("value" to StringNode.of("x"))

        val restored = mapper.parse(mapper.formatBytes(node), Node::class.java)
        assertEquals(node, restored)
    }
}
