package com.mingliqiye.document.fuzz

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.cbor.CborMapper
import com.mingliqiye.document.format.csv.CsvMapper
import com.mingliqiye.document.format.ion.IonMapper
import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.format.properties.PropertiesMapper
import com.mingliqiye.document.format.smile.SmileMapper
import com.mingliqiye.document.format.toml.TomlMapper
import com.mingliqiye.document.format.xml.XmlMapper
import com.mingliqiye.document.format.yaml.YamlMapper
import com.pholser.junit.quickcheck.From
import edu.berkeley.cs.jqf.fuzz.junit.quickcheck.InputStreamGenerator
import edu.berkeley.cs.jqf.junit5.FuzzTest
import java.io.InputStream

/**
 * JQF 覆盖率引导 fuzz 测试。
 *
 * 普通 `test` 任务排除本类；通过 `gradle :fuzz:fuzzJqf` 启用 JQF 模式。
 */
public class DocumentJqfTest {

    private val json = JsonMapper()
    private val yaml = YamlMapper()
    private val xml = XmlMapper()
    private val properties = PropertiesMapper()
    private val toml = TomlMapper()
    private val csv = CsvMapper()
    private val ion = IonMapper()
    private val cbor = CborMapper()
    private val smile = SmileMapper()

    @FuzzTest
    public fun json(@From(InputStreamGenerator::class) input: InputStream) {
        parseSafely { json.parse(input.readBytes(), Node::class.java) }
    }

    @FuzzTest
    public fun yaml(@From(InputStreamGenerator::class) input: InputStream) {
        parseSafely { yaml.parse(text(input), Node::class.java) }
    }

    @FuzzTest
    public fun xml(@From(InputStreamGenerator::class) input: InputStream) {
        parseSafely { xml.parse(text(input), Node::class.java) }
    }

    @FuzzTest
    public fun properties(@From(InputStreamGenerator::class) input: InputStream) {
        parseSafely { properties.parse(text(input), Node::class.java) }
    }

    @FuzzTest
    public fun toml(@From(InputStreamGenerator::class) input: InputStream) {
        parseSafely { toml.parse(text(input), Node::class.java) }
    }

    @FuzzTest
    public fun csv(@From(InputStreamGenerator::class) input: InputStream) {
        parseSafely { csv.parse(text(input), Node::class.java) }
    }

    @FuzzTest
    public fun ion(@From(InputStreamGenerator::class) input: InputStream) {
        parseSafely { ion.parse(input.readBytes(), Node::class.java) }
    }

    @FuzzTest
    public fun cbor(@From(InputStreamGenerator::class) input: InputStream) {
        parseSafely { cbor.parse(input.readBytes(), Node::class.java) }
    }

    @FuzzTest
    public fun smile(@From(InputStreamGenerator::class) input: InputStream) {
        parseSafely { smile.parse(input.readBytes(), Node::class.java) }
    }

    private fun text(input: InputStream): String = String(input.readBytes(), Charsets.UTF_8)

    private inline fun parseSafely(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}
