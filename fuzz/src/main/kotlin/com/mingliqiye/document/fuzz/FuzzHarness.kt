package com.mingliqiye.document.fuzz

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.avro.AvroMapper
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.ion.IonMapper
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.properties.PropertiesMapper
import com.mingliqiye.document.format.protobuf.ProtobufMapper
import com.mingliqiye.document.format.smile.SmileMapper
import com.mingliqiye.document.format.toml.TomlMapper
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.yaml.YamlMapper
import org.apache.avro.SchemaBuilder
import java.util.Random
import kotlin.system.exitProcess

/**
 * 独立持续 fuzz 入口。
 *
 * 与 `gradle :fuzz:test` 的有限随机测试不同，本入口按时间持续生成畸形输入，
 * 直到达到时间预算；适合本地或 CI 手动任务长时间运行。
 *
 * 用法：
 *
 * ```bash
 * gradle :fuzz:fuzzContinuous -PfuzzSeconds=60
 * ```
 */
public fun main(args: Array<String>) {
    val seconds = args.firstOrNull()?.toIntOrNull()
        ?: System.getProperty("fuzz.seconds")?.toIntOrNull()
        ?: 10
    val deadline = System.currentTimeMillis() + seconds * 1000L
    val random = Random()
    val json = JsonMapper()
    val yaml = YamlMapper()
    val xml = XmlMapper()
    val properties = PropertiesMapper()
    val toml = TomlMapper()
    val csv = CsvMapper()
    val ion = IonMapper()
    val cbor = CborMapper()
    val smile = SmileMapper()
    val avro = AvroMapper(SchemaBuilder.record("Fuzz").fields().requiredInt("id").endRecord())
    val protobuf = ProtobufMapper(emptyProtobufDescriptor())
    var iterations = 0L
    while (System.currentTimeMillis() < deadline) {
        iterations++
        val bytes = ByteArray(random.nextInt(128)) { random.nextInt(256).toByte() }
        val text = buildString {
            repeat(random.nextInt(128)) {
                append((' '.code + random.nextInt(95)).toChar())
            }
        }
        val tasks = listOf<() -> Unit>(
            { json.parse(text, Node::class.java) },
            { yaml.parse(text, Node::class.java) },
            { xml.parse(text, Node::class.java) },
            { properties.parse(text, Node::class.java) },
            { toml.parse(text, Node::class.java) },
            { csv.parse(text, Node::class.java) },
            { ion.parse(text, Node::class.java) },
            { cbor.parse(bytes, Node::class.java) },
            { smile.parse(bytes, Node::class.java) },
            { avro.parse(bytes, Node::class.java) },
            { protobuf.parse(bytes, Node::class.java) },
        )
        for (task in tasks) {
            try {
                task()
            } catch (_: Exception) {
                // 解析器拒绝畸形输入是预期行为
            } catch (error: StackOverflowError) {
                System.err.println("FATAL: StackOverflowError after $iterations iterations")
                exitProcess(2)
            } catch (error: OutOfMemoryError) {
                System.err.println("FATAL: OutOfMemoryError after $iterations iterations")
                exitProcess(3)
            }
        }
    }
    println("fuzz completed: iterations=$iterations seconds=$seconds")
}

private fun emptyProtobufDescriptor(): com.google.protobuf.Descriptors.Descriptor {
    val field = com.google.protobuf.DescriptorProtos.FieldDescriptorProto.newBuilder()
        .setName("id")
        .setNumber(1)
        .setLabel(com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
        .setType(com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
        .build()
    val message = com.google.protobuf.DescriptorProtos.DescriptorProto.newBuilder()
        .setName("Fuzz")
        .addField(field)
        .build()
    val file = com.google.protobuf.DescriptorProtos.FileDescriptorProto.newBuilder()
        .setName("fuzz.proto")
        .setPackage("fuzz")
        .setSyntax("proto3")
        .addMessageType(message)
        .build()
    return com.google.protobuf.Descriptors.FileDescriptor.buildFrom(
        file,
        emptyArray(),
    ).findMessageTypeByName("Fuzz")!!
}
