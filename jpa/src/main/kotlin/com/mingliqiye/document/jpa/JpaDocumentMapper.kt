package com.mingliqiye.document.jpa

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.core.StringNode
import com.mingliqiye.document.jpa.exception.JpaConfigException
import com.mingliqiye.document.jpa.exception.JpaMappingException
import com.mingliqiye.document.jpa.internal.JpaAssociationKind
import com.mingliqiye.document.jpa.internal.JpaAttributeConverter
import com.mingliqiye.document.jpa.internal.JpaConvertOverride
import com.mingliqiye.document.jpa.internal.JpaEntityMetadata
import com.mingliqiye.document.jpa.internal.JpaIdentifierKind
import com.mingliqiye.document.jpa.internal.JpaInheritanceMetadata
import com.mingliqiye.document.jpa.internal.JpaMetadataResolver
import com.mingliqiye.document.jpa.internal.JpaProperty
import com.mingliqiye.document.jpa.internal.JpaPropertyKind
import com.mingliqiye.document.jpa.internal.JpaValueConverter
import com.mingliqiye.document.jpa.internal.isScalarValueType
import com.mingliqiye.document.jpa.internal.newCollectionInstance
import com.mingliqiye.document.jpa.internal.newMapInstance
import jakarta.persistence.DiscriminatorType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.LinkedHashMap

/**
 * JPA 实体与 [Node] 树之间的映射器。
 *
 * 读取 JPA 注解（`@Entity`/`@Table`/`@Column`/`@Id`/`@Embedded`/`@EmbeddedId`/
 * `@Transient`/`@Enumerated`/`@Temporal`/`@OneToMany`/`@ManyToOne`/`@OneToOne`/
 * `@ManyToMany`/`@ElementCollection`/`@Access`/`@MappedSuperclass`）构建实体元数据，
 * 在 Java 实体与格式无关的文档树之间转换：
 * - [toNode]：实体 -> 对象节点；
 * - [fromNode]：对象节点 -> 实体（无参构造器 + 字段/setter 填充）。
 *
 * 高级特性：
 * - `@Convert`/autoApply `AttributeConverter`：文档中出现的是数据库列值（转换器
 *   `convertToDatabaseColumn` 的结果），读入时反向转换；null 也会传入转换器
 *   （与 JPA 提供者一致）；
 * - `@Convert(attributeName = "...")`：`@Embedded`/`@EmbeddedId` 属性内部基本属性的
 *   点分路径覆盖转换器（类级或属性级标注），覆盖嵌入类自身的转换器/autoApply；
 * - 关系属性上的显式 `@Convert`：整个关联值按基本属性转换（不展开关联），显式转换
 *   优先于关联展开；
 * - Map 关联（`@OneToMany`/`@ManyToMany` 的 `Map<K, V>`）与 Map `@ElementCollection`：
 *   文档中为对象节点，键为字符串化的 Map 键（`@MapKey`/`@MapKeyColumn`/键类型语义见
 *   `JpaConfig` 文档）；`@MapKeyJoinColumn` 的实体键写出键实体标识符文本；
 * - `@Inheritance`/`@DiscriminatorColumn`/`@DiscriminatorValue`：继承层次的对象额外
 *   写出 discriminator 字段；读入时按 discriminator 选择已注册的子类，未知值抛异常。
 *
 * 该映射器不依赖 Hibernate 运行期，也不管理 `Session`/`EntityManager`/懒加载/事务：
 * 只处理已经加载到内存中的实体与普通字段值。Hibernate 延迟代理在写出时按其
 * `@Entity` 父类元数据展开（访问代理从未加载的字段可能抛懒加载异常，由调用方规避）。
 *
 * 线程安全性：映射器内部缓存按类不可变元数据，可跨线程复用；
 * 单次映射调用自身不共享可变状态。
 *
 * @param config 映射配置
 */
public class JpaDocumentMapper @JvmOverloads constructor(
    public val config: JpaConfig = JpaConfig.builder().build(),
) {

    private val resolver = JpaMetadataResolver(config)
    private val values = JpaValueConverter()

    /**
     * 将实体序列化为对象节点。
     *
     * 关联按 [JpaConfig.associationDepth] 递归展开；同一对象在递归路径上再次出现
     * （循环引用）时抛出 [JpaMappingException]。继承层次中的实体会额外写出
     * discriminator 字段（位于对象字段最前）。
     *
     * @param entity 实体对象，不能为 `null`
     * @return 对象节点
     * @throws JpaMappingException 循环引用、属性不可读或值类型不受支持时抛出
     * @throws JpaConfigException 实体元数据非法时抛出
     */
    public fun toNode(entity: Any): ObjectNode {
        val metadata = resolver.resolve(entity.javaClass)
        if (!metadata.entityClass.isInstance(entity)) {
            throw JpaMappingException(
                "Cannot map ${entity.javaClass.name} as entity ${metadata.entityClass.name}",
            )
        }
        val fields = LinkedHashMap<String, Node>(metadata.properties.size + 1)
        writeObject(entity, metadata, 0, "$", IdentityHashMap(), fields, metadata.convertOverrides)
        return ObjectNode.from(fields)
    }

    /**
     * 将实体集合序列化为数组节点。
     *
     * @param entities 实体集合
     * @return 数组节点
     */
    public fun toNodes(entities: Iterable<*>): ArrayNode {
        val elements = ArrayList<Node>()
        for (entity in entities) {
            if (entity == null) {
                throw JpaMappingException("Entity collection must not contain null elements")
            }
            elements.add(toNode(entity))
        }
        return ArrayNode.from(elements)
    }

    /**
     * 将对象节点反序列化为指定实体类型。
     *
     * 只写入文档中出现的属性；缺失属性保持新建对象的默认值。`@Column(nullable = false)`
     * 的标量属性读到显式 null 时抛异常；基本类型属性读到 null 同样抛异常。
     *
     * 如果 [entityType] 位于 `@Inheritance`/`@DiscriminatorColumn` 层次中，会优先读取
     * 文档中的 discriminator 字段，并按 [JpaConfig.entityTypes] 注册的子类实例化：
     * 未知 discriminator 值抛 [JpaMappingException]；没有 discriminator 字段时回退到
     * 声明类型（声明类型为抽象类/接口时抛异常）。
     *
     * @param node 文档节点，必须是对象节点
     * @param entityType 目标实体类型
     * @return 实体对象
     * @throws JpaMappingException 节点类型不匹配、未知属性、属性不可写、值类型不匹配、
     * 循环、未知/缺失 discriminator 或无法实例化时抛出
     * @throws JpaConfigException 实体元数据非法时抛出
     */
    public fun <T : Any> fromNode(node: Node, entityType: Class<T>): T {
        val metadata = resolver.resolve(entityType)
        val objectNode = node.asObjectOrNull()
            ?: throw JpaMappingException(
                "Expected object node for entity ${metadata.entityName} at $ but was ${node.type}",
            )
        @Suppress("UNCHECKED_CAST")
        return readObject(objectNode, metadata, 0, "$", metadata.convertOverrides) as T
    }

    /**
     * 将数组节点反序列化为指定实体类型的列表。
     *
     * @param node 文档节点，必须是数组节点
     * @param entityType 元素实体类型
     * @return 实体列表
     */
    public fun <T : Any> fromNodes(node: Node, entityType: Class<T>): List<T> {
        val arrayNode = node.asArrayOrNull()
            ?: throw JpaMappingException("Expected array node at $ but was ${node.type}")
        val metadata = resolver.resolve(entityType)
        val result = ArrayList<T>(arrayNode.size)
        for (index in 0 until arrayNode.size) {
            val element = arrayNode[index]
            @Suppress("UNCHECKED_CAST")
            result.add(
                readObject(
                    requireObject(element, "$[$index]"),
                    metadata,
                    0,
                    "$[$index]",
                    metadata.convertOverrides,
                ) as T,
            )
        }
        return result
    }

    /**
     * 类层次中是否存在 `@Entity` 注解。
     *
     * @param entityType 类型
     */
    public fun isEntity(entityType: Class<*>): Boolean = resolver.findEntityClass(entityType) != null

    /**
     * 实体名：`@Entity(name)` 非空时使用，否则使用实体类的简单类名。
     *
     * @param entityType 实体类型
     */
    public fun entityName(entityType: Class<*>): String = resolver.resolve(entityType).entityName

    /**
     * 表名：类层次中第一个非空 `@Table(name)`。
     *
     * @param entityType 实体类型
     * @return 表名；没有 `@Table` 注解或名称为空时返回 `null`
     */
    public fun tableName(entityType: Class<*>): String? = resolver.resolve(entityType).tableName

    /**
     * 实体在继承层次中的 discriminator 值（`@DiscriminatorValue` 或 STRING/CHAR 的
     * 实体名默认值）。
     *
     * @param entityType 实体类型
     * @return discriminator 值；实体不在 `@Inheritance`/`@DiscriminatorColumn` 层次中，
     * 或 INTEGER discriminator 缺少 `@DiscriminatorValue` 时返回 `null`
     */
    public fun discriminatorValue(entityType: Class<*>): String? = resolver.resolve(entityType).inheritance?.discriminatorValue

    /**
     * 继承 discriminator 的节点字段名（列名经命名策略转换）。
     *
     * @param entityType 实体类型
     * @return 字段名；实体不在继承层次中时返回 `null`
     */
    public fun discriminatorFieldName(entityType: Class<*>): String? = resolver.resolve(entityType).inheritance?.discriminatorNodeName

    /**
     * 实体作为 Map 键（`@MapKeyJoinColumn`）时的标识符文本。
     *
     * 用于文档对象字段名：单个基本 `@Id` 输出标识符值文本；`@EmbeddedId`/`@IdClass`
     * 复合主键输出 `属性名=转义值` 以 `&` 连接的规范文本（`%`、`&`、`=` 会百分号转义）。
     *
     * 与 [JpaDocumentMapper] 内部的 Map 实体键映射规则一致；公开给需要复用 jpa 元数据的
     * 扩展模块（例如 `jpa-hibernate` 在 Hibernate 感知映射中处理实体 Map 键）。
     *
     * @param keyEntity Map 键实体实例（标识符属性必须已赋值）
     * @param keyType Map 键实体类型
     * @return 标识符文本
     * @throws JpaMappingException 标识符属性为 null 或不受支持时抛出
     * @throws JpaConfigException 类型没有主键/复合主键包含非基本属性时抛出
     */
    public fun entityKeyText(keyEntity: Any, keyType: Class<*>): String = entityKeyTextInternal(keyEntity, keyType, "$")

    /**
     * 标识符文本 → detach 键实体实例，是 [entityKeyText] 的逆操作。
     *
     * 只还原标识符属性（不读取其他字段），适合作为 Map 的 detach 键；
     * Hibernate 集成可在拿到实例后再按标识符解析为受管实例。
     *
     * @param text 标识符文本（文档对象字段名）
     * @param keyType Map 键实体类型（必须有无参构造器）
     * @return detach 键实体实例
     * @throws JpaMappingException 文本无法解析为标识符时抛出
     * @throws JpaConfigException 类型没有主键或标识符属性不可写时抛出
     */
    public fun entityKeyFromText(text: String, keyType: Class<*>): Any = entityKeyFromTextInternal(text, keyType, "$")

    // ------------------------------------------------------------------
    // 实体 -> Node
    // ------------------------------------------------------------------

    private fun writeObject(
        instance: Any,
        metadata: JpaEntityMetadata,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
        fields: MutableMap<String, Node>,
        overrides: Map<String, JpaConvertOverride>,
    ) {
        val previous = stack.put(instance, path)
        if (previous != null) {
            throw JpaMappingException(
                "Circular reference detected at $path: the same ${instance.javaClass.name} instance " +
                    "was already visited at $previous",
            )
        }
        try {
            writeDiscriminator(metadata, path, fields)
            val discriminatorKey = metadata.inheritance?.discriminatorNodeName
            for (property in metadata.readableProperties()) {
                if (property.nodeName == discriminatorKey) {
                    throw JpaConfigException(
                        "Property '${property.name}' of ${metadata.entityClass.name} conflicts with the " +
                            "discriminator field '$discriminatorKey'",
                    )
                }
                writeProperty(
                    instance,
                    property,
                    depth,
                    "$path.${property.nodeName}",
                    stack,
                    fields,
                    overrides[property.name],
                    nestedConvertOverrides(overrides, property.name),
                )
            }
        } finally {
            stack.remove(instance)
        }
    }

    private fun writeDiscriminator(
        metadata: JpaEntityMetadata,
        path: String,
        fields: MutableMap<String, Node>,
    ) {
        val inheritance = metadata.inheritance ?: return
        val value = inheritance.discriminatorValue
            ?: throw JpaConfigException(
                "Entity ${inheritance.declaredClass.name} at $path has no @DiscriminatorValue; " +
                    "DiscriminatorType.INTEGER requires an explicit value",
            )
        fields[inheritance.discriminatorNodeName] = discriminatorNode(inheritance, value, path)
    }

    private fun discriminatorNode(inheritance: JpaInheritanceMetadata, value: String, path: String): Node {
        if (inheritance.discriminatorType != DiscriminatorType.INTEGER) {
            return StringNode.of(value)
        }
        val number = value.toIntOrNull()
            ?: throw JpaConfigException(
                "Discriminator value '$value' of ${inheritance.declaredClass.name} at $path is not an integer",
            )
        return NumberNode.of(number)
    }

    private fun writeProperty(
        instance: Any,
        property: JpaProperty,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
        fields: MutableMap<String, Node>,
        propertyOverride: JpaConvertOverride?,
        nestedOverrides: Map<String, JpaConvertOverride>,
    ) {
        val converter = effectiveConverter(property, propertyOverride)
        if (property.kind == JpaPropertyKind.ASSOCIATION &&
            converter == null &&
            depth >= config.associationDepth
        ) {
            return
        }
        val value = property.read(instance, path)
        if (value == null) {
            if (converter != null) {
                // 与 JPA 提供者一致：null 也会传入转换器，转换器可返回非 null 的数据库值。
                val node = values.writeConverted(null, converter, property.enumType, property.temporalType, path)
                if (!node.isNull || config.nullPolicy == JpaNullPolicy.INCLUDE) {
                    fields[property.nodeName] = node
                }
                return
            }
            if (config.nullPolicy == JpaNullPolicy.INCLUDE) {
                fields[property.nodeName] = NullNode.INSTANCE
            }
            return
        }
        fields[property.nodeName] = when (property.kind) {
            JpaPropertyKind.SCALAR -> writeScalar(value, property, converter, path)
            JpaPropertyKind.EMBEDDED -> writeEmbedded(value, property, depth, path, stack, nestedOverrides)
            JpaPropertyKind.ASSOCIATION -> if (converter != null) {
                // 显式 @Convert 的关系属性：整个关联值按基本属性转换，不展开关联。
                values.writeConverted(value, converter, property.enumType, property.temporalType, path)
            } else {
                writeAssociation(value, property, depth, path, stack)
            }

            JpaPropertyKind.ELEMENT_COLLECTION -> writeElementCollection(value, property, depth, path, stack)
        }
    }

    /**
     * 计算属性最终生效的转换器：嵌套路径覆盖 > 属性自身转换器。
     *
     * `@Convert(attributeName = ...)` 显式覆盖优先；`disableConversion` 覆盖表现为
     * 存在覆盖但转换器为 `null`，此时不使用属性自身的转换器/autoApply。
     */
    private fun effectiveConverter(property: JpaProperty, override: JpaConvertOverride?): JpaAttributeConverter? = if (override != null) override.converter else property.converter

    private fun writeScalar(
        value: Any,
        property: JpaProperty,
        converter: JpaAttributeConverter?,
        path: String,
    ): Node = if (converter != null) {
        values.writeConverted(value, converter, property.enumType, property.temporalType, path)
    } else if (property.transientProperty) {
        values.writeTransient(value, property, path)
    } else {
        values.writeProperty(value, property, path)
    }

    private fun writeEmbedded(
        value: Any,
        property: JpaProperty,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
        nestedOverrides: Map<String, JpaConvertOverride>,
    ): ObjectNode {
        val metadata = resolver.resolve(property.declaredType)
        val effectiveOverrides = mergedConvertOverrides(metadata.convertOverrides, nestedOverrides)
        val fields = LinkedHashMap<String, Node>(metadata.properties.size)
        writeObject(value, metadata, depth, path, stack, fields, effectiveOverrides)
        return ObjectNode.from(fields)
    }

    private fun writeAssociation(
        value: Any,
        property: JpaProperty,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): Node {
        val associationKind = property.associationKind
            ?: throw JpaConfigException("Association property '${property.name}' has no association kind")
        if (associationKind == JpaAssociationKind.ONE_TO_ONE || associationKind == JpaAssociationKind.MANY_TO_ONE) {
            return writeEntity(value, depth + 1, path, stack)
        }
        if (property.map) {
            val map = value as? Map<*, *>
                ?: throw JpaMappingException(
                    "Association property '${property.name}' expects a Map at $path but was ${value.javaClass.name}",
                )
            return writeAssociationMap(map, property, depth, path, stack)
        }
        val elements = value as? Collection<*>
            ?: throw JpaMappingException(
                "Association property '${property.name}' expects a Collection at $path but was ${value.javaClass.name}",
            )
        val nodes = ArrayList<Node>(elements.size)
        var index = 0
        for (element in elements) {
            nodes.add(
                if (element == null) {
                    NullNode.INSTANCE
                } else {
                    writeEntity(element, depth + 1, "$path[$index]", stack)
                },
            )
            index++
        }
        return ArrayNode.from(nodes)
    }

    private fun writeAssociationMap(
        value: Map<*, *>,
        property: JpaProperty,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): ObjectNode {
        val fields = LinkedHashMap<String, Node>(value.size)
        if (property.mapKeyEntity) {
            val keyType = mapKeyTypeOf(property)
            for ((key, element) in value) {
                if (key == null) {
                    throw JpaMappingException("Map key must not be null at $path")
                }
                val keyText = entityKeyTextInternal(key, keyType, path)
                if (fields.containsKey(keyText)) {
                    throw JpaMappingException(
                        "Map key collision at $path: multiple keys of '${property.name}' " +
                            "are serialized as '$keyText'",
                    )
                }
                fields[keyText] = if (element == null) {
                    NullNode.INSTANCE
                } else {
                    writeEntity(element, depth + 1, "$path[$keyText]", stack)
                }
            }
            return ObjectNode.from(fields)
        }
        val keyType = mapKeyTypeOf(property)
        for ((key, element) in value) {
            val keyText = values.writeMapKey(key, keyType, property.mapKeyEnumerated, property.mapKeyTemporal, path)
            if (fields.containsKey(keyText)) {
                throw JpaMappingException(
                    "Map key collision at $path: multiple keys of '${property.name}' are serialized as '$keyText'",
                )
            }
            fields[keyText] = if (element == null) {
                NullNode.INSTANCE
            } else {
                writeEntity(element, depth + 1, "$path[$keyText]", stack)
            }
        }
        return ObjectNode.from(fields)
    }

    private fun writeEntity(
        value: Any,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): ObjectNode {
        val metadata = resolver.resolve(value.javaClass)
        val fields = LinkedHashMap<String, Node>(metadata.properties.size + 1)
        writeObject(value, metadata, depth, path, stack, fields, metadata.convertOverrides)
        return ObjectNode.from(fields)
    }

    private fun writeElementCollection(
        value: Any,
        property: JpaProperty,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): Node {
        val elementType = property.elementType
            ?: throw JpaConfigException("@ElementCollection property '${property.name}' has no element type")
        if (property.map) {
            val map = value as? Map<*, *>
                ?: throw JpaMappingException(
                    "@ElementCollection property '${property.name}' expects a Map at $path " +
                        "but was ${value.javaClass.name}",
                )
            return writeElementCollectionMap(map, property, elementType, depth, path, stack)
        }
        val elements = value as? Collection<*>
            ?: throw JpaMappingException(
                "@ElementCollection property '${property.name}' expects a Collection at $path " +
                    "but was ${value.javaClass.name}",
            )
        val nodes = ArrayList<Node>(elements.size)
        var index = 0
        for (element in elements) {
            val elementPath = "$path[$index]"
            nodes.add(writeElement(element, property, elementType, depth, elementPath, stack))
            index++
        }
        return ArrayNode.from(nodes)
    }

    private fun writeElementCollectionMap(
        value: Map<*, *>,
        property: JpaProperty,
        elementType: Class<*>,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): ObjectNode {
        val keyType = mapKeyTypeOf(property)
        val fields = LinkedHashMap<String, Node>(value.size)
        for ((key, element) in value) {
            val keyText = values.writeMapKey(key, keyType, property.mapKeyEnumerated, property.mapKeyTemporal, path)
            if (fields.containsKey(keyText)) {
                throw JpaMappingException(
                    "Map key collision at $path: multiple keys of '${property.name}' are serialized as '$keyText'",
                )
            }
            fields[keyText] = writeElement(element, property, elementType, depth, "$path[$keyText]", stack)
        }
        return ObjectNode.from(fields)
    }

    private fun writeElement(
        element: Any?,
        property: JpaProperty,
        elementType: Class<*>,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): Node {
        val converter = property.converter
        return when {
            converter != null -> values.writeConverted(element, converter, property.enumType, null, path)
            element == null -> NullNode.INSTANCE
            isScalarValueType(elementType) -> values.writeElement(element, elementType, property.enumType, path)
            else -> writeEntity(element, depth, path, stack)
        }
    }

    // ------------------------------------------------------------------
    // Node -> 实体
    // ------------------------------------------------------------------

    private fun readObject(
        node: ObjectNode,
        metadata: JpaEntityMetadata,
        depth: Int,
        path: String,
        overrides: Map<String, JpaConvertOverride>,
    ): Any {
        val effective = resolveReadMetadata(node, metadata, path)
        val effectiveOverrides = mergedConvertOverrides(effective.convertOverrides, overrides)
        val instance = instantiate(effective.entityClass, path)
        val assigned = HashSet<String>()
        val inheritance = effective.inheritance
        val discriminatorKeys = if (inheritance == null) {
            emptySet()
        } else {
            setOf(inheritance.discriminatorNodeName, inheritance.discriminatorColumnName)
        }
        for ((key, child) in node.asMap()) {
            if (key in discriminatorKeys) {
                continue
            }
            val property = effective.propertyFor(key)
            if (property == null) {
                if (config.unknownPropertyPolicy == JpaUnknownPropertyPolicy.FAIL) {
                    throw JpaMappingException(
                        "Unknown property '$key' for entity ${effective.entityName} " +
                            "(${effective.entityClass.name}) at $path",
                    )
                }
                continue
            }
            if (!assigned.add(property.nodeName)) {
                throw JpaMappingException(
                    "Property '${property.name}' appears more than once at $path " +
                        "(通过别名重复出现: '$key')",
                )
            }
            val propertyOverride = effectiveOverrides[property.name]
            val converter = effectiveConverter(property, propertyOverride)
            if (property.kind == JpaPropertyKind.ASSOCIATION &&
                converter == null &&
                depth >= config.associationDepth
            ) {
                continue
            }
            if (!property.canWrite) {
                continue
            }
            property.write(
                instance,
                readProperty(
                    child,
                    property,
                    depth,
                    "$path.$key",
                    propertyOverride,
                    nestedConvertOverrides(effectiveOverrides, property.name),
                ),
                path,
            )
        }
        return instance
    }

    /**
     * 按 discriminator 选择读入使用的元数据。
     *
     * - 类层次没有 `@Inheritance`/`@DiscriminatorColumn`：使用声明类型元数据；
     * - 有 discriminator 字段：按已注册子类解析，未知值抛异常；
     * - 没有 discriminator 字段：声明类型具体可实例化时回退声明类型，否则抛异常。
     */
    private fun resolveReadMetadata(
        node: ObjectNode,
        declared: JpaEntityMetadata,
        path: String,
    ): JpaEntityMetadata {
        val inheritance = resolver.inheritanceOf(declared.entityClass) ?: return declared
        val discriminatorKey = findDiscriminatorKey(node, inheritance)
        if (discriminatorKey == null) {
            if (isInstantiable(declared.entityClass)) {
                return declared
            }
            throw JpaMappingException(
                "Cannot instantiate abstract entity ${declared.entityClass.name} at $path: document is missing " +
                    "the discriminator field '${inheritance.discriminatorNodeName}'",
            )
        }
        val valueNode = node[discriminatorKey]!!
        val value = readDiscriminatorValue(valueNode, inheritance, "$path.$discriminatorKey")
        val candidates = resolver.discriminatorCandidates(declared.entityClass)
        val hasRegisteredSubtype = candidates.values.any { it != declared.entityClass }
        val target = candidates[value] ?: throw JpaMappingException(
            "Unknown discriminator value '$value' for entity ${declared.entityName} at " +
                "$path.$discriminatorKey; known values: ${candidates.keys.sorted()}${
                    registrationHint(hasRegisteredSubtype)
                }",
        )
        if (!declared.entityClass.isAssignableFrom(target)) {
            throw JpaMappingException(
                "Discriminator value '$value' at $path.$discriminatorKey resolves to ${target.name}, " +
                    "which is not assignable to ${declared.entityClass.name}",
            )
        }
        if (!isInstantiable(target)) {
            throw JpaMappingException(
                "Discriminator value '$value' at $path.$discriminatorKey resolves to abstract entity ${target.name}",
            )
        }
        return resolver.resolve(target)
    }

    private fun findDiscriminatorKey(node: ObjectNode, inheritance: JpaInheritanceMetadata): String? {
        val names = LinkedHashSet<String>()
        names.add(inheritance.discriminatorNodeName)
        names.add(inheritance.discriminatorColumnName)
        val found = names.filter { node.containsKey(it) }
        if (found.size > 1) {
            throw JpaMappingException(
                "Discriminator field appears more than once at $: $found " +
                    "(通过别名重复出现)",
            )
        }
        return found.firstOrNull()
    }

    private fun readDiscriminatorValue(
        node: Node,
        inheritance: JpaInheritanceMetadata,
        path: String,
    ): String = when (inheritance.discriminatorType) {
        DiscriminatorType.INTEGER -> {
            val number = node.asNumberOrNull()
                ?: throw JpaMappingException("Expected number discriminator at $path but was ${node.type}")
            val integer = try {
                number.toInt()
            } catch (e: ArithmeticException) {
                throw JpaMappingException("Discriminator at $path must be a 32-bit integer", e)
            }
            integer.toString()
        }

        else -> node.asStringOrNull()
            ?: throw JpaMappingException("Expected string discriminator at $path but was ${node.type}")
    }

    private fun registrationHint(hasRegisteredSubtype: Boolean): String = if (hasRegisteredSubtype) {
        ""
    } else {
        "; no concrete subtypes are registered, use JpaConfig.builder().entityType(...) to register them"
    }

    private fun readProperty(
        node: Node,
        property: JpaProperty,
        depth: Int,
        path: String,
        propertyOverride: JpaConvertOverride?,
        nestedOverrides: Map<String, JpaConvertOverride>,
    ): Any? {
        val converter = effectiveConverter(property, propertyOverride)
        if (node.isNull && converter == null) {
            if (property.primitive) {
                throw JpaMappingException(
                    "Cannot assign null to primitive property '${property.name}' at $path",
                )
            }
            if (!property.nullable) {
                throw JpaMappingException(
                    "Property '${property.name}' (${property.columnName}) does not allow null at $path",
                )
            }
            return null
        }
        return when (property.kind) {
            JpaPropertyKind.SCALAR -> readScalar(node, property, converter, path)
            JpaPropertyKind.EMBEDDED -> readEmbedded(node, property, depth, path, nestedOverrides)
            JpaPropertyKind.ASSOCIATION -> if (converter != null) {
                values.readConverted(node, converter, property.enumType, property.temporalType, path)
            } else {
                readAssociation(node, property, depth, path)
            }

            JpaPropertyKind.ELEMENT_COLLECTION -> readElementCollection(node, property, depth, path)
        }
    }

    private fun readScalar(
        node: Node,
        property: JpaProperty,
        converter: JpaAttributeConverter?,
        path: String,
    ): Any? = if (converter != null) {
        values.readConverted(node, converter, property.enumType, property.temporalType, path)
    } else if (property.transientProperty) {
        values.readTransient(node, property, path)
    } else {
        values.readProperty(node, property, path)
    }

    private fun readEmbedded(
        node: Node,
        property: JpaProperty,
        depth: Int,
        path: String,
        nestedOverrides: Map<String, JpaConvertOverride>,
    ): Any {
        val metadata = resolver.resolve(property.declaredType)
        val effectiveOverrides = mergedConvertOverrides(metadata.convertOverrides, nestedOverrides)
        return readObject(
            requireObject(node, path, property),
            metadata,
            depth,
            path,
            effectiveOverrides,
        )
    }

    private fun readAssociation(node: Node, property: JpaProperty, depth: Int, path: String): Any {
        val associationKind = property.associationKind
            ?: throw JpaConfigException("Association property '${property.name}' has no association kind")
        val targetType = property.elementType ?: property.declaredType
        val targetMetadata = resolver.resolve(targetType)
        if (associationKind == JpaAssociationKind.ONE_TO_ONE || associationKind == JpaAssociationKind.MANY_TO_ONE) {
            return readObject(
                requireObject(node, path, property),
                targetMetadata,
                depth + 1,
                path,
                targetMetadata.convertOverrides,
            )
        }
        if (property.map) {
            return readAssociationMap(node, property, targetMetadata, depth, path)
        }
        val arrayNode = node.asArrayOrNull()
            ?: throw JpaMappingException(
                "Expected array node for association '${property.name}' at $path but was ${node.type}",
            )
        val collection = newCollectionInstance(property.declaredType, path)
        for (index in 0 until arrayNode.size) {
            val element = arrayNode[index]
            val elementPath = "$path[$index]"
            if (element.isNull) {
                collection.add(null)
                continue
            }
            collection.add(
                readObject(
                    requireObject(element, elementPath),
                    targetMetadata,
                    depth + 1,
                    elementPath,
                    targetMetadata.convertOverrides,
                ),
            )
        }
        return collection
    }

    private fun readAssociationMap(
        node: Node,
        property: JpaProperty,
        targetMetadata: JpaEntityMetadata,
        depth: Int,
        path: String,
    ): Any {
        val objectNode = node.asObjectOrNull()
            ?: throw JpaMappingException(
                "Expected object node for Map association '${property.name}' at $path but was ${node.type}",
            )
        val map = newMapInstance(property.declaredType, path)
        if (property.mapKeyEntity) {
            val keyType = mapKeyTypeOf(property)
            val canonicalKeys = HashSet<String>()
            for ((keyText, child) in objectNode.asMap()) {
                val key = entityKeyFromTextInternal(keyText, keyType, "$path.$keyText")
                val canonical = entityKeyTextInternal(key, keyType, "$path.$keyText")
                if (!canonicalKeys.add(canonical) || map.containsKey(key)) {
                    throw JpaMappingException(
                        "Map key collision at $path: multiple document keys of '${property.name}' " +
                            "resolve to the same key",
                    )
                }
                val elementPath = "$path[$keyText]"
                map[key] = if (child.isNull) {
                    null
                } else {
                    readObject(
                        requireObject(child, elementPath),
                        targetMetadata,
                        depth + 1,
                        elementPath,
                        targetMetadata.convertOverrides,
                    )
                }
            }
            return map
        }
        val keyType = mapKeyTypeOf(property)
        for ((keyText, child) in objectNode.asMap()) {
            val key = values.readMapKey(keyText, keyType, property.mapKeyEnumerated, property.mapKeyTemporal, "$path.$keyText")
            if (map.containsKey(key)) {
                throw JpaMappingException(
                    "Map key collision at $path: multiple document keys of '${property.name}' " +
                        "resolve to the same key",
                )
            }
            val elementPath = "$path[$keyText]"
            map[key] = if (child.isNull) {
                null
            } else {
                readObject(
                    requireObject(child, elementPath),
                    targetMetadata,
                    depth + 1,
                    elementPath,
                    targetMetadata.convertOverrides,
                )
            }
        }
        return map
    }

    private fun readElementCollection(node: Node, property: JpaProperty, depth: Int, path: String): Any {
        val elementType = property.elementType
            ?: throw JpaConfigException("@ElementCollection property '${property.name}' has no element type")
        if (property.map) {
            return readElementCollectionMap(node, property, elementType, depth, path)
        }
        val arrayNode = node.asArrayOrNull()
            ?: throw JpaMappingException(
                "Expected array node for @ElementCollection property '${property.name}' at $path " +
                    "but was ${node.type}",
            )
        val collection = newCollectionInstance(property.declaredType, path)
        for (index in 0 until arrayNode.size) {
            val element = arrayNode[index]
            val elementPath = "$path[$index]"
            if (element.isNull) {
                collection.add(null)
                continue
            }
            collection.add(readElement(element, property, elementType, depth, elementPath))
        }
        return collection
    }

    private fun readElementCollectionMap(
        node: Node,
        property: JpaProperty,
        elementType: Class<*>,
        depth: Int,
        path: String,
    ): Any {
        val objectNode = node.asObjectOrNull()
            ?: throw JpaMappingException(
                "Expected object node for @ElementCollection Map property '${property.name}' at $path " +
                    "but was ${node.type}",
            )
        val keyType = mapKeyTypeOf(property)
        val map = newMapInstance(property.declaredType, path)
        for ((keyText, child) in objectNode.asMap()) {
            val key = values.readMapKey(keyText, keyType, property.mapKeyEnumerated, property.mapKeyTemporal, "$path.$keyText")
            if (map.containsKey(key)) {
                throw JpaMappingException(
                    "Map key collision at $path: multiple document keys of '${property.name}' " +
                        "resolve to the same key",
                )
            }
            val elementPath = "$path[$keyText]"
            map[key] = readElement(child, property, elementType, depth, elementPath)
        }
        return map
    }

    private fun readElement(
        node: Node,
        property: JpaProperty,
        elementType: Class<*>,
        depth: Int,
        path: String,
    ): Any? {
        val converter = property.converter
        return when {
            converter != null -> values.readConverted(node, converter, property.enumType, null, path)
            node.isNull -> null
            isScalarValueType(elementType) -> values.readElement(node, elementType, property.enumType, path)
            else -> {
                val metadata = resolver.resolve(elementType)
                readObject(requireObject(node, path), metadata, depth, path, metadata.convertOverrides)
            }
        }
    }

    /**
     * 解析 Map 键类型：优先使用泛型 `Map<K, V>`/`@MapKeyClass`；`@MapKey` 且泛型键为
     * `Object` 时按目标实体的主键/指定属性类型延迟解析。
     */
    private fun mapKeyTypeOf(property: JpaProperty): Class<*> {
        property.mapKeyType?.let { return it }
        val mapKeyPropertyName = property.mapKeyPropertyName
            ?: throw JpaConfigException(
                "Cannot determine the key type of Map property '${property.name}'; " +
                    "declare Map<K, V>, @MapKeyClass or @MapKey",
            )
        val targetType = property.elementType
            ?: throw JpaConfigException("Map property '${property.name}' has no value type")
        val targetMetadata = resolver.resolve(targetType)
        val keyProperty = if (mapKeyPropertyName.isEmpty()) {
            targetMetadata.properties.firstOrNull { it.id && !it.embeddedId }
                ?: targetMetadata.properties.firstOrNull { it.id }
                ?: throw JpaConfigException(
                    "@MapKey on Map property '${property.name}' requires ${targetType.name} " +
                        "to declare an @Id property",
                )
        } else {
            targetMetadata.properties.firstOrNull { it.name == mapKeyPropertyName }
                ?: targetMetadata.propertyFor(mapKeyPropertyName)
                ?: throw JpaConfigException(
                    "@MapKey(name = '$mapKeyPropertyName') on Map property '${property.name}': " +
                        "${targetType.name} has no such property",
                )
        }
        return keyProperty.declaredType
    }

    // ------------------------------------------------------------------
    // @Convert(attributeName) 覆盖规则
    // ------------------------------------------------------------------

    /** 合并嵌套属性覆盖：后者（父级路径）优先于前者（嵌入类自身声明）。 */
    private fun mergedConvertOverrides(
        base: Map<String, JpaConvertOverride>,
        overrides: Map<String, JpaConvertOverride>,
    ): Map<String, JpaConvertOverride> {
        if (overrides.isEmpty()) {
            return base
        }
        if (base.isEmpty()) {
            return overrides
        }
        val merged = LinkedHashMap<String, JpaConvertOverride>(base.size + overrides.size)
        merged.putAll(base)
        merged.putAll(overrides)
        return merged
    }

    /** 取相对某个属性的子路径覆盖（去掉 `属性名.` 前缀）。 */
    private fun nestedConvertOverrides(
        overrides: Map<String, JpaConvertOverride>,
        propertyName: String,
    ): Map<String, JpaConvertOverride> {
        val prefix = "$propertyName."
        val nested = LinkedHashMap<String, JpaConvertOverride>()
        for ((path, override) in overrides) {
            if (path.length > prefix.length && path.startsWith(prefix)) {
                nested[path.substring(prefix.length)] = override
            }
        }
        return nested
    }

    // ------------------------------------------------------------------
    // 实体作为 Map 键（@MapKeyJoinColumn）
    // ------------------------------------------------------------------

    private fun entityKeyTextInternal(keyEntity: Any, keyType: Class<*>, path: String): String {
        val metadata = resolver.resolve(keyType)
        val identifier = metadata.identifier
            ?: throw JpaConfigException(
                "Entity ${keyType.name} used as Map key at $path must declare an @Id/@EmbeddedId",
            )
        return when (identifier.kind) {
            JpaIdentifierKind.SIMPLE -> idComponentText(identifier.properties[0], keyEntity, path)
            JpaIdentifierKind.EMBEDDED -> {
                val idProperty = identifier.properties[0]
                val idValue = idProperty.read(keyEntity, "$path.${idProperty.name}")
                    ?: throw JpaMappingException(
                        "@EmbeddedId property '${idProperty.name}' must not be null at $path",
                    )
                val idMetadata = resolver.resolve(identifier.embeddedIdType ?: idProperty.declaredType)
                compositeIdText(idMetadata.properties, idValue, "$path.${idProperty.name}")
            }

            JpaIdentifierKind.ID_CLASS -> compositeIdText(identifier.properties, keyEntity, path)
        }
    }

    private fun entityKeyFromTextInternal(text: String, keyType: Class<*>, path: String): Any {
        val metadata = resolver.resolve(keyType)
        val identifier = metadata.identifier
            ?: throw JpaConfigException(
                "Entity ${keyType.name} used as Map key at $path must declare an @Id/@EmbeddedId",
            )
        return when (identifier.kind) {
            JpaIdentifierKind.SIMPLE -> {
                val idProperty = writableIdentifierProperty(identifier.properties[0], path)
                val value = idComponentValue(idProperty, text, path)
                val entity = instantiate(keyType, path)
                idProperty.write(entity, value, path)
                entity
            }

            JpaIdentifierKind.EMBEDDED -> {
                val idProperty = writableIdentifierProperty(identifier.properties[0], path)
                val idType = identifier.embeddedIdType ?: idProperty.declaredType
                val idMetadata = resolver.resolve(idType)
                val values = parseCompositeId(text, idMetadata.properties, path)
                val idInstance = instantiate(idType, path)
                for (property in idMetadata.properties) {
                    if (!property.canRead) {
                        continue
                    }
                    val componentText = values[property.name]
                        ?: throw JpaMappingException(
                            "Composite identifier text at $path is missing component '${property.name}'",
                        )
                    writableIdentifierProperty(property, path)
                        .write(idInstance, idComponentValue(property, componentText, path), path)
                }
                val entity = instantiate(keyType, path)
                idProperty.write(entity, idInstance, path)
                entity
            }

            JpaIdentifierKind.ID_CLASS -> {
                val components = parseCompositeId(text, identifier.properties, path)
                val entity = instantiate(keyType, path)
                for (property in identifier.properties) {
                    val componentText = components[property.name]
                        ?: throw JpaMappingException(
                            "Composite identifier text at $path is missing component '${property.name}'",
                        )
                    writableIdentifierProperty(property, path)
                        .write(entity, idComponentValue(property, componentText, path), path)
                }
                entity
            }
        }
    }

    private fun writableIdentifierProperty(property: JpaProperty, path: String): JpaProperty {
        if (!property.canWrite) {
            throw JpaConfigException(
                "Identifier property '${property.name}' is not writable; it cannot be reconstructed " +
                    "from a Map key at $path",
            )
        }
        return property
    }

    private fun idComponentText(property: JpaProperty, holder: Any, path: String): String {
        val componentPath = "$path.${property.name}"
        val value = property.read(holder, componentPath)
            ?: throw JpaMappingException(
                "Identifier property '${property.name}' must not be null at $path",
            )
        val converter = property.converter
        return if (converter != null) {
            values.writeConvertedMapKey(value, converter, property.enumType, property.temporalType, componentPath)
        } else {
            values.writeMapKey(value, property.declaredType, property.enumType, property.temporalType, componentPath)
        }
    }

    private fun idComponentValue(property: JpaProperty, text: String, path: String): Any {
        val componentPath = "$path.${property.name}"
        val converter = property.converter
        return if (converter != null) {
            values.readConvertedMapKey(text, converter, property.enumType, property.temporalType, componentPath)
        } else {
            values.readMapKey(text, property.declaredType, property.enumType, property.temporalType, componentPath)
        }
    }

    private fun compositeIdText(
        properties: List<JpaProperty>,
        holder: Any,
        path: String,
    ): String {
        val components = properties.filter { it.canRead }
        if (components.isEmpty()) {
            throw JpaConfigException("Composite identifier type at $path has no readable identifier properties")
        }
        for (property in components) {
            if (property.kind != JpaPropertyKind.SCALAR) {
                throw JpaConfigException(
                    "Composite identifier property '${property.name}' at $path is not a basic attribute; " +
                        "nested embedded/composite components are not supported as Map key text",
                )
            }
        }
        return components.joinToString("&") { property ->
            "${property.name}=${escapeKeyComponent(idComponentText(property, holder, path))}"
        }
    }

    private fun parseCompositeId(
        text: String,
        properties: List<JpaProperty>,
        path: String,
    ): Map<String, String> {
        val expected = properties.filter { it.canRead }.map { it.name }.toSet()
        val result = LinkedHashMap<String, String>(expected.size)
        if (text.isEmpty()) {
            throw JpaMappingException("Composite identifier text must not be empty at $path")
        }
        for (part in text.split('&')) {
            val separator = part.indexOf('=')
            if (separator <= 0) {
                throw JpaMappingException(
                    "Invalid composite identifier text '$text' at $path: expected 'name=value' components",
                )
            }
            val name = part.substring(0, separator)
            if (!expected.contains(name)) {
                throw JpaMappingException(
                    "Invalid composite identifier text '$text' at $path: unknown component '$name'",
                )
            }
            if (result.put(name, unescapeKeyComponent(part.substring(separator + 1))) != null) {
                throw JpaMappingException(
                    "Invalid composite identifier text '$text' at $path: duplicate component '$name'",
                )
            }
        }
        if (result.size != expected.size) {
            throw JpaMappingException(
                "Invalid composite identifier text '$text' at $path: missing components " +
                    "${expected - result.keys}",
            )
        }
        return result
    }

    private fun escapeKeyComponent(text: String): String = text
        .replace("%", "%25")
        .replace("&", "%26")
        .replace("=", "%3D")

    private fun unescapeKeyComponent(text: String): String = text
        .replace("%3D", "=")
        .replace("%26", "&")
        .replace("%25", "%")

    private fun requireObject(
        node: Node,
        path: String,
        property: JpaProperty? = null,
    ): ObjectNode {
        val target = if (property == null) {
            "entity"
        } else {
            "property '${property.name}'"
        }
        return node.asObjectOrNull()
            ?: throw JpaMappingException("Expected object node for $target at $path but was ${node.type}")
    }

    private fun isInstantiable(type: Class<*>): Boolean = !type.isInterface && !Modifier.isAbstract(type.modifiers)

    private fun instantiate(type: Class<*>, path: String): Any {
        if (type.isInterface || Modifier.isAbstract(type.modifiers)) {
            throw JpaMappingException(
                "Cannot instantiate entity ${type.name} at $path: type is abstract or an interface",
            )
        }
        val constructor = try {
            type.getDeclaredConstructor()
        } catch (e: NoSuchMethodException) {
            throw JpaMappingException(
                "Entity ${type.name} must declare a no-argument constructor to be instantiated at $path",
                e,
            )
        }
        return try {
            constructor.isAccessible = true
            constructor.newInstance()
        } catch (e: InvocationTargetException) {
            throw JpaMappingException("Constructor of ${type.name} failed at $path", e.cause ?: e)
        } catch (e: ReflectiveOperationException) {
            throw JpaMappingException("Cannot instantiate entity ${type.name} at $path", e)
        } catch (e: IllegalArgumentException) {
            throw JpaMappingException("Cannot instantiate entity ${type.name} at $path", e)
        }
    }
}
