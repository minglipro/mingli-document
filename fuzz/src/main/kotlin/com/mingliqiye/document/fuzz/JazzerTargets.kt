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

/**
 * Jazzer 字节目标集合。
 *
 * 目标类名可通过 [JazzerTargets.TARGETS] 获取，供 Jazzer CLI 的
 * `--target_class` 参数使用。
 */
public object JazzerTargets {

    /** 全部目标类名。 */
    public val TARGETS: List<String> = listOf(
        "com.mingliqiye.document.fuzz.JsonJazzerTarget",
        "com.mingliqiye.document.fuzz.YamlJazzerTarget",
        "com.mingliqiye.document.fuzz.XmlJazzerTarget",
        "com.mingliqiye.document.fuzz.PropertiesJazzerTarget",
        "com.mingliqiye.document.fuzz.TomlJazzerTarget",
        "com.mingliqiye.document.fuzz.CsvJazzerTarget",
        "com.mingliqiye.document.fuzz.IonJazzerTarget",
        "com.mingliqiye.document.fuzz.CborJazzerTarget",
        "com.mingliqiye.document.fuzz.SmileJazzerTarget",
        "com.mingliqiye.document.fuzz.AvroJazzerTarget",
        "com.mingliqiye.document.fuzz.ProtobufJazzerTarget",
        "com.mingliqiye.document.fuzz.CborTagJazzerTarget",
        "com.mingliqiye.document.fuzz.IonFidelityJazzerTarget",
    )
}

public object JsonJazzerTarget {
    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            JsonMapper().parse(data, Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object YamlJazzerTarget {
    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            YamlMapper().parse(String(data, Charsets.UTF_8), Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object XmlJazzerTarget {
    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            XmlMapper().parse(String(data, Charsets.UTF_8), Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object PropertiesJazzerTarget {
    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            PropertiesMapper().parse(String(data, Charsets.UTF_8), Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object TomlJazzerTarget {
    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            TomlMapper().parse(String(data, Charsets.UTF_8), Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object CsvJazzerTarget {
    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            CsvMapper().parse(String(data, Charsets.UTF_8), Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object IonJazzerTarget {
    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            IonMapper().parse(data, Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object CborJazzerTarget {
    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            CborMapper().parse(data, Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object SmileJazzerTarget {
    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            SmileMapper().parse(data, Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object AvroJazzerTarget {
    private val mapper = AvroMapper(
        SchemaBuilder.record("Fuzz").fields().requiredInt("id").endRecord(),
    )

    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            mapper.parse(data, Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object CborTagJazzerTarget {
    private val mapper = CborMapper(
        com.mingliqiye.document.format.cbor.CborConfig.builder().preserveTags(true).build(),
    )

    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            mapper.parse(data, Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object IonFidelityJazzerTarget {
    private val mapper = IonMapper(
        com.mingliqiye.document.format.ion.IonConfig.builder().preserveTypes(true).build(),
    )

    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            mapper.parse(data, Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }
}

public object ProtobufJazzerTarget {
    private val mapper = ProtobufMapper(descriptor())

    @JvmStatic
    public fun fuzzerTestOneInput(data: ByteArray) {
        try {
            mapper.parse(data, Node::class.java)
        } catch (_: Exception) {
            // 畸形输入
        }
    }

    private fun descriptor(): Descriptors.Descriptor {
        val field = DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName("id")
            .setNumber(1)
            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
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
        return Descriptors.FileDescriptor.buildFrom(file, emptyArray()).findMessageTypeByName("Fuzz")!!
    }
}
