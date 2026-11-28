package com.mingliqiye.document.format.protobuf

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FileDescriptor
import com.google.protobuf.DynamicMessage
import com.google.protobuf.ExtensionRegistry
import java.util.LinkedHashMap

/**
 * Protobuf extension 注册表。
 *
 * Protobuf 的 extension 是“在消息声明之外”声明的字段：wire 上仍然是普通字段，但只有拿到对应的
 * [FieldDescriptor] 才能解析。本类封装 [ExtensionRegistry] 与文档字段命名，供 [ProtobufMapper]
 * 在读写时把 extension 映射为对象字段：
 *
 * - 读入：`DynamicMessage.parseFrom(descriptor, bytes, protobufRegistry)` 解析已知 extension，
 *   再按 [documentName] 映射为对象字段；未注册的 extension 编号仍按未知字段处理（见
 *   [ProtobufUnknownFieldPolicy]）。
 * - 写出：对象中出现的 extension 字段名解析为 [FieldDescriptor] 后写入 `DynamicMessage`。
 *
 * 默认命名方式为 [ProtobufExtensionNaming.FULL_NAME]。同一消息内文档字段名冲突（例如
 * [ProtobufExtensionNaming.SIMPLE_NAME] 下两个同名 extension，或与普通字段同名）时，
 * [Builder.build] 抛出 [IllegalArgumentException]。
 *
 * 注意：Protobuf 的 extension 解析按 Descriptor **实例**匹配，注册表中的 extension 必须与
 * [ProtobufMapper] 使用的 [Descriptor] 来自同一个消息定义实例（通常是同一个 `FileDescriptor`
 * 树）。用其它地方重新构建的 Descriptor 注册 extension 时，解析会把这些编号当作未知字段。
 *
 * @see ProtobufMapper.extensions
 * @see ProtobufExtensionNaming
 */
public class ProtobufExtensionRegistry private constructor(
    /** 文档字段命名方式。 */
    public val naming: ProtobufExtensionNaming,
    extensionList: List<FieldDescriptor>,
) {

    /** 注册的 extension 字段列表，保持注册顺序。 */
    public val extensions: List<FieldDescriptor> = extensionList.toList()

    /**
     * 底层 Protobuf [ExtensionRegistry]，用于 [DynamicMessage.parseFrom] 等原生 API。
     *
     * 消息类型的 extension 已用 [DynamicMessage.getDefaultInstance] 作为默认实例注册，
     * 可以直接交给 Protobuf 解析器使用。
     */
    public val protobufRegistry: ExtensionRegistry = createProtobufRegistry(this.extensions)

    private val byContainingType: Map<String, List<FieldDescriptor>> =
        this.extensions.groupBy { it.containingType.fullName }

    private val byName: Map<String, Map<String, FieldDescriptor>> = this.extensions
        .groupBy { it.containingType.fullName }
        .mapValues { (_, fields) -> fields.associateBy { documentName(it) } }

    private val byNumber: Map<String, Map<Int, FieldDescriptor>> = this.extensions
        .groupBy { it.containingType.fullName }
        .mapValues { (_, fields) -> fields.associateBy { it.number } }

    /** 是否没有注册任何 extension。 */
    public val isEmpty: Boolean get() = extensions.isEmpty()

    /**
     * 计算 extension 在当前命名方式下的文档字段名。
     *
     * @param extension extension 字段描述符
     * @return 文档字段名
     */
    public fun documentName(extension: FieldDescriptor): String = when (naming) {
        ProtobufExtensionNaming.SIMPLE_NAME -> extension.name
        ProtobufExtensionNaming.FULL_NAME -> extension.fullName
        ProtobufExtensionNaming.BRACKETED -> "[${extension.fullName}]"
    }

    /**
     * 查找某个消息已注册的 extension。
     *
     * @param containingType 被扩展的消息描述符
     * @return extension 字段列表；没有时返回空列表
     */
    public fun extensionsFor(containingType: Descriptor): List<FieldDescriptor> = byContainingType[containingType.fullName].orEmpty()

    /**
     * 按文档字段名查找某个消息的 extension。
     *
     * @param containingType 被扩展的消息描述符
     * @param documentName 文档字段名
     * @return extension 字段描述符；不存在时返回 `null`
     */
    public fun extension(containingType: Descriptor, documentName: String): FieldDescriptor? = byName[containingType.fullName]?.get(documentName)

    /**
     * 按扩展编号查找某个消息的 extension。
     *
     * @param containingType 被扩展的消息描述符
     * @param fieldNumber 扩展字段编号
     * @return extension 字段描述符；不存在时返回 `null`
     */
    public fun extension(containingType: Descriptor, fieldNumber: Int): FieldDescriptor? = byNumber[containingType.fullName]?.get(fieldNumber)

    /**
     * 仅按消息全名与扩展编号查找 extension（不比较 Descriptor 实例）。
     *
     * 用于诊断“注册表与 mapper 使用了不同 Descriptor 实例”的情况。
     */
    internal fun extensionByTypeName(containingTypeFullName: String, fieldNumber: Int): FieldDescriptor? = byNumber[containingTypeFullName]?.get(fieldNumber)

    public companion object {

        /** 空注册表，表示不使用任何 extension。 */
        public val EMPTY: ProtobufExtensionRegistry = ProtobufExtensionRegistry(ProtobufExtensionNaming.FULL_NAME, emptyList())

        /**
         * 创建注册表构建器。
         */
        @JvmStatic
        public fun builder(): Builder = Builder()

        /**
         * 使用默认命名方式（[ProtobufExtensionNaming.FULL_NAME]）注册一批 extension。
         *
         * @param extensions extension 字段描述符
         * @return 注册表
         */
        @JvmStatic
        public fun of(vararg extensions: FieldDescriptor): ProtobufExtensionRegistry = Builder().addAll(extensions.asList()).build()

        /**
         * 注册 [FileDescriptor] 中声明（含嵌套 message 内声明）的全部 extension。
         *
         * @param fileDescriptor Protobuf 文件描述符
         * @return 注册表
         */
        @JvmStatic
        public fun of(fileDescriptor: FileDescriptor): ProtobufExtensionRegistry = Builder().addAll(collectExtensions(fileDescriptor)).build()

        /**
         * 使用指定命名方式注册 [FileDescriptor] 中声明（含嵌套 message 内声明）的全部 extension。
         *
         * @param fileDescriptor Protobuf 文件描述符
         * @param naming 文档字段命名方式
         * @return 注册表
         */
        @JvmStatic
        public fun of(fileDescriptor: FileDescriptor, naming: ProtobufExtensionNaming): ProtobufExtensionRegistry = Builder().addAll(collectExtensions(fileDescriptor)).naming(naming).build()

        private fun collectExtensions(fileDescriptor: FileDescriptor): List<FieldDescriptor> {
            val collected = ArrayList<FieldDescriptor>()
            collected.addAll(fileDescriptor.extensions)
            collected.addAll(collectExtensions(fileDescriptor.messageTypes))
            return collected
        }

        private fun collectExtensions(descriptors: List<Descriptor>): List<FieldDescriptor> {
            val collected = ArrayList<FieldDescriptor>()
            for (descriptor in descriptors) {
                collected.addAll(descriptor.extensions)
                collected.addAll(collectExtensions(descriptor.nestedTypes))
            }
            return collected
        }

        private fun createProtobufRegistry(extensions: List<FieldDescriptor>): ExtensionRegistry {
            val registry = ExtensionRegistry.newInstance()
            for (extension in extensions) {
                if (extension.javaType == FieldDescriptor.JavaType.MESSAGE) {
                    registry.add(extension, DynamicMessage.getDefaultInstance(extension.messageType))
                } else {
                    registry.add(extension)
                }
            }
            return registry
        }

        private fun validate(extension: FieldDescriptor) {
            require(extension.isExtension) {
                "Protobuf extension 注册表只接受 extension 字段描述符: ${extension.fullName}"
            }
        }

        private fun validateNames(naming: ProtobufExtensionNaming, extensions: List<FieldDescriptor>) {
            val namesByType = LinkedHashMap<String, MutableSet<String>>()
            for (extension in extensions) {
                val documentName = when (naming) {
                    ProtobufExtensionNaming.SIMPLE_NAME -> extension.name
                    ProtobufExtensionNaming.FULL_NAME -> extension.fullName
                    ProtobufExtensionNaming.BRACKETED -> "[${extension.fullName}]"
                }
                val typeName = extension.containingType.fullName
                val names = namesByType.getOrPut(typeName) { HashSet() }
                require(names.add(documentName)) {
                    "消息 $typeName 的 extension 文档字段名冲突: $documentName（命名方式 $naming）"
                }
                require(extension.containingType.findFieldByName(documentName) == null) {
                    "消息 $typeName 的 extension 文档字段名与普通字段冲突: $documentName（命名方式 $naming）"
                }
            }
        }
    }

    /**
     * [ProtobufExtensionRegistry] 构建器。
     */
    public class Builder internal constructor() {

        private val extensions = ArrayList<FieldDescriptor>()
        private var naming: ProtobufExtensionNaming = ProtobufExtensionNaming.FULL_NAME

        /**
         * 注册一个 extension 字段。
         *
         * @param extension extension 字段描述符（必须是 `extend` 声明的字段）
         * @return 当前构建器
         */
        public fun add(extension: FieldDescriptor): Builder {
            validate(extension)
            extensions.add(extension)
            return this
        }

        /**
         * 注册一批 extension 字段。
         *
         * @param extensions extension 字段描述符
         * @return 当前构建器
         */
        public fun addAll(extensions: Iterable<FieldDescriptor>): Builder {
            for (extension in extensions) {
                add(extension)
            }
            return this
        }

        /**
         * 设置文档字段命名方式。
         *
         * @param naming 命名方式
         * @return 当前构建器
         */
        public fun naming(naming: ProtobufExtensionNaming): Builder {
            this.naming = naming
            return this
        }

        /**
         * 构建注册表。
         *
         * @return 注册表
         * @throws IllegalArgumentException 注册的不是 extension 字段，或同一消息内文档字段名冲突时抛出
         */
        public fun build(): ProtobufExtensionRegistry {
            validateNames(naming, extensions)
            return ProtobufExtensionRegistry(naming, extensions)
        }
    }
}
