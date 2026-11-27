package com.mingliqiye.document.serialization

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.format.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun moduleIsDeduplicatedByName() {
        val mapper = JsonMapper.builder()
            .addModules(TokenModule, TokenModule)
            .build()

        assertEquals("ABC", mapper.toNode(Token("abc")).asString())
    }

    private data class Token(val value: String)

    private class TokenConverter : JsonConverter<Token> {
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
}
