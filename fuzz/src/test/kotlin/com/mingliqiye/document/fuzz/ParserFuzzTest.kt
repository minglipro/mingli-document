package com.mingliqiye.document.fuzz

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
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
import org.junit.jupiter.api.Timeout
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 各解析器的边界语料与随机畸形输入测试。
 *
 * 目标不是校验具体异常类型，而是保证畸形输入不会导致进程崩溃、死循环、
 * StackOverflow 或不可控内存占用。
 */
@Timeout(30)
class ParserFuzzTest {

    private val json = JsonMapper()
    private val yaml = YamlMapper()
    private val xml = XmlMapper()
    private val properties = PropertiesMapper()
    private val toml = TomlMapper()
    private val csv = CsvMapper()
    private val ion = IonMapper()
    private val cbor = CborMapper()
    private val smile = SmileMapper()
    private val avro = AvroMapper(
        SchemaBuilder.record("Fuzz").fields().requiredInt("id").endRecord(),
    )
    private val protobuf = ProtobufMapper(protobufDescriptor())

    @Test
    fun corpusInputsNeverEscapeAsError() {
        val textParsers = mapOf(
            "json" to { source: String -> json.parse(source, Node::class.java) },
            "yaml" to { source: String -> yaml.parse(source, Node::class.java) },
            "xml" to { source: String -> xml.parse(source, Node::class.java) },
            "properties" to { source: String -> properties.parse(source, Node::class.java) },
            "toml" to { source: String -> toml.parse(source, Node::class.java) },
            "csv" to { source: String -> csv.parse(source, Node::class.java) },
            "ion" to { source: String -> ion.parse(source, Node::class.java) },
        )
        val binaryParsers = mapOf(
            "cbor" to { bytes: ByteArray -> cbor.parse(bytes, Node::class.java) },
            "smile" to { bytes: ByteArray -> smile.parse(bytes, Node::class.java) },
            "avro" to { bytes: ByteArray -> avro.parse(bytes, Node::class.java) },
            "protobuf" to { bytes: ByteArray -> protobuf.parse(bytes, Node::class.java) },
        )

        for ((format, parser) in textParsers) {
            for (path in corpusFiles(format)) {
                val source = String(resource(path), Charsets.UTF_8)
                parseSafely { parser(source) }
            }
        }
        for ((format, parser) in binaryParsers) {
            for (path in corpusFiles(format)) {
                val bytes = resource(path)
                parseSafely { parser(bytes) }
            }
        }
    }

    @Test
    fun randomBinaryInputsAreRejectedOrParsedSafely() {
        val random = Random(0x5EED)
        val parsers = listOf<(ByteArray) -> Any?>(
            { cbor.parse(it, Node::class.java) },
            { smile.parse(it, Node::class.java) },
            { avro.parse(it, Node::class.java) },
            { protobuf.parse(it, Node::class.java) },
        )
        var handled = 0
        repeat(200) {
            val bytes = ByteArray(random.nextInt(64)) { random.nextInt(256).toByte() }
            for (parser in parsers) {
                parseSafely {
                    parser(bytes)
                    handled++
                }
                handled++
            }
        }
        assertTrue(handled > 0)
    }

    @Test
    fun randomTextInputsAreRejectedOrParsedSafely() {
        val random = Random(0xC0FFEE)
        val parsers = listOf<(String) -> Any?>(
            { json.parse(it, Node::class.java) },
            { yaml.parse(it, Node::class.java) },
            { xml.parse(it, Node::class.java) },
            { properties.parse(it, Node::class.java) },
            { toml.parse(it, Node::class.java) },
            { csv.parse(it, Node::class.java) },
            { ion.parse(it, Node::class.java) },
        )
        var handled = 0
        repeat(150) {
            val text = buildString(random.nextInt(96)) {
                repeat(random.nextInt(96)) {
                    append((' '.code + random.nextInt(95)).toChar())
                }
            }
            for (parser in parsers) {
                parseSafely {
                    parser(text)
                    handled++
                }
                handled++
            }
        }
        assertTrue(handled > 0)
    }

    private fun parseSafely(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            // 解析器拒绝畸形输入是预期行为
        }
    }

    private fun corpusFiles(format: String): List<String> = javaClass.getResource("/corpus/$format")?.let { root ->
        root.protocol
        val dir = java.io.File(root.toURI())
        dir.listFiles()?.map { "$format/${it.name}" }.orEmpty()
    } ?: emptyList()

    private fun resource(path: String): ByteArray = javaClass.getResourceAsStream("/corpus/$path")?.use { it.readBytes() }
        ?: error("Missing corpus resource: $path")

    private companion object {

        private fun protobufDescriptor(): Descriptors.Descriptor {
            val field = DescriptorProtos.FieldDescriptorProto.newBuilder()
                .setName("id")
                .setNumber(1)
                .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                .build()
            val message = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Fuzz")
                .addField(field)
                .build()
            val file = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("fuzz.proto")
                .setPackage("fuzz")
                .setSyntax("proto3")
                .addMessageType(message)
                .build()
            return Descriptors.FileDescriptor.buildFrom(
                file,
                emptyArray<Descriptors.FileDescriptor>(),
            ).findMessageTypeByName("Fuzz")
        }
    }
}
