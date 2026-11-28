package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentCreator
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import org.junit.jupiter.api.Assumptions
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Java record Bean 映射测试。
 *
 * 主代码与测试代码都按 JDK 8 目标编译，因此 record 模型不在源码中出现，而是在测试
 * 运行时用系统 Java 编译器动态编译并加载；本测试需要 JDK 16+ 运行时，默认的
 * `:serialization:test`（JDK 8 工具链）会跳过，使用 `:serialization:recordTest` 运行。
 *
 * 覆盖：组件访问器 `name()` 识别、`@DocumentName`/`@DocumentIgnore`、
 * canonical constructor 注入、显式 `@DocumentCreator` 优先、嵌套 record、
 * 文本与 Node 双路径往返、缺失字段策略、命名策略与 merge。
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

        val restored = parseRecord(json.format(source))

        assertEquals("小于", RecordModels.value(restored, "name"))
        assertEquals(18, RecordModels.value(restored, "age"))
        assertEquals("杭州", RecordModels.value(RecordModels.value(restored, "address"), "city"))
        assertEquals(listOf("a", "b"), RecordModels.value(restored, "tags"))
        assertEquals(mapOf("math" to 95), RecordModels.value(restored, "scores"))
        assertNull(RecordModels.value(restored, "nickname"))
    }

    @Test
    fun recordRoundTripThroughNode() {
        val source = recordUser()

        val restored = fromRecordNode(json.toNode(source))

        assertEquals("小于", RecordModels.value(restored, "name"))
        assertEquals(310000, RecordModels.value(RecordModels.value(restored, "address"), "zipCode"))
    }

    @Test
    fun recordMissingFieldsKeepDefault() {
        val restored = fromRecordNode(ObjectNode.of("display_name" to stringNode("x")))

        assertEquals("x", RecordModels.value(restored, "name"))
        assertEquals(0, RecordModels.value(restored, "age"))
        assertNull(RecordModels.value(restored, "address"))
    }

    @Test
    fun recordMissingFieldsFailPolicy() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.FAIL)
                .build(),
        )

        val exception = assertFailsWith<DocumentSerializationException> {
            mapper.parse("""{"display_name":"x"}""", recordClass(RecordModels.userType()))
        }
        assertTrue(exception.message.orEmpty().contains("Missing field 'age'"), exception.message.orEmpty())
    }

    @Test
    fun recordNullPrimitiveUsesDefaultWhenFeatureDisabled() {
        val fromJson = parseRecord("""{"display_name":"x","age":null}""")
        assertEquals("x", RecordModels.value(fromJson, "name"))
        assertEquals(0, RecordModels.value(fromJson, "age"))

        val fromNode = fromRecordNode(
            ObjectNode.of(
                "display_name" to stringNode("x"),
                "age" to NullNode.INSTANCE,
            ),
        )
        assertEquals(0, RecordModels.value(fromNode, "age"))
    }

    @Test
    fun recordNullPrimitiveFailsWhenFeatureEnabled() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .failOnNullForPrimitives(true)
                .build(),
        )

        val fromJson = assertFailsWith<DocumentSerializationException> {
            parseRecord("""{"display_name":"x","age":null}""", mapper = mapper)
        }
        assertTrue(fromJson.message.orEmpty().contains("$.age"), fromJson.message.orEmpty())
        assertTrue(fromJson.message.orEmpty().contains("int"), fromJson.message.orEmpty())

        val fromNode = assertFailsWith<DocumentSerializationException> {
            mapper.fromNode(
                ObjectNode.of(
                    "display_name" to stringNode("x"),
                    "age" to NullNode.INSTANCE,
                ),
                recordClass(RecordModels.userType()),
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
            mapper.parse("""{"record_id":"r1","record_count":null}""", recordClass(RecordModels.annotatedType()))
        }
        assertTrue(exception.message.orEmpty().contains("$.record_count"), exception.message.orEmpty())
        assertTrue(exception.message.orEmpty().contains("int"), exception.message.orEmpty())

        val disabled = parseRecord("""{"record_id":"r1","record_count":null}""", type = RecordModels.annotatedType())
        assertEquals(0, RecordModels.value(disabled, "count"))
    }

    @Test
    fun ignoredRecordComponentIsNeverRequired() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.FAIL)
                .build(),
        )

        val restored = parseRecord(
            """{"display_name":"x","age":1,"address":null,"tags":[],"scores":{},"nickname":null}""",
            mapper,
        )

        assertEquals("x", RecordModels.value(restored, "name"))
        assertNull(RecordModels.value(restored, "internalTag"))
    }

    @Test
    fun ignoredRecordComponentIsSkippedWithFailOnUnknownFields() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .failOnUnknownFields(true)
                .build(),
        )

        val restored = parseRecord("""{"display_name":"x","age":1,"internalTag":"secret"}""", mapper)

        assertEquals("x", RecordModels.value(restored, "name"))
        assertNull(RecordModels.value(restored, "internalTag"))
        assertFalse(mapper.format(restored).contains("internalTag"))
    }

    @Test
    fun recordUsesExplicitDocumentCreatorConstructor() {
        val type = RecordModels.annotatedType()

        val restored = parseRecord("""{"record_id":"r1","record_count":3}""", type = type)

        assertEquals("r1", RecordModels.value(restored, "id"))
        assertEquals(3, RecordModels.value(restored, "count"))

        val text = json.format(restored)
        assertTrue(text.contains("\"record_id\":\"r1\""), text)
        assertTrue(text.contains("\"record_count\":3"), text)
    }

    @Test
    fun emptyRecordRoundTrip() {
        val type = RecordModels.emptyType()
        val instance = RecordModels.newInstance(type)

        val node = json.toNode(instance).asObject()
        assertTrue(node.keys.isEmpty())

        val restored = fromRecordNode(ObjectNode.EMPTY, type = type)
        assertEquals(type, restored.javaClass)
    }

    @Test
    fun recordComponentsUseNamingStrategy() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .build(),
        )
        val addressType = RecordModels.addressType()
        val address = RecordModels.newInstance(addressType, "杭州", 310000)

        val node = mapper.toNode(address).asObject()
        assertEquals("杭州", node["city"]?.asString())
        assertEquals(310000, node["zip_code"]?.asNumber()?.toInt())

        val restored = mapper.fromNode(node, recordClass(addressType))
        assertEquals(310000, RecordModels.value(restored, "zipCode"))
    }

    @Test
    fun recordMergePreservesAbsentComponents() {
        val source = recordUser()

        val merged = json.merge(ObjectNode.of("age" to NumberNode.of(20)), source)

        assertNotSame(source, merged)
        assertEquals(20, RecordModels.value(merged, "age"))
        assertEquals("小于", RecordModels.value(merged, "name"))
        assertEquals("杭州", RecordModels.value(RecordModels.value(merged, "address"), "city"))
        assertEquals(18, RecordModels.value(source, "age"))
    }

    @Test
    fun deepMergePreservesNestedRecordComponents() {
        val source = recordUser()

        val merged = json.merge(
            ObjectNode.of(
                "address" to ObjectNode.of("city" to stringNode("北京")),
            ),
            source,
            MergePolicy.DEEP,
        )

        assertEquals("北京", RecordModels.value(RecordModels.value(merged, "address"), "city"))
        assertEquals(310000, RecordModels.value(RecordModels.value(merged, "address"), "zipCode"))
        assertEquals(18, RecordModels.value(merged, "age"))
    }

    private fun recordUser(): Any = RecordModels.newInstance(
        RecordModels.userType(),
        "小于",
        18,
        RecordModels.newInstance(RecordModels.addressType(), "杭州", 310000),
        listOf("a", "b"),
        mapOf("math" to 95),
        "secret",
        null,
    )

    private fun parseRecord(text: String, mapper: JsonMapper = json, type: Class<*> = RecordModels.userType()): Any = mapper.parse(text, recordClass(type))

    private fun fromRecordNode(node: Node, type: Class<*> = RecordModels.userType()): Any = json.fromNode(node, recordClass(type))

    private fun stringNode(value: String): StringNode = StringNode.of(value)

    private fun recordClass(type: Class<*>): Class<Any> {
        @Suppress("UNCHECKED_CAST")
        return type as Class<Any>
    }
}

/**
 * 运行期编译并加载 Java record 测试模型。
 *
 * record 语法要求 JDK 16+ 编译器，因此模型以源码字符串形式保存，测试运行时用
 * 当前 JVM 的 `ToolProvider` 编译；主代码与测试代码仍然只依赖 JDK 8 API。
 */
private object RecordModels {

    private const val PACKAGE = "com.mingliqiye.document.serialization.recordmodel"

    private val sources: Map<String, String> = linkedMapOf(
        "RecordAddress.java" to """
            package $PACKAGE;

            public record RecordAddress(String city, int zipCode) {
            }
        """.trimIndent(),
        "RecordUser.java" to """
            package $PACKAGE;

            import com.mingliqiye.document.annotation.DocumentIgnore;
            import com.mingliqiye.document.annotation.DocumentName;
            import java.util.List;
            import java.util.Map;

            public record RecordUser(
                    @DocumentName("display_name") String name,
                    int age,
                    RecordAddress address,
                    List<String> tags,
                    Map<String, Integer> scores,
                    @DocumentIgnore String internalTag,
                    String nickname) {
            }
        """.trimIndent(),
        "AnnotatedRecord.java" to """
            package $PACKAGE;

            import com.mingliqiye.document.annotation.DocumentCreator;
            import com.mingliqiye.document.annotation.DocumentName;

            public record AnnotatedRecord(String id, int count) {

                @DocumentCreator
                public AnnotatedRecord(
                        @DocumentName("record_id") String id,
                        @DocumentName("record_count") int count) {
                    this.id = id;
                    this.count = count;
                }
            }
        """.trimIndent(),
        "EmptyRecord.java" to """
            package $PACKAGE;

            public record EmptyRecord() {
            }
        """.trimIndent(),
    )

    private val classes: Map<String, Class<*>> by lazy { compile() }

    fun addressType(): Class<*> = classOf("RecordAddress")

    fun userType(): Class<*> = classOf("RecordUser")

    fun annotatedType(): Class<*> = classOf("AnnotatedRecord")

    fun emptyType(): Class<*> = classOf("EmptyRecord")

    fun newInstance(type: Class<*>, vararg arguments: Any?): Any {
        val constructor = type.declaredConstructors.firstOrNull { it.parameterCount == arguments.size }
            ?: throw AssertionError("No constructor with ${arguments.size} parameters on ${type.name}")
        constructor.isAccessible = true
        return constructor.newInstance(*arguments)
    }

    fun value(instance: Any?, accessor: String): Any? {
        if (instance == null) {
            throw AssertionError("Cannot read record component '$accessor' from null instance")
        }
        return try {
            instance.javaClass.getMethod(accessor).invoke(instance)
        } catch (e: ReflectiveOperationException) {
            throw AssertionError("Cannot read record component '$accessor' of ${instance.javaClass.name}", e)
        }
    }

    fun requireRecordRuntime() {
        val specification = System.getProperty("java.specification.version") ?: "1.8"
        val major = if (specification.startsWith("1.")) {
            specification.removePrefix("1.").toIntOrNull() ?: 8
        } else {
            specification.substringBefore('.').toIntOrNull() ?: 8
        }
        Assumptions.assumeTrue(major >= 16, "Java record tests require JDK 16+ runtime but was Java $specification")
    }

    private fun classOf(simpleName: String): Class<*> = classes[simpleName]
        ?: throw AssertionError("Missing compiled record model $simpleName")

    private fun compile(): Map<String, Class<*>> {
        requireRecordRuntime()
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: throw AssertionError("System Java compiler is unavailable; run record tests on a JDK 16+")
        val directory = Files.createTempDirectory("document-record-mapping").toFile()
        try {
            val sourceFiles = sources.map { (fileName, source) ->
                File(directory, fileName).apply { writeText(source, Charsets.UTF_8) }
            }
            val diagnostics = DiagnosticCollector<JavaFileObject>()
            val fileManager = compiler.getStandardFileManager(diagnostics, null, Charsets.UTF_8)
            try {
                val options = mutableListOf(
                    "-classpath",
                    annotationClasspath(),
                    "-d",
                    directory.absolutePath,
                    "-encoding",
                    "UTF-8",
                    "-parameters",
                )
                val units = fileManager.getJavaFileObjectsFromFiles(sourceFiles)
                val compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call()
                if (!compiled) {
                    throw AssertionError(
                        "Failed to compile record models: " +
                            diagnostics.diagnostics.joinToString { it.getMessage(null) },
                    )
                }
            } finally {
                fileManager.close()
            }
            val loader = URLClassLoader(arrayOf(directory.toURI().toURL()), RecordModels::class.java.classLoader)
            return sources.keys.associate { fileName ->
                val simpleName = fileName.removeSuffix(".java")
                simpleName to Class.forName("$PACKAGE.$simpleName", true, loader)
            }
        } finally {
            directory.deleteOnExit()
        }
    }

    private fun annotationClasspath(): String {
        val location = DocumentCreator::class.java.protectionDomain?.codeSource?.location
            ?: throw AssertionError("Cannot locate annotation classes on the test classpath")
        return File(location.toURI()).absolutePath
    }
}
