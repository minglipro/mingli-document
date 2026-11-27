package com.mingliqiye.document.format.protobuf

import com.google.protobuf.DynamicMessage
import com.mingliqiye.document.format.protobuf.exception.ProtobufParseException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ProtobufDepthTest {

    @Test
    fun maxDepthRejectsNestedMessage() {
        val addressDescriptor = personDescriptor.findNestedTypeByName("Address")
        val addressField = personDescriptor.findFieldByName("addresses")
        val address = DynamicMessage.newBuilder(addressDescriptor)
            .setField(addressDescriptor.findFieldByName("city"), "Shanghai")
            .build()
        val person = DynamicMessage.newBuilder(personDescriptor)
            .setField(personDescriptor.findFieldByName("name"), "小于")
            .addRepeatedField(addressField, address)
            .build()

        assertFailsWith<ProtobufParseException> {
            ProtobufMapper(personDescriptor, ProtobufUnknownFieldPolicy.FAIL, maxDepth = 1)
                .parse(person.toByteArray(), com.mingliqiye.document.core.Node::class.java)
        }
        ProtobufMapper(personDescriptor, ProtobufUnknownFieldPolicy.FAIL, maxDepth = 2)
            .parse(person.toByteArray(), com.mingliqiye.document.core.Node::class.java)
    }
}
