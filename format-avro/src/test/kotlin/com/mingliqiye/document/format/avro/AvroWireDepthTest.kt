package com.mingliqiye.document.format.avro

import com.mingliqiye.document.format.avro.exception.AvroParseException
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.EncoderFactory
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AvroWireDepthTest {

    @Test
    fun maxDepthRejectsNestedRecord() {
        val childSchema = Schema.createRecord("Child", null, "depthtest", false).apply {
            fields = listOf(Schema.Field("name", Schema.create(Schema.Type.STRING), null, null))
        }
        val parentSchema = Schema.createRecord("Parent", null, "depthtest", false).apply {
            fields = listOf(Schema.Field("child", childSchema, null, null))
        }
        val child = GenericData.Record(childSchema).apply { put("name", "deep") }
        val parent = GenericData.Record(parentSchema).apply { put("child", child) }
        val output = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().binaryEncoder(output, null)
        GenericDatumWriter<Any>(parentSchema).write(parent, encoder)
        encoder.flush()

        assertFailsWith<AvroParseException> {
            AvroWireReader(parentSchema, parentSchema, maxDepth = 1).read(output.toByteArray())
        }
        AvroWireReader(parentSchema, parentSchema, maxDepth = 2).read(output.toByteArray())
    }
}
