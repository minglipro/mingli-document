package com.mingliqiye.document.format.avro

import com.mingliqiye.document.core.BinaryNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.avro.exception.AvroParseException
import com.mingliqiye.document.schema.SchemaType
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import org.apache.avro.Schema
import org.apache.avro.file.DataFileConstants
import org.apache.avro.file.DataFileReader
import org.apache.avro.file.SeekableByteArrayInput
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.EncoderFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AvroConfig] 与 上游实现 Avro 格式特性对齐测试。
 */
class AvroConfigTest {

    @Test
    fun configDefaultsMatchJacksonAvro() {
        val config = AvroConfig.builder().build()

        assertEquals(setOf(AvroReadFeature.AVRO_BUFFERING), config.readFeatures)
        assertEquals(setOf(AvroWriteFeature.AVRO_BUFFERING, AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA), config.writeFeatures)
        assertEquals(AvroConfig.DEFAULT_READ_FEATURES, config.readFeatures)
        assertEquals(AvroConfig.DEFAULT_WRITE_FEATURES, config.writeFeatures)

        assertTrue(config.isEnabled(AvroReadFeature.AVRO_BUFFERING))
        assertTrue(config.isEnabled(AvroWriteFeature.AVRO_BUFFERING))
        assertFalse(config.isEnabled(AvroWriteFeature.AVRO_FILE_OUTPUT))
        // 本项目为保持历史 schema 输出默认开启（上游实现 3.x 默认关闭，见文档化差异）
        assertTrue(config.isEnabled(AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA))

        assertTrue(config.avroBufferingOnRead)
        assertTrue(config.avroBufferingOnWrite)
        assertFalse(config.avroFileOutput)
        assertTrue(config.addNullAsDefaultValueInSchema)
        assertEquals(AvroCodec.NULL, config.codec)
        assertNull(config.schema)
        assertNull(config.readerSchema)
    }

    @Test
    fun builderEnableDisableConfigureFeatures() {
        val config = AvroConfig.builder()
            .disable(AvroReadFeature.AVRO_BUFFERING)
            .disable(AvroWriteFeature.AVRO_BUFFERING)
            .enable(AvroWriteFeature.AVRO_FILE_OUTPUT)
            .configure(AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA, false)
            .build()

        assertFalse(config.isEnabled(AvroReadFeature.AVRO_BUFFERING))
        assertFalse(config.avroBufferingOnRead)
        assertFalse(config.avroBufferingOnWrite)
        assertTrue(config.avroFileOutput)
        assertFalse(config.addNullAsDefaultValueInSchema)

        val replaced = AvroConfig.builder()
            .readFeatures(setOf(AvroReadFeature.AVRO_BUFFERING))
            .writeFeatures(setOf(AvroWriteFeature.AVRO_BUFFERING))
            .build()
        assertEquals(setOf(AvroReadFeature.AVRO_BUFFERING), replaced.readFeatures)
        assertEquals(setOf(AvroWriteFeature.AVRO_BUFFERING), replaced.writeFeatures)
        assertFalse(replaced.avroFileOutput)
    }

    @Test
    fun avroBufferingFeatureControlsEncoderAndDecoder() {
        val bufferedEncoder = AvroFeatureSupport.createEncoder(ByteArrayOutputStream(), true)
        val directEncoder = AvroFeatureSupport.createEncoder(ByteArrayOutputStream(), false)
        assertEquals("BufferedBinaryEncoder", bufferedEncoder.javaClass.simpleName)
        assertEquals("DirectBinaryEncoder", directEncoder.javaClass.simpleName)
        assertEquals("BinaryDecoder", AvroFeatureSupport.createBufferedDecoder(ByteArray(0)).javaClass.simpleName)
        assertEquals(
            "DirectBinaryDecoder",
            AvroFeatureSupport.createDirectDecoder(ByteArrayInputStream(ByteArray(0))).javaClass.simpleName,
        )

        val buffered = AvroMapper(userSchema, AvroConfig.builder().build())
        val directly = AvroMapper(
            userSchema,
            AvroConfig.builder()
                .disable(AvroReadFeature.AVRO_BUFFERING)
                .disable(AvroWriteFeature.AVRO_BUFFERING)
                .build(),
        )
        // 配置确实进入 Mapper 使用的裸 wire 读取/写出器（不是空开关）
        assertTrue(AvroFeatureSupport.wireWriter(userSchema, buffered.avroConfig).buffering)
        assertTrue(AvroFeatureSupport.wireReader(userSchema, userSchema, 0, buffered.avroConfig).buffering)
        assertFalse(AvroFeatureSupport.wireWriter(userSchema, directly.avroConfig).buffering)
        assertFalse(AvroFeatureSupport.wireReader(userSchema, userSchema, 0, directly.avroConfig).buffering)
        val node = userNode()
        val bufferedBytes = buffered.formatBytes(node)
        assertContentEquals(bufferedBytes, directly.formatBytes(node))
        assertEquals(node, buffered.parse(bufferedBytes, Node::class.java))
        assertEquals(node, directly.parse(bufferedBytes, Node::class.java))
        assertEquals(node, buffered.parse(directly.formatBytes(node), Node::class.java))

        // direct 解码路径同样校验空输入、截断与尾随字节
        assertFailsWith<AvroParseException> { directly.parse(ByteArray(0), Node::class.java) }
        assertFailsWith<AvroParseException> {
            directly.parse(bufferedBytes.copyOf(bufferedBytes.size - 1), Node::class.java)
        }
        assertFailsWith<AvroParseException> { directly.parse(bufferedBytes + byteArrayOf(0), Node::class.java) }
    }

    @Test
    fun avroBufferingDisabledWorksWithBytesFixedAndEnum() {
        val schema = schema(
            """
            {"type":"record","name":"Blob","fields":[
              {"name":"hash","type":{"type":"fixed","name":"Hash","size":4}},
              {"name":"data","type":"bytes"},
              {"name":"status","type":{"type":"enum","name":"Status","symbols":["NEW","DONE"]}}
            ]}
            """.trimIndent(),
        )
        val mapper = AvroMapper(
            schema,
            AvroConfig.builder()
                .disable(AvroReadFeature.AVRO_BUFFERING)
                .disable(AvroWriteFeature.AVRO_BUFFERING)
                .build(),
        )
        val node = ObjectNode.of(
            "hash" to BinaryNode.of(byteArrayOf(1, 2, 3, 4)),
            "data" to BinaryNode.of(byteArrayOf(9, 8, 7)),
            "status" to StringNode.of("DONE"),
        )

        assertEquals(node, mapper.parse(mapper.formatBytes(node), Node::class.java))
    }

    @Test
    fun avroFileOutputFeatureWritesObjectContainer() {
        val node = userNode()
        val raw = AvroMapper(userSchema)
        assertFalse(isOcf(raw.formatBytes(node)))

        val fileOutput = AvroMapper(
            userSchema,
            AvroConfig.builder().enable(AvroWriteFeature.AVRO_FILE_OUTPUT).build(),
        )
        val ocf = fileOutput.formatBytes(node)
        assertTrue(isOcf(ocf))
        assertEquals("null", containerCodecName(ocf))
        assertEquals(userSchema, containerSchema(ocf))
        assertEquals(listOf(node), fileOutput.parseContainerToNodes(ocf))
        assertEquals(1, readOcfRecords(ocf).size)
        // 与 上游实现 一致：读取端需显式使用容器 API，裸 parse 不接受 OCF
        assertFailsWith<AvroParseException> { fileOutput.parse(ocf, Node::class.java) }

        // format(value, OutputStream) / format(value, File) 由 formatBytes 驱动，同样写容器
        val output = ByteArrayOutputStream()
        fileOutput.format(node, output)
        assertTrue(isOcf(output.toByteArray()))
        assertEquals(listOf(node), fileOutput.parseContainerToNodes(output.toByteArray()))

        val file = Files.createTempFile("format-avro-file-output", ".avro").toFile()
        try {
            fileOutput.format(node, file)
            assertTrue(isOcf(file.readBytes()))
            assertEquals(listOf(node), fileOutput.parseContainerToNodes(file.readBytes()))
        } finally {
            file.delete()
        }

        // 配置 codec 作用于单记录容器
        val deflate = AvroMapper(
            userSchema,
            AvroConfig.builder()
                .enable(AvroWriteFeature.AVRO_FILE_OUTPUT)
                .codec(AvroCodec.DEFLATE)
                .build(),
        )
        assertEquals("deflate", containerCodecName(deflate.formatBytes(node)))

        // 关闭后回到裸 datum
        val disabled = AvroMapper(
            userSchema,
            AvroConfig.builder().disable(AvroWriteFeature.AVRO_FILE_OUTPUT).build(),
        )
        assertFalse(isOcf(disabled.formatBytes(node)))
        assertEquals(node, disabled.parse(disabled.formatBytes(node), Node::class.java))
    }

    @Test
    fun addNullAsDefaultValueInSchemaFeatureControlsGeneratedSchema() {
        val view = AvroSchema.builder("Simple")
            .field("id", SchemaType.LONG)
            .field("nick", SchemaType.STRING, required = false, nullable = true)
            .build()

        assertTrue(view.toAvroSchema().getField("nick").hasDefaultValue())
        assertFalse(view.toAvroSchema(false).getField("nick").hasDefaultValue())

        // 默认配置（本项目历史兼容）保留 null 默认值
        val defaultMapper = AvroMapper(view, AvroConfig.builder().build())
        assertTrue(defaultMapper.schema.getField("nick").hasDefaultValue())

        val noDefaultConfig = AvroConfig.builder()
            .disable(AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA)
            .build()
        val noDefaultMapper = AvroMapper(view, noDefaultConfig)
        assertFalse(noDefaultMapper.schema.getField("nick").hasDefaultValue())
        // 无默认值时仍可显式写 null（union 含 null 分支）
        val node = ObjectNode.of("id" to NumberNode.of(1L), "nick" to NullNode.INSTANCE)
        assertEquals(node, noDefaultMapper.parse(noDefaultMapper.formatBytes(node), Node::class.java))

        // AvroConfig 在 build 时按特性转换 AvroSchema 视图
        val configSchema = AvroConfig.builder()
            .schema(view)
            .disable(AvroWriteFeature.ADD_NULL_AS_DEFAULT_VALUE_IN_SCHEMA)
            .build()
            .schema
        assertFalse(configSchema!!.getField("nick").hasDefaultValue())

        // 构建器路径同样按配置转换
        assertFalse(
            AvroMapper.builder(view)
                .config(noDefaultConfig)
                .build()
                .schema
                .getField("nick")
                .hasDefaultValue(),
        )
        assertTrue(AvroMapper.builder(view).build().schema.getField("nick").hasDefaultValue())
    }

    @Test
    fun configCarriesSchemasForEvolution() {
        val config = AvroConfig.builder()
            .schema(idOnlySchema)
            .readerSchema(idNameSchema)
            .build()
        val mapper = AvroMapper(config)

        assertEquals(idOnlySchema, mapper.writerSchema)
        assertEquals(idNameSchema, mapper.readerSchema)
        assertEquals(idOnlySchema, mapper.schema)
        assertTrue(mapper.avroConfig.isEnabled(AvroWriteFeature.AVRO_BUFFERING))

        val bytes = mapper.formatBytes(ObjectNode.of("id" to NumberNode.of(7L)))
        val evolved = mapper.parse(bytes, Node::class.java).asObject()
        assertEquals(7L, evolved["id"]!!.asNumber().toLong())
        assertEquals("x", evolved["name"]!!.asString())

        // 显式 writer Schema + AvroConfig.readerSchema
        val explicit = AvroMapper(idOnlySchema, config)
        assertEquals(idNameSchema, explicit.readerSchema)
        val explicitEvolved = explicit.parse(
            explicit.formatBytes(ObjectNode.of("id" to NumberNode.of(1L))),
            Node::class.java,
        ).asObject()
        assertEquals("x", explicitEvolved["name"]!!.asString())

        // 配置缺 writer Schema 时无法创建 Mapper
        assertFailsWith<IllegalArgumentException> { AvroMapper(AvroConfig.builder().build()) }
    }

    @Test
    fun configCodecIsDefaultForContainerApis() {
        val mapper = AvroMapper(userSchema, AvroConfig.builder().codec(AvroCodec.DEFLATE).build())
        val nodes = listOf(userNode(1L), userNode(2L))

        val ocf = mapper.formatContainerBytes(nodes)
        assertEquals("deflate", containerCodecName(ocf))
        assertEquals(nodes, mapper.parseContainerToNodes(ocf))
        // 显式 codec 覆盖配置默认值
        assertEquals("null", containerCodecName(mapper.formatContainerBytes(nodes, AvroCodec.NULL)))

        val file = Files.createTempFile("format-avro-codec", ".avro").toFile()
        file.delete()
        try {
            mapper.appendContainer(file, listOf(userNode(3L)))
            assertEquals("deflate", containerCodecName(file.readBytes()))
        } finally {
            file.delete()
        }
    }

    @Test
    fun builderCreatesMapperFromConfigOrExplicitSchema() {
        val config = AvroConfig.builder()
            .schema(userSchema)
            .enable(AvroWriteFeature.AVRO_FILE_OUTPUT)
            .build()
        val fromConfig = AvroMapper.builder(config).build()
        assertEquals(userSchema, fromConfig.writerSchema)
        assertEquals(userSchema, fromConfig.readerSchema)
        assertTrue(isOcf(fromConfig.formatBytes(userNode())))

        val fromSchema = AvroMapper.builder().schema(userSchema).build()
        assertEquals(userSchema, fromSchema.writerSchema)
        assertFalse(isOcf(fromSchema.formatBytes(userNode())))

        val fromViews = AvroMapper.builder(AvroSchema.fromAvroSchema(userSchema)).build()
        assertEquals(userSchema, fromViews.writerSchema)

        val overridden = AvroMapper.builder(config).schema(idOnlySchema).readerSchema(idNameSchema).build()
        assertEquals(idOnlySchema, overridden.writerSchema)
        assertEquals(idNameSchema, overridden.readerSchema)

        assertFailsWith<IllegalArgumentException> { AvroMapper.builder().build() }
    }

    @Test
    fun configForwardsCommonDocumentMapperOptions() {
        val config = AvroConfig.builder()
            .missingFieldPolicy(MissingFieldPolicy.FAIL)
            .failOnUnknownFields(true)
            .writeNulls(false)
            .maxInputSize(64L)
            .maxStringLength(32)
            .maxNodeDepth(4)
            .maxNodeCount(16L)
            .resolveReferences(true)
            .writeReferences(true)
            .build()

        assertEquals(MissingFieldPolicy.FAIL, config.missingFieldPolicy)
        assertTrue(config.failOnUnknownFields)
        assertFalse(config.writeNulls)
        assertEquals(64L, config.maxInputSize)
        assertEquals(32, config.maxStringLength)
        assertEquals(4, config.maxNodeDepth)
        assertEquals(16L, config.maxNodeCount)
        assertTrue(config.resolveReferences)
        assertTrue(config.writeReferences)

        // maxInputSize 生效
        val sizeLimited = AvroMapper(userSchema, AvroConfig.builder().maxInputSize(1L).build())
        assertFailsWith<DocumentSerializationException> {
            sizeLimited.parse(AvroMapper(userSchema).formatBytes(userNode()), Node::class.java)
        }

        // maxNodeDepth 在 datum → Node 转换时生效
        val nestedSchema = schema(
            """
            {"type":"record","name":"Parent","fields":[
              {"name":"child","type":{"type":"record","name":"Child","fields":[
                {"name":"name","type":"string"}
              ]}}
            ]}
            """.trimIndent(),
        )
        val nestedBytes = rawDatumBytes(nestedSchema)
        assertFailsWith<AvroParseException> {
            AvroMapper(nestedSchema, AvroConfig.builder().maxNodeDepth(1).build())
                .parse(nestedBytes, Node::class.java)
        }
        // 深度足够时可解析（NodeLimits 从根节点计 1，Parent→Child→name 深度为 3）
        AvroMapper(nestedSchema, AvroConfig.builder().maxNodeDepth(3).build())
            .parse(nestedBytes, Node::class.java)

        // writeNulls(false) 经配置转发：Bean 的 null 字段不进入 Node
        val mapper = AvroMapper(
            userSchema,
            AvroConfig.builder().writeNulls(false).build(),
        )
        val bean = SimpleBean().apply {
            id = 1L
            name = "a"
            nick = null
        }
        assertFalse(mapper.toNode(bean).asObject().containsKey("nick"))
    }

    @Test
    fun plainDocumentMapperConfigKeepsAvroDefaults() {
        val mapper = AvroMapper(userSchema, DocumentMapperConfig.empty())

        assertEquals(AvroConfig.DEFAULT_READ_FEATURES, mapper.avroConfig.readFeatures)
        assertEquals(AvroConfig.DEFAULT_WRITE_FEATURES, mapper.avroConfig.writeFeatures)
        assertEquals(AvroCodec.NULL, mapper.avroConfig.codec)
        assertFalse(isOcf(mapper.formatBytes(userNode())))
        assertEquals("null", containerCodecName(mapper.formatContainerBytes(listOf(userNode()))))
    }

    private fun userNode(id: Long = 1L): ObjectNode = ObjectNode.of(
        "id" to NumberNode.of(id),
        "name" to StringNode.of("u$id"),
        "nick" to NullNode.INSTANCE,
    )

    private fun rawDatumBytes(schema: Schema): ByteArray {
        val child = GenericData.Record(schema.getField("child").schema()).apply { put("name", "deep") }
        val parent = GenericData.Record(schema).apply { put("child", child) }
        val output = ByteArrayOutputStream()
        val encoder = EncoderFactory.get().binaryEncoder(output, null)
        GenericDatumWriter<Any?>(schema).write(parent, encoder)
        encoder.flush()
        return output.toByteArray()
    }

    private fun isOcf(bytes: ByteArray): Boolean = bytes.size >= DataFileConstants.MAGIC.size &&
        bytes.copyOf(DataFileConstants.MAGIC.size).contentEquals(DataFileConstants.MAGIC)

    private fun containerCodecName(bytes: ByteArray): String? = openOcf(bytes) { reader -> reader.getMetaString(DataFileConstants.CODEC) }

    private fun containerSchema(bytes: ByteArray): Schema = openOcf(bytes) { reader -> reader.schema }

    private fun readOcfRecords(bytes: ByteArray): List<GenericRecord> {
        val records = ArrayList<GenericRecord>()
        DataFileReader<GenericRecord>(
            SeekableByteArrayInput(bytes),
            GenericDatumReader<GenericRecord>(),
        ).use { reader ->
            while (reader.hasNext()) {
                records.add(reader.next())
            }
        }
        return records
    }

    private fun <T> openOcf(bytes: ByteArray, block: (DataFileReader<GenericRecord>) -> T): T = DataFileReader<GenericRecord>(
        SeekableByteArrayInput(bytes),
        GenericDatumReader<GenericRecord>(),
    ).use(block)

    private fun schema(json: String): Schema = Schema.Parser().parse(json)

    private val userSchema: Schema = schema(
        """
        {"type":"record","name":"User","namespace":"com.example","fields":[
          {"name":"id","type":"long"},
          {"name":"name","type":"string"},
          {"name":"nick","type":["null","string"],"default":null}
        ]}
        """.trimIndent(),
    )

    private val idOnlySchema: Schema = schema(
        """
        {"type":"record","name":"User","namespace":"com.example","fields":[
          {"name":"id","type":"long"}
        ]}
        """.trimIndent(),
    )

    private val idNameSchema: Schema = schema(
        """
        {"type":"record","name":"User","namespace":"com.example","fields":[
          {"name":"id","type":"long"},
          {"name":"name","type":"string","default":"x"}
        ]}
        """.trimIndent(),
    )

    private class SimpleBean {
        var id: Long = 0
        var name: String? = null
        var nick: String? = null
    }
}
