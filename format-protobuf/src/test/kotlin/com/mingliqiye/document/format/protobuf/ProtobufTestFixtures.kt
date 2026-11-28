package com.mingliqiye.document.format.protobuf

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.DescriptorProto.ExtensionRange
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.MessageOptions
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.UnknownFieldSet

/**
 * 高级语义测试使用的动态 Descriptor 工厂。
 *
 * 全部通过 [FileDescriptorProto] 手工构造，不依赖 protoc 与生成代码，覆盖 extension、group、
 * MessageSet 与 proto3 `optional` 等无法由现有 Schema 直接声明的描述符。
 */
internal object ProtobufTestFixtures {

    /**
     * proto2 文件，包含可扩展消息 [extendedBase]、extension 声明（含嵌套作用域 extension）与
     * 多个被扩展的消息。
     */
    val extendedFile: FileDescriptor = buildExtendedFile()

    /** proto2 可扩展消息 `ext.Base`。 */
    val extendedBase: Descriptor = requireNotNull(extendedFile.findMessageTypeByName("Base"))

    /** 扩展消息 `ext.Extra`。 */
    val extendedExtra: Descriptor = requireNotNull(extendedFile.findMessageTypeByName("Extra"))

    /** 含嵌套扩展消息的 `ext.Nested`。 */
    val extendedNested: Descriptor = requireNotNull(extendedFile.findMessageTypeByName("Nested"))

    /** `ext.Nested.Inner`。 */
    val extendedNestedInner: Descriptor = requireNotNull(extendedNested.findNestedTypeByName("Inner"))

    /** `ext.Base` 的全部 extension 字段，含嵌套作用域内声明的 extension。 */
    val extendedExtensions: List<FieldDescriptor> = ProtobufExtensionRegistry.builder()
        .addAll(extendedFile.extensions)
        .addAll(requireNotNull(extendedFile.findMessageTypeByName("A")).extensions)
        .addAll(requireNotNull(extendedFile.findMessageTypeByName("B")).extensions)
        .addAll(extendedNested.extensions)
        .build()
        .extensions

    /** 该文件全部 extension（含嵌套作用域）的注册表，默认全名命名。 */
    val extendedRegistry: ProtobufExtensionRegistry = ProtobufExtensionRegistry.builder()
        .addAll(extendedExtensions)
        .build()

    /** proto2 MessageSet 文件，包含 MessageSet 根消息、payload 消息与两个 extension。 */
    val messageSetFile: FileDescriptor = buildMessageSetFile()

    /** MessageSet 根消息 `ms.MessageSetRoot`。 */
    val messageSetRoot: Descriptor = requireNotNull(messageSetFile.findMessageTypeByName("MessageSetRoot"))

    /** MessageSet payload 消息 `ms.Payload`。 */
    val messageSetPayload: Descriptor = requireNotNull(messageSetFile.findMessageTypeByName("Payload"))

    /** MessageSet extension 注册表（全部使用全名：`ms.payload_a`、`ms.payload_b`）。 */
    val messageSetRegistry: ProtobufExtensionRegistry = ProtobufExtensionRegistry.of(messageSetFile)

    /** MessageSet extension 字段描述符，按编号升序。 */
    val messageSetExtensions: List<FieldDescriptor> = messageSetRegistry.extensions.sortedBy { it.number }

    /** proto3 消息，覆盖默认值省略、proto3 `optional`、enum、map、嵌套 message。 */
    val proto3File: FileDescriptor = buildProto3File()

    /** proto3 根消息 `p3.Sample`。 */
    val proto3Sample: Descriptor = requireNotNull(proto3File.findMessageTypeByName("Sample"))

    /** proto2 单 required 字段消息，用于 presence 回归。 */
    val proto2Required: Descriptor = buildProto2Required()

    /**
     * 使用同一份 [FileDescriptorProto] 重新构建一个 [FileDescriptor]。
     *
     * 用于验证“extension 注册表必须与 mapper 使用同一个 Descriptor 实例”的诊断行为。
     */
    fun rebuildExtendedFile(): FileDescriptor = FileDescriptor.buildFrom(extendedFile.toProto(), emptyArray())

    private fun buildExtendedFile(): FileDescriptor {
        val base = DescriptorProto.newBuilder()
            .setName("Base")
            .addField(optionalField("id", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(optionalField("name", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addExtensionRange(extensionRange(100, 200))
            .build()
        val extra = DescriptorProto.newBuilder()
            .setName("Extra")
            .addField(optionalField("value", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(optionalField("count", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .build()
        val nestedInner = DescriptorProto.newBuilder()
            .setName("Inner")
            .addField(optionalField("code", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addExtensionRange(extensionRange(100, 200))
            .build()
        val nested = DescriptorProto.newBuilder()
            .setName("Nested")
            .addField(messageField("inner", 1, ".ext.Nested.Inner"))
            .addNestedType(nestedInner)
            .addExtension(
                extensionField(
                    name = "inner_note",
                    number = 100,
                    type = FieldDescriptorProto.Type.TYPE_STRING,
                    extendee = ".ext.Nested.Inner",
                ),
            )
            .build()
        val messageA = DescriptorProto.newBuilder()
            .setName("A")
            .addExtension(
                extensionField(
                    name = "dup",
                    number = 110,
                    type = FieldDescriptorProto.Type.TYPE_STRING,
                    extendee = ".ext.Base",
                ),
            )
            .build()
        val messageB = DescriptorProto.newBuilder()
            .setName("B")
            .addExtension(
                extensionField(
                    name = "dup",
                    number = 111,
                    type = FieldDescriptorProto.Type.TYPE_STRING,
                    extendee = ".ext.Base",
                ),
            )
            .build()
        val fileProto = FileDescriptorProto.newBuilder()
            .setName("ext/extended.proto")
            .setPackage("ext")
            .setSyntax("proto2")
            .addMessageType(base)
            .addMessageType(extra)
            .addMessageType(nested)
            .addMessageType(messageA)
            .addMessageType(messageB)
            .addExtension(
                extensionField(
                    name = "nickname",
                    number = 100,
                    type = FieldDescriptorProto.Type.TYPE_STRING,
                    extendee = ".ext.Base",
                ),
            )
            .addExtension(
                extensionField(
                    name = "extra",
                    number = 101,
                    type = FieldDescriptorProto.Type.TYPE_MESSAGE,
                    typeName = ".ext.Extra",
                    extendee = ".ext.Base",
                ),
            )
            .addExtension(
                extensionField(
                    name = "numbers",
                    number = 102,
                    type = FieldDescriptorProto.Type.TYPE_INT32,
                    extendee = ".ext.Base",
                    repeated = true,
                ),
            )
            .build()
        return FileDescriptor.buildFrom(fileProto, emptyArray())
    }

    private fun buildMessageSetFile(): FileDescriptor {
        val messageSet = DescriptorProto.newBuilder()
            .setName("MessageSetRoot")
            .setOptions(MessageOptions.newBuilder().setMessageSetWireFormat(true))
            .addExtensionRange(extensionRange(4, 536_870_912))
            .build()
        val payload = DescriptorProto.newBuilder()
            .setName("Payload")
            .addField(optionalField("text", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(optionalField("count", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .build()
        val fileProto = FileDescriptorProto.newBuilder()
            .setName("ms/message_set.proto")
            .setPackage("ms")
            .setSyntax("proto2")
            .addMessageType(messageSet)
            .addMessageType(payload)
            .addExtension(
                extensionField(
                    name = "payload_a",
                    number = 100,
                    type = FieldDescriptorProto.Type.TYPE_MESSAGE,
                    typeName = ".ms.Payload",
                    extendee = ".ms.MessageSetRoot",
                ),
            )
            .addExtension(
                extensionField(
                    name = "payload_b",
                    number = 101,
                    type = FieldDescriptorProto.Type.TYPE_MESSAGE,
                    typeName = ".ms.Payload",
                    extendee = ".ms.MessageSetRoot",
                ),
            )
            .build()
        return FileDescriptor.buildFrom(fileProto, emptyArray())
    }

    private fun buildProto3File(): FileDescriptor {
        val status = EnumDescriptorProto.newBuilder()
            .setName("Status")
            .addValue(EnumValueDescriptorProto.newBuilder().setName("UNKNOWN").setNumber(0))
            .addValue(EnumValueDescriptorProto.newBuilder().setName("ACTIVE").setNumber(1))
            .build()
        val child = DescriptorProto.newBuilder()
            .setName("Child")
            .addField(optionalField("label", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .build()
        val countsEntry = DescriptorProto.newBuilder()
            .setName("CountsEntry")
            .setOptions(MessageOptions.newBuilder().setMapEntry(true))
            .addField(optionalField("key", 1, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(optionalField("value", 2, FieldDescriptorProto.Type.TYPE_INT32))
            .build()
        val sample = DescriptorProto.newBuilder()
            .setName("Sample")
            .addField(optionalField("age", 1, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(optionalField("name", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .addField(optionalField("data", 3, FieldDescriptorProto.Type.TYPE_BYTES))
            .addField(optionalField("active", 4, FieldDescriptorProto.Type.TYPE_BOOL))
            .addField(optionalField("score", 5, FieldDescriptorProto.Type.TYPE_DOUBLE))
            .addField(repeatedField("nums", 6, FieldDescriptorProto.Type.TYPE_INT32))
            .addField(messageField("counts", 7, ".p3.Sample.CountsEntry", repeated = true))
            .addField(enumField("status", 8, ".p3.Sample.Status"))
            .addField(messageField("child", 9, ".p3.Sample.Child"))
            .addField(
                optionalField("opt_age", 10, FieldDescriptorProto.Type.TYPE_INT32)
                    .toBuilder()
                    .setProto3Optional(true)
                    .setOneofIndex(0)
                    .build(),
            )
            .addOneofDecl(OneofDescriptorProto.newBuilder().setName("_opt_age"))
            .addNestedType(child)
            .addNestedType(countsEntry)
            .addEnumType(status)
            .build()
        val fileProto = FileDescriptorProto.newBuilder()
            .setName("p3/sample.proto")
            .setPackage("p3")
            .setSyntax("proto3")
            .addMessageType(sample)
            .build()
        return FileDescriptor.buildFrom(fileProto, emptyArray())
    }

    private fun buildProto2Required(): Descriptor {
        val message = DescriptorProto.newBuilder()
            .setName("Required")
            .addField(optionalField("id", 1, FieldDescriptorProto.Type.TYPE_INT32).toBuilder().setLabel(FieldDescriptorProto.Label.LABEL_REQUIRED).build())
            .addField(optionalField("name", 2, FieldDescriptorProto.Type.TYPE_STRING))
            .build()
        val fileProto = FileDescriptorProto.newBuilder()
            .setName("p2/required.proto")
            .setPackage("p2")
            .setSyntax("proto2")
            .addMessageType(message)
            .build()
        return FileDescriptor.buildFrom(fileProto, emptyArray()).findMessageTypeByName("Required")
    }

    private fun extensionRange(start: Int, end: Int): ExtensionRange = ExtensionRange.newBuilder().setStart(start).setEnd(end).build()

    private fun optionalField(name: String, number: Int, type: FieldDescriptorProto.Type): FieldDescriptorProto = baseField(name, number, type, FieldDescriptorProto.Label.LABEL_OPTIONAL)

    private fun repeatedField(name: String, number: Int, type: FieldDescriptorProto.Type): FieldDescriptorProto = baseField(name, number, type, FieldDescriptorProto.Label.LABEL_REPEATED)

    private fun messageField(
        name: String,
        number: Int,
        typeName: String,
        repeated: Boolean = false,
    ): FieldDescriptorProto = baseField(
        name,
        number,
        FieldDescriptorProto.Type.TYPE_MESSAGE,
        if (repeated) FieldDescriptorProto.Label.LABEL_REPEATED else FieldDescriptorProto.Label.LABEL_OPTIONAL,
    ).toBuilder().setTypeName(typeName).build()

    private fun enumField(name: String, number: Int, typeName: String): FieldDescriptorProto = baseField(
        name,
        number,
        FieldDescriptorProto.Type.TYPE_ENUM,
        FieldDescriptorProto.Label.LABEL_OPTIONAL,
    ).toBuilder().setTypeName(typeName).build()

    private fun baseField(
        name: String,
        number: Int,
        type: FieldDescriptorProto.Type,
        label: FieldDescriptorProto.Label,
    ): FieldDescriptorProto = FieldDescriptorProto.newBuilder()
        .setName(name)
        .setNumber(number)
        .setLabel(label)
        .setType(type)
        .build()

    private fun extensionField(
        name: String,
        number: Int,
        type: FieldDescriptorProto.Type,
        extendee: String,
        typeName: String? = null,
        repeated: Boolean = false,
    ): FieldDescriptorProto {
        val builder = FieldDescriptorProto.newBuilder()
            .setName(name)
            .setNumber(number)
            .setLabel(if (repeated) FieldDescriptorProto.Label.LABEL_REPEATED else FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setType(type)
            .setExtendee(extendee)
        if (typeName != null) {
            builder.setTypeName(typeName)
        }
        return builder.build()
    }

    /**
     * 构造带未知字段的动态消息字节。
     *
     * 未知字段通过 [UnknownFieldSet] 挂到消息上写出，方便构造 varint/fixed32/fixed64/
     * length-delimited/group 各类未知 wire 数据。
     */
    fun bytesWithUnknownFields(
        descriptor: Descriptor,
        declaredFields: Map<String, Any>,
        unknownFields: UnknownFieldSet,
    ): ByteArray {
        val builder = DynamicMessage.newBuilder(descriptor)
        for ((name, value) in declaredFields) {
            val field = descriptor.findFieldByName(name) ?: error("字段不存在: $name")
            if (field.isRepeated) {
                @Suppress("UNCHECKED_CAST")
                for (element in value as List<Any>) {
                    builder.addRepeatedField(field, element)
                }
            } else {
                builder.setField(field, value)
            }
        }
        return builder.setUnknownFields(unknownFields).build().toByteArray()
    }

    /** 构造 varint 未知字段集合。 */
    fun varintUnknown(number: Int, vararg values: Long): UnknownFieldSet {
        val field = UnknownFieldSet.Field.newBuilder()
        values.forEach { field.addVarint(it) }
        return UnknownFieldSet.newBuilder().mergeField(number, field.build()).build()
    }

    /** 构造固定宽度未知字段集合。 */
    fun fixedUnknown(number: Int, vararg fixed32: Int): UnknownFieldSet {
        val field = UnknownFieldSet.Field.newBuilder()
        fixed32.forEach { field.addFixed32(it) }
        return UnknownFieldSet.newBuilder().mergeField(number, field.build()).build()
    }

    /** 构造 64 位固定宽度未知字段集合。 */
    fun fixed64Unknown(number: Int, vararg values: Long): UnknownFieldSet {
        val field = UnknownFieldSet.Field.newBuilder()
        values.forEach { field.addFixed64(it) }
        return UnknownFieldSet.newBuilder().mergeField(number, field.build()).build()
    }

    /** 构造 length-delimited 未知字段集合。 */
    fun lengthDelimitedUnknown(number: Int, vararg payloads: ByteArray): UnknownFieldSet {
        val field = UnknownFieldSet.Field.newBuilder()
        payloads.forEach { field.addLengthDelimited(com.google.protobuf.ByteString.copyFrom(it)) }
        return UnknownFieldSet.newBuilder().mergeField(number, field.build()).build()
    }

    /** 构造 group 未知字段集合。 */
    fun groupUnknown(number: Int, inner: UnknownFieldSet): UnknownFieldSet = UnknownFieldSet.newBuilder()
        .mergeField(number, UnknownFieldSet.Field.newBuilder().addGroup(inner).build())
        .build()

    /** 合并多个未知字段集合。 */
    fun mergeUnknown(vararg sets: UnknownFieldSet): UnknownFieldSet {
        val builder = UnknownFieldSet.newBuilder()
        sets.forEach { builder.mergeFrom(it) }
        return builder.build()
    }
}
