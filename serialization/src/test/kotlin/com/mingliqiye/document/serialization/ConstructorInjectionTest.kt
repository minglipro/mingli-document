package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentCreator
import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [com.mingliqiye.document.annotation.DocumentCreator] 构造器注入测试。
 *
 * 覆盖不可变 Bean 读写往返、嵌套不可变 Bean、泛型集合/Map、缺失字段策略、
 * 参数名回退与缺失报错、多构造器校验，以及无注解 Bean 的原有行为不回归。
 */
class ConstructorInjectionTest {

    private val json = JsonMapper()

    @Test
    fun immutableBeanRoundTrip() {
        val source = immutableUser()

        val node = json.toNode(source).asObject()
        assertTrue(node.containsKey("display_name"), node.toString())
        assertTrue(node.containsKey("former_addresses"), node.toString())
        assertEquals("zip_code", node["address"]?.asObject()?.keys?.firstOrNull { it == "zip_code" })

        val text = json.format(source)
        val restored = json.parse(text, ImmutableUser::class.java)

        assertEquals(source.name, restored.name)
        assertEquals(source.age, restored.age)
        assertNotNull(restored.address)
        assertEquals("杭州", restored.address.city)
        assertEquals(310000, restored.address.zipCode)
        assertEquals(listOf("a", "b"), restored.tags)
        assertEquals(mapOf("math" to 95), restored.scores)
        assertNull(restored.nickname)
        assertNotNull(restored.formerAddresses)
        assertEquals(1, restored.formerAddresses.size)
        assertEquals("上海", restored.formerAddresses[0].city)
    }

    @Test
    fun immutableBeanFromNode() {
        val node = ObjectNode.of(
            "display_name" to StringNode.of("小于"),
            "age" to NumberNode.of(18),
            "address" to ObjectNode.of(
                "city" to StringNode.of("北京"),
                "zip_code" to NumberNode.of(100000),
            ),
            "tags" to ArrayNode.of(StringNode.of("a"), StringNode.of("b")),
            "scores" to ObjectNode.of("math" to NumberNode.of(95)),
            "former_addresses" to ArrayNode.of(
                ObjectNode.of(
                    "city" to StringNode.of("上海"),
                    "zip_code" to NumberNode.of(200000),
                ),
            ),
        )

        val user = json.fromNode(node, ImmutableUser::class.java)
        assertEquals("小于", user.name)
        assertEquals(18, user.age)
        assertNotNull(user.address)
        assertEquals(100000, user.address.zipCode)
        assertEquals(95, user.scores?.get("math"))
        assertNull(user.nickname)
        assertNotNull(user.formerAddresses)
        assertEquals(200000, user.formerAddresses[0].zipCode)
    }

    @Test
    fun nestedImmutableBean() {
        val address = json.parse("""{"city":"杭州","zip_code":310000}""", ImmutableAddress::class.java)
        assertEquals("杭州", address.city)
        assertEquals(310000, address.zipCode)

        val user = json.parse(
            """{"display_name":"小于","address":{"city":"杭州","zip_code":310000}}""",
            ImmutableUser::class.java,
        )
        assertEquals("小于", user.name)
        assertNotNull(user.address)
        assertEquals("杭州", user.address.city)
    }

    @Test
    fun missingFieldsKeepDefaultPolicy() {
        val user = json.parse("""{"display_name":"小于"}""", ImmutableUser::class.java)
        assertEquals("小于", user.name)
        assertEquals(0, user.age)
        assertNull(user.address)
        assertNull(user.tags)
        assertNull(user.scores)
        assertNull(user.nickname)
        assertNull(user.formerAddresses)
    }

    @Test
    fun missingFieldsSetNullPolicy() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.SET_NULL)
                .build(),
        )
        val user = mapper.parse("""{"display_name":"小于"}""", ImmutableUser::class.java)
        assertEquals("小于", user.name)
        assertEquals(0, user.age)
        assertNull(user.address)
        assertNull(user.tags)
        assertNull(user.nickname)
    }

    @Test
    fun missingFieldsSetEmptyPolicy() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.SET_EMPTY)
                .build(),
        )
        val user = mapper.parse("""{"display_name":"小于"}""", ImmutableUser::class.java)
        assertEquals("小于", user.name)
        assertEquals("", user.nickname)
        assertEquals(emptyList(), user.tags)
        assertEquals(emptyMap(), user.scores)
        assertEquals(emptyList(), user.formerAddresses)
        assertNull(user.address)
    }

    @Test
    fun missingFieldsFailPolicy() {
        val mapper = JsonMapper(
            DocumentMapperConfig.builder()
                .missingFieldPolicy(MissingFieldPolicy.FAIL)
                .build(),
        )
        val exception = assertFailsWith<DocumentSerializationException> {
            mapper.parse("""{"display_name":"小于"}""", ImmutableUser::class.java)
        }
        assertTrue(exception.message.orEmpty().contains("Missing field 'age'"), exception.message.orEmpty())
    }

    @Test
    fun unannotatedParameterUsesReflectionName() {
        val user = json.parse("""{"name":"小于","age":7}""", ReflectionNamedUser::class.java)
        assertEquals("小于", user.name)
        assertEquals(7, user.age)

        val text = json.format(ReflectionNamedUser("x", 1))
        assertTrue(text.contains("\"name\":\"x\""), text)
        assertTrue(text.contains("\"age\":1"), text)
    }

    @Test
    fun unnamedConstructorParameterThrows() {
        val model = compileJavaModel("UnnamedCreatorModel", UNNAMED_CREATOR_MODEL, parameterNames = false)
        val exception = assertFailsWith<DocumentSerializationException> {
            @Suppress("UNCHECKED_CAST")
            json.parse("""{"value":"x"}""", model as Class<Any>)
        }
        val message = exception.message.orEmpty()
        assertTrue(message.contains("UnnamedCreatorModel"), message)
        assertTrue(message.contains("parameter 0"), message)
        assertTrue(message.contains("@DocumentName"), message)
    }

    @Test
    fun multipleCreatorsAreRejected() {
        val model = compileJavaModel("MultipleCreatorModel", MULTIPLE_CREATOR_MODEL, parameterNames = true)
        val exception = assertFailsWith<DocumentSerializationException> {
            @Suppress("UNCHECKED_CAST")
            json.parse("""{"value":"x"}""", model as Class<Any>)
        }
        assertTrue(exception.message.orEmpty().contains("Multiple @DocumentCreator constructors"), exception.message.orEmpty())
    }

    @Test
    fun noCreatorKeepsSetterBasedDeserialization() {
        val user = json.parse(
            """{"name":"小于","age":18,"address":{"city":"杭州","zipCode":310000},"tags":["a"]}""",
            User::class.java,
        )
        assertEquals("小于", user.name)
        assertEquals(18, user.age)
        assertNotNull(user.address)
        assertEquals("杭州", user.address.city)
        assertEquals(listOf("a"), user.tags)

        val text = json.format(user)
        assertTrue(text.contains("\"name\":\"小于\""), text)
    }

    private fun immutableUser(): ImmutableUser = ImmutableUser(
        "小于",
        18,
        ImmutableAddress("杭州", 310000),
        listOf("a", "b"),
        mapOf("math" to 95),
        null,
        listOf(ImmutableAddress("上海", 200000)),
    )

    /**
     * 运行期编译一个测试模型，用于覆盖源码编译选项（`-parameters`）对参数名的影响。
     */
    private fun compileJavaModel(className: String, source: String, parameterNames: Boolean): Class<*> {
        val compiler = ToolProvider.getSystemJavaCompiler()
            ?: throw AssertionError("System Java compiler is unavailable; run tests on a JDK")
        val directory = Files.createTempDirectory("document-constructor-injection").toFile()
        try {
            val sourceFile = File(directory, "$className.java")
            sourceFile.writeText(source, Charsets.UTF_8)
            val diagnostics = DiagnosticCollector<JavaFileObject>()
            val fileManager = compiler.getStandardFileManager(diagnostics, null, Charsets.UTF_8)
            try {
                val options = mutableListOf(
                    "-classpath",
                    annotationClasspath(),
                    "-d",
                    directory.absolutePath,
                )
                if (parameterNames) {
                    options += "-parameters"
                }
                val units = fileManager.getJavaFileObjectsFromFiles(listOf(sourceFile))
                val compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call()
                if (!compiled) {
                    throw AssertionError(
                        "Failed to compile $className: " +
                            diagnostics.diagnostics.joinToString { it.getMessage(null) },
                    )
                }
            } finally {
                fileManager.close()
            }
            val loader = URLClassLoader(arrayOf(directory.toURI().toURL()), javaClass.classLoader)
            return Class.forName("$MODEL_PACKAGE.$className", true, loader)
        } finally {
            directory.deleteOnExit()
        }
    }

    private fun annotationClasspath(): String {
        val location = DocumentCreator::class.java.protectionDomain?.codeSource?.location
            ?: throw AssertionError("Cannot locate annotation classes on the test classpath")
        return File(location.toURI()).absolutePath
    }

    private companion object {
        private const val MODEL_PACKAGE = "com.mingliqiye.document.serialization"

        private val UNNAMED_CREATOR_MODEL = """
            package com.mingliqiye.document.serialization;

            import com.mingliqiye.document.annotation.DocumentCreator;

            public final class UnnamedCreatorModel {
                private final String value;

                @DocumentCreator
                public UnnamedCreatorModel(String value) {
                    this.value = value;
                }

                public String getValue() {
                    return value;
                }
            }
        """.trimIndent()

        private val MULTIPLE_CREATOR_MODEL = """
            package com.mingliqiye.document.serialization;

            import com.mingliqiye.document.annotation.DocumentCreator;
            import com.mingliqiye.document.annotation.DocumentName;

            public final class MultipleCreatorModel {
                private final String value;

                @DocumentCreator
                public MultipleCreatorModel(@DocumentName("value") String value) {
                    this.value = value;
                }

                @DocumentCreator
                public MultipleCreatorModel(
                        @DocumentName("value") String value,
                        @DocumentName("flag") int flag) {
                    this.value = value;
                }

                public String getValue() {
                    return value;
                }
            }
        """.trimIndent()
    }
}
