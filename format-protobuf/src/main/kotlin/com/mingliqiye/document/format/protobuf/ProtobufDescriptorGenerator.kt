package com.mingliqiye.document.format.protobuf

import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto
import com.google.protobuf.DescriptorProtos.EnumOptions
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FileDescriptorProto
import com.google.protobuf.DescriptorProtos.MessageOptions
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto
import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.DescriptorValidationException
import com.google.protobuf.Descriptors.FileDescriptor
import com.mingliqiye.document.format.protobuf.exception.ProtobufException

/**
 * 把 [ProtobufSchema] 生成完整 [FileDescriptorProto]，并构建为 [Descriptor]。
 *
 * 生成 proto2 语法的单文件 descriptor：
 * - 根 message 名称中的点号生成隐式外层嵌套 message
 * - 嵌套 message/enum 递归生成
 * - map 字段生成 `map_entry = true` 的嵌套 entry message（key/value 两个字段）
 * - oneof 按字段声明顺序收集，字段写入 `oneof_index`
 * - message/enum 类型引用优先解析为当前文件内定义的绝对类型名；`.` 开头的全限定名原样交给
 *   protobuf-java，由 [ProtobufSchema.toDescriptor] 的依赖 Descriptor 解析
 */
internal object ProtobufDescriptorGenerator {

    private const val KIND_MESSAGE: String = "message"
    private const val KIND_ENUM: String = "enum"

    /**
     * 生成根 message 的 [Descriptor]。
     *
     * @param schema Protobuf Schema
     * @param dependencies 依赖的 FileDescriptor，用于解析全限定类型名
     * @return 根 message 描述符
     */
    fun generate(schema: ProtobufSchema, dependencies: Array<FileDescriptor>): Descriptor {
        val messageParts = schema.messageName.split('.')
        val packageName = schema.packageName
        val rootFullName = fullName(packageName, schema.messageName)
        val registry = TypeRegistry()
        registry.register(rootFullName, KIND_MESSAGE)
        registerDefinitions(rootFullName, schema.nestedMessages, schema.enums, registry)

        var messageProto = buildMessageProto(
            name = messageParts.last(),
            fields = schema.protobufFields,
            nestedMessages = schema.nestedMessages,
            enums = schema.enums,
            fullName = rootFullName,
            registry = registry,
        )
        for (index in messageParts.size - 2 downTo 0) {
            messageProto = DescriptorProto.newBuilder()
                .setName(messageParts[index])
                .addNestedType(messageProto)
                .build()
        }
        val fileProtoBuilder = FileDescriptorProto.newBuilder()
            .setName(fileName(packageName, schema.messageName))
            .addMessageType(messageProto)
        if (!packageName.isNullOrEmpty()) {
            fileProtoBuilder.setPackage(packageName)
        }
        val fileDescriptor = try {
            FileDescriptor.buildFrom(fileProtoBuilder.build(), dependencies)
        } catch (e: DescriptorValidationException) {
            throw ProtobufException("构建 Protobuf Descriptor 失败: ${e.message}", e)
        }
        var descriptor = fileDescriptor.findMessageTypeByName(messageParts.first())
            ?: throw ProtobufException("未找到 Protobuf message: ${schema.messageName}")
        for (index in 1 until messageParts.size) {
            descriptor = descriptor.findNestedTypeByName(messageParts[index])
                ?: throw ProtobufException("未找到嵌套 Protobuf message: ${schema.messageName}")
        }
        return descriptor
    }

    private fun buildMessageProto(
        name: String,
        fields: List<ProtobufSchemaField>,
        nestedMessages: List<ProtobufMessage>,
        enums: List<ProtobufEnum>,
        fullName: String,
        registry: TypeRegistry,
    ): DescriptorProto {
        val builder = DescriptorProto.newBuilder().setName(name)
        val oneofIndexes = LinkedHashMap<String, Int>()
        for (field in fields) {
            val oneofName = field.oneofName ?: continue
            if (!oneofIndexes.containsKey(oneofName)) {
                oneofIndexes[oneofName] = oneofIndexes.size
                builder.addOneofDecl(OneofDescriptorProto.newBuilder().setName(oneofName))
            }
        }
        val nestedTypeNames = LinkedHashSet<String>()
        for (nested in nestedMessages) {
            nestedTypeNames.add(nested.name)
        }
        for (enumSchema in enums) {
            nestedTypeNames.add(enumSchema.name)
        }
        for (field in fields) {
            if (field.protobufType == ProtobufType.MAP) {
                val entryProto = buildMapEntryProto(field, fullName, registry, nestedTypeNames)
                builder.addNestedType(entryProto)
                builder.addField(
                    FieldDescriptorProto.newBuilder()
                        .setName(field.name)
                        .setNumber(field.fieldNumber)
                        .setLabel(FieldDescriptorProto.Label.LABEL_REPEATED)
                        .setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                        .setTypeName(".$fullName.${entryProto.name}")
                        .build(),
                )
            } else {
                builder.addField(buildFieldProto(field, fullName, registry, oneofIndexes))
            }
        }
        for (nested in nestedMessages) {
            builder.addNestedType(
                buildMessageProto(
                    nested.name,
                    nested.fields,
                    nested.nestedMessages,
                    nested.enums,
                    "$fullName.${nested.name}",
                    registry,
                ),
            )
        }
        for (enumSchema in enums) {
            builder.addEnumType(buildEnumProto(enumSchema))
        }
        return builder.build()
    }

    private fun buildFieldProto(
        field: ProtobufSchemaField,
        scopeFullName: String,
        registry: TypeRegistry,
        oneofIndexes: Map<String, Int>,
    ): FieldDescriptorProto {
        val builder = FieldDescriptorProto.newBuilder()
            .setName(field.name)
            .setNumber(field.fieldNumber)
        val label = when {
            field.repeated -> FieldDescriptorProto.Label.LABEL_REPEATED
            field.required -> FieldDescriptorProto.Label.LABEL_REQUIRED
            else -> FieldDescriptorProto.Label.LABEL_OPTIONAL
        }
        builder.setLabel(label)
        field.oneofName?.let { builder.setOneofIndex(oneofIndexes.getValue(it)) }
        when (field.protobufType) {
            ProtobufType.MESSAGE -> {
                val reference = field.typeReference as? ProtobufTypeReference.MessageType
                    ?: throw ProtobufException(
                        "message 字段 '${field.name}' 缺少 MessageType 类型引用（作用域 $scopeFullName）",
                    )
                builder.setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                builder.setTypeName(resolveTypeName(reference.typeName, scopeFullName, registry, KIND_MESSAGE, field.name))
            }

            ProtobufType.ENUM -> {
                val reference = field.typeReference as? ProtobufTypeReference.EnumType
                    ?: throw ProtobufException(
                        "enum 字段 '${field.name}' 缺少 EnumType 类型引用（作用域 $scopeFullName）",
                    )
                builder.setType(FieldDescriptorProto.Type.TYPE_ENUM)
                builder.setTypeName(resolveTypeName(reference.typeName, scopeFullName, registry, KIND_ENUM, field.name))
            }

            else -> builder.setType(scalarType(field.protobufType, field.name))
        }
        return builder.build()
    }

    private fun buildMapEntryProto(
        field: ProtobufSchemaField,
        scopeFullName: String,
        registry: TypeRegistry,
        usedNestedTypeNames: MutableSet<String>,
    ): DescriptorProto {
        val mapType = field.mapType
            ?: throw ProtobufException("map 字段 '${field.name}' 缺少 MapType 类型引用（作用域 $scopeFullName）")
        val entryName = uniqueMapEntryName(field.name, usedNestedTypeNames)
        val keyField = FieldDescriptorProto.newBuilder()
            .setName("key")
            .setNumber(1)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
            .setType(scalarType(mapType.keyType, field.name))
            .build()
        val valueBuilder = FieldDescriptorProto.newBuilder()
            .setName("value")
            .setNumber(2)
            .setLabel(FieldDescriptorProto.Label.LABEL_OPTIONAL)
        when (mapType.valueType) {
            ProtobufType.MESSAGE -> {
                val reference = mapType.valueReference as? ProtobufTypeReference.MessageType
                    ?: throw ProtobufException(
                        "map 字段 '${field.name}' 的 value 缺少 MessageType 类型引用（作用域 $scopeFullName）",
                    )
                valueBuilder.setType(FieldDescriptorProto.Type.TYPE_MESSAGE)
                valueBuilder.setTypeName(resolveTypeName(reference.typeName, scopeFullName, registry, KIND_MESSAGE, field.name))
            }

            ProtobufType.ENUM -> {
                val reference = mapType.valueReference as? ProtobufTypeReference.EnumType
                    ?: throw ProtobufException(
                        "map 字段 '${field.name}' 的 value 缺少 EnumType 类型引用（作用域 $scopeFullName）",
                    )
                valueBuilder.setType(FieldDescriptorProto.Type.TYPE_ENUM)
                valueBuilder.setTypeName(resolveTypeName(reference.typeName, scopeFullName, registry, KIND_ENUM, field.name))
            }

            else -> valueBuilder.setType(scalarType(mapType.valueType, field.name))
        }
        return DescriptorProto.newBuilder()
            .setName(entryName)
            .setOptions(MessageOptions.newBuilder().setMapEntry(true))
            .addField(keyField)
            .addField(valueBuilder.build())
            .build()
    }

    private fun buildEnumProto(enumSchema: ProtobufEnum): EnumDescriptorProto {
        val builder = EnumDescriptorProto.newBuilder().setName(enumSchema.name)
        val numbers = HashSet<Int>()
        var hasAlias = false
        for (value in enumSchema.values) {
            builder.addValue(
                EnumValueDescriptorProto.newBuilder()
                    .setName(value.name)
                    .setNumber(value.number),
            )
            if (!numbers.add(value.number)) {
                hasAlias = true
            }
        }
        if (hasAlias) {
            builder.setOptions(EnumOptions.newBuilder().setAllowAlias(true))
        }
        return builder.build()
    }

    private fun scalarType(protobufType: ProtobufType, fieldName: String): FieldDescriptorProto.Type = when (protobufType) {
        ProtobufType.DOUBLE -> FieldDescriptorProto.Type.TYPE_DOUBLE
        ProtobufType.FLOAT -> FieldDescriptorProto.Type.TYPE_FLOAT
        ProtobufType.INT32 -> FieldDescriptorProto.Type.TYPE_INT32
        ProtobufType.INT64 -> FieldDescriptorProto.Type.TYPE_INT64
        ProtobufType.UINT32 -> FieldDescriptorProto.Type.TYPE_UINT32
        ProtobufType.UINT64 -> FieldDescriptorProto.Type.TYPE_UINT64
        ProtobufType.SINT32 -> FieldDescriptorProto.Type.TYPE_SINT32
        ProtobufType.SINT64 -> FieldDescriptorProto.Type.TYPE_SINT64
        ProtobufType.FIXED32 -> FieldDescriptorProto.Type.TYPE_FIXED32
        ProtobufType.FIXED64 -> FieldDescriptorProto.Type.TYPE_FIXED64
        ProtobufType.SFIXED32 -> FieldDescriptorProto.Type.TYPE_SFIXED32
        ProtobufType.SFIXED64 -> FieldDescriptorProto.Type.TYPE_SFIXED64
        ProtobufType.BOOL -> FieldDescriptorProto.Type.TYPE_BOOL
        ProtobufType.STRING -> FieldDescriptorProto.Type.TYPE_STRING
        ProtobufType.BYTES -> FieldDescriptorProto.Type.TYPE_BYTES
        ProtobufType.ENUM,
        ProtobufType.MESSAGE,
        ProtobufType.MAP,
        -> throw ProtobufException("字段 '$fieldName' 的类型 '${protobufType.typeName}' 不是标量类型")
    }

    private fun resolveTypeName(
        typeName: String,
        scopeFullName: String,
        registry: TypeRegistry,
        expectedKind: String,
        fieldName: String,
    ): String {
        if (typeName.startsWith(".")) {
            return typeName
        }
        val resolved = registry.resolve(typeName, scopeFullName) ?: return typeName
        val actualKind = registry.kindOf(resolved)
        if (actualKind != expectedKind) {
            throw ProtobufException(
                "字段 '$fieldName' 引用的类型 $resolved 不是 $expectedKind（实际为 $actualKind）",
            )
        }
        return ".$resolved"
    }

    private fun registerDefinitions(
        parentFullName: String,
        nestedMessages: List<ProtobufMessage>,
        enums: List<ProtobufEnum>,
        registry: TypeRegistry,
    ) {
        for (nested in nestedMessages) {
            val nestedFullName = "$parentFullName.${nested.name}"
            registry.register(nestedFullName, KIND_MESSAGE)
            registerDefinitions(nestedFullName, nested.nestedMessages, nested.enums, registry)
        }
        for (enumSchema in enums) {
            registry.register("$parentFullName.${enumSchema.name}", KIND_ENUM)
        }
    }

    private fun uniqueMapEntryName(fieldName: String, usedNames: MutableSet<String>): String {
        val base = camelize(fieldName) + "Entry"
        var candidate = base
        while (!usedNames.add(candidate)) {
            candidate += "_"
        }
        return candidate
    }

    private fun camelize(name: String): String = name
        .split('_')
        .filter { it.isNotEmpty() }
        .joinToString("") { segment -> segment.replaceFirstChar { it.uppercaseChar() } }

    private fun fullName(packageName: String?, messageName: String): String = if (packageName.isNullOrEmpty()) messageName else "$packageName.$messageName"

    private fun fileName(packageName: String?, messageName: String): String {
        val messagePath = messageName.replace('.', '/')
        val prefix = packageName?.replace('.', '/')
        return if (prefix.isNullOrEmpty()) "$messagePath.proto" else "$prefix/$messagePath.proto"
    }

    /**
     * 文件内定义的 message/enum 全限定名索引，用于解析相对类型引用。
     */
    private class TypeRegistry {

        private val kinds = LinkedHashMap<String, String>()

        fun register(fullName: String, kind: String) {
            kinds[fullName] = kind
        }

        fun kindOf(fullName: String): String? = kinds[fullName]

        fun resolve(typeName: String, scopeFullName: String): String? {
            val firstPart = typeName.substringBefore('.')
            var scopeToTry = scopeFullName
            while (true) {
                val firstPartSymbol = "$scopeToTry.$firstPart"
                if (kinds.containsKey(firstPartSymbol)) {
                    val resolved = if (typeName == firstPart) firstPartSymbol else "$scopeToTry.$typeName"
                    if (kinds.containsKey(resolved)) {
                        return resolved
                    }
                }
                val lastDot = scopeToTry.lastIndexOf('.')
                if (lastDot < 0) {
                    return null
                }
                scopeToTry = scopeToTry.substring(0, lastDot)
            }
        }
    }
}
