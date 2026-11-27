package com.mingliqiye.document.fuzz

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.cbor.CborConfig
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.format.ion.IonConfig
import com.mingliqiye.document.format.ion.IonMapper
import org.junit.jupiter.api.Timeout
import java.util.Random
import kotlin.test.Test

/**
 * 新格式能力（CBOR tag / Ion 类型保真）畸形输入安全测试。
 */
@Timeout(30)
class NewFeatureFuzzTest {

    private val taggedCbor = CborMapper(CborConfig.builder().preserveTags(true).build())
    private val typedIon = IonMapper(IonConfig.builder().preserveTypes(true).build())

    @Test
    fun randomBinaryInputsDoNotEscapeAsFatalErrors() {
        val random = Random(0x7A6)
        repeat(200) {
            val bytes = ByteArray(random.nextInt(96)) { random.nextInt(256).toByte() }
            parseSafely { taggedCbor.parse(bytes, Node::class.java) }
        }
    }

    @Test
    fun randomTextInputsDoNotEscapeAsFatalErrors() {
        val random = Random(0x10F)
        repeat(150) {
            val text = buildString {
                repeat(random.nextInt(96)) {
                    append((' '.code + random.nextInt(95)).toChar())
                }
            }
            parseSafely { typedIon.parse(text, Node::class.java) }
        }
    }

    private fun parseSafely(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            // 解析器拒绝畸形输入是预期行为
        }
    }
}
