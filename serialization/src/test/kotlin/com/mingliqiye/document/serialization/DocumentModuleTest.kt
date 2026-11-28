package com.mingliqiye.document.serialization

import com.mingliqiye.document.annotation.DocumentName
import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [DocumentModule] / [ModuleSetupContext] / [DocumentMapperBuilder] 注册 API 审计测试。
 *
 * 证明：
 * - [ModuleSetupContext.addConverter]、[ModuleSetupContext.addSerializer]、
 *   [ModuleSetupContext.addDeserializer] 三种注册方式均可通过模块生效；
 * - [DocumentMapperBuilder.addModule] / [DocumentMapperBuilder.addModules] 生效，
 *   同名模块去重（后者覆盖）；
 * - 直接 Builder 注册与模块注册的优先级：模块注册覆盖直接注册，后注册模块覆盖先注册
 *   模块；
 * - [DocumentMapperBuilder.propertyMetadataResolver] 可设置属性元数据解析器，
 *   且模块通过 [ModuleSetupContext.setPropertyMetadataResolver] 注册的解析器优先。
 */
class DocumentModuleTest {

    @Test
    fun moduleRegistersConverter() {
        val mapper = JsonMapper.builder()
            .addModule(TokenModule)
            .build()

        val node = mapper.toNode(Token("abc"))
        assertEquals("ABC", node.asString())

        val restored = mapper.fromNode(node, Token::class.java)
        assertEquals(Token("ABC"), restored)
    }

    @Test
    fun moduleRegistersSerializerOnly() {
        val mapper = JsonMapper.builder()
            .addModule(SerializeOnlyModule)
            .build()

        assertEquals("serialized:x", mapper.toNode(SerializeOnly("x")).asString())
    }

    @Test
    fun moduleRegistersDeserializerOnly() {
        val mapper = JsonMapper.builder()
            .addModule(DeserializeOnlyModule)
            .build()

        val restored = mapper.fromNode(StringNode.of("7"), DeserializeOnly::class.java)
        assertEquals(DeserializeOnly(7), restored)

        // 只注册反序列化器时，序列化仍走内置 Bean 映射。
        assertIs<ObjectNode>(mapper.toNode(DeserializeOnly(7)))
    }

    @Test
    fun addModulesRegistersEveryModule() {
        val mapper = JsonMapper.builder()
            .addModules(SerializeOnlyModule, TokenModule)
            .build()

        assertEquals("serialized:x", mapper.toNode(SerializeOnly("x")).asString())
        assertEquals("ABC", mapper.toNode(Token("abc")).asString())
    }

    @Test
    fun moduleIsDeduplicatedByName() {
        val mapper = JsonMapper.builder()
            .addModules(TokenModule, TokenModule)
            .build()

        assertEquals("ABC", mapper.toNode(Token("abc")).asString())
    }

    @Test
    fun laterModuleWithSameNameOverridesEarlierModule() {
        val mapper = JsonMapper.builder()
            .addModules(PrefixTokenModule("first", "A:"), PrefixTokenModule("first", "B:"))
            .build()

        assertEquals("B:abc", mapper.toNode(Token("abc")).asString())
    }

    @Test
    fun moduleRegistrationOverridesDirectBuilderRegistration() {
        val mapper = JsonMapper.builder()
            .addConverter(PrefixTokenConverter("D:"))
            .addModule(PrefixTokenModule("module", "M:"))
            .build()

        assertEquals("M:abc", mapper.toNode(Token("abc")).asString())
        assertEquals(Token("M:abc"), mapper.fromNode(StringNode.of("abc"), Token::class.java))
    }

    @Test
    fun laterModuleOverridesEarlierModuleForSameType() {
        val mapper = JsonMapper.builder()
            .addModules(PrefixTokenModule("a", "A:"), PrefixTokenModule("b", "B:"))
            .build()

        assertEquals("B:abc", mapper.toNode(Token("abc")).asString())
    }

    @Test
    fun directRegistrationStillAppliesForOtherTypes() {
        val mapper = JsonMapper.builder()
            .addConverter(OtherTokenConverter())
            .addModule(PrefixTokenModule("module", "M:"))
            .build()

        assertEquals("other:x", mapper.toNode(OtherToken("x")).asString())
        assertEquals("M:abc", mapper.toNode(Token("abc")).asString())
    }

    @Test
    fun builderPropertyMetadataResolverAddsAnnotations() {
        val mapper = JsonMapper.builder()
            .propertyMetadataResolver(PrefixMetadataResolver("builder_name"))
            .build()

        val node = mapper.toNode(ResolvedBean().apply { name = "v" }).asObject()
        assertEquals("v", node["builder_name"]?.asString())
        assertTrue(!node.containsKey("name"))

        val restored = mapper.fromNode(ObjectNode.of("builder_name" to StringNode.of("v")), ResolvedBean::class.java)
        assertEquals("v", restored.name)
    }

    @Test
    fun moduleMetadataResolverOverridesBuilderResolver() {
        val mapper = JsonMapper.builder()
            .propertyMetadataResolver(PrefixMetadataResolver("builder_name"))
            .addModule(MetadataResolverModule)
            .build()

        val node = mapper.toNode(ResolvedBean().apply { name = "v" }).asObject()
        assertEquals("v", node["module_name"]?.asString())
    }

    private data class Token(val value: String)

    private class TokenConverter : DocumentBaseConverter<Token> {
        override fun getType(): DocumentTypeReference<Token> = DocumentTypeReference.of()

        override fun serialize(value: Token, context: SerializationContext): Node = StringNode.of(value.value.uppercase())

        override fun deserialize(node: Node, context: DeserializationContext): Token = Token(node.asString())
    }

    private object TokenModule : DocumentModule {
        override val moduleName: String = "token"

        override fun setupModule(context: ModuleSetupContext) {
            context.addConverter(TokenConverter())
        }
    }

    private class PrefixTokenConverter(
        private val prefix: String,
    ) : DocumentBaseConverter<Token> {
        override fun getType(): DocumentTypeReference<Token> = DocumentTypeReference.of()

        override fun serialize(value: Token, context: SerializationContext): Node = StringNode.of(prefix + value.value)

        override fun deserialize(node: Node, context: DeserializationContext): Token = Token(prefix + node.asString())
    }

    private class PrefixTokenModule(
        override val moduleName: String,
        private val prefix: String,
    ) : DocumentModule {
        override fun setupModule(context: ModuleSetupContext) {
            context.addConverter(PrefixTokenConverter(prefix))
        }
    }

    private data class OtherToken(val value: String)

    private class OtherTokenConverter : DocumentBaseConverter<OtherToken> {
        override fun getType(): DocumentTypeReference<OtherToken> = DocumentTypeReference.of()

        override fun serialize(value: OtherToken, context: SerializationContext): Node = StringNode.of("other:${value.value}")

        override fun deserialize(node: Node, context: DeserializationContext): OtherToken = OtherToken(node.asString().removePrefix("other:"))
    }

    private class SerializeOnly(val text: String)

    private class SerializeOnlySerializer : DocumentBaseSerializer<SerializeOnly> {
        override fun getType(): DocumentTypeReference<SerializeOnly> = DocumentTypeReference.of()

        override fun serialize(value: SerializeOnly, context: SerializationContext): Node = StringNode.of("serialized:${value.text}")
    }

    private object SerializeOnlyModule : DocumentModule {
        override val moduleName: String = "serialize-only"

        override fun setupModule(context: ModuleSetupContext) {
            context.addSerializer(SerializeOnlySerializer())
        }
    }

    private data class DeserializeOnly(val number: Int)

    private class DeserializeOnlyDeserializer : DocumentBaseDeserializer<DeserializeOnly> {
        override fun getType(): DocumentTypeReference<DeserializeOnly> = DocumentTypeReference.of()

        override fun deserialize(node: Node, context: DeserializationContext): DeserializeOnly = DeserializeOnly(node.asString().toInt())
    }

    private object DeserializeOnlyModule : DocumentModule {
        override val moduleName: String = "deserialize-only"

        override fun setupModule(context: ModuleSetupContext) {
            context.addDeserializer(DeserializeOnlyDeserializer())
        }
    }

    private class ResolvedBean {
        var name: String = ""
    }

    private class PrefixMetadataResolver(
        private val documentName: String,
    ) : PropertyMetadataResolver {
        override fun resolve(type: Class<*>, propertyName: String): List<Annotation>? = if (type == ResolvedBean::class.java && propertyName == "name") {
            listOf(DocumentName(documentName))
        } else {
            null
        }
    }

    private object MetadataResolverModule : DocumentModule {
        override val moduleName: String = "metadata-resolver"

        override fun setupModule(context: ModuleSetupContext) {
            context.setPropertyMetadataResolver(PrefixMetadataResolver("module_name"))
        }
    }
}
