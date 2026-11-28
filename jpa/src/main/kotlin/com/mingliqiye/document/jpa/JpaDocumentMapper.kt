package com.mingliqiye.document.jpa

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.jpa.exception.JpaConfigException
import com.mingliqiye.document.jpa.exception.JpaMappingException
import com.mingliqiye.document.jpa.internal.JpaAssociationKind
import com.mingliqiye.document.jpa.internal.JpaEntityMetadata
import com.mingliqiye.document.jpa.internal.JpaMetadataResolver
import com.mingliqiye.document.jpa.internal.JpaProperty
import com.mingliqiye.document.jpa.internal.JpaPropertyKind
import com.mingliqiye.document.jpa.internal.JpaValueConverter
import com.mingliqiye.document.jpa.internal.isScalarValueType
import com.mingliqiye.document.jpa.internal.newCollectionInstance
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
     * （循环引用）时抛出 [JpaMappingException]。
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
        val fields = LinkedHashMap<String, Node>(metadata.properties.size)
        writeObject(entity, metadata, 0, "$", IdentityHashMap(), fields)
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
     * @param node 文档节点，必须是对象节点
     * @param entityType 目标实体类型
     * @return 实体对象
     * @throws JpaMappingException 节点类型不匹配、未知属性、属性不可写、值类型不匹配、
     * 循环或无法实例化时抛出
     * @throws JpaConfigException 实体元数据非法时抛出
     */
    public fun <T : Any> fromNode(node: Node, entityType: Class<T>): T {
        val metadata = resolver.resolve(entityType)
        val objectNode = node.asObjectOrNull()
            ?: throw JpaMappingException(
                "Expected object node for entity ${metadata.entityName} at $ but was ${node.type}",
            )
        @Suppress("UNCHECKED_CAST")
        return readObject(objectNode, metadata, 0, "$") as T
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
            result.add(readObject(requireObject(element, "$[$index]"), metadata, 0, "$[$index]") as T)
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

    private fun writeObject(
        instance: Any,
        metadata: JpaEntityMetadata,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
        fields: MutableMap<String, Node>,
    ) {
        val previous = stack.put(instance, path)
        if (previous != null) {
            throw JpaMappingException(
                "Circular reference detected at $path: the same ${instance.javaClass.name} instance " +
                    "was already visited at $previous",
            )
        }
        try {
            for (property in metadata.readableProperties()) {
                writeProperty(instance, property, depth, "$path.${property.nodeName}", stack, fields)
            }
        } finally {
            stack.remove(instance)
        }
    }

    private fun writeProperty(
        instance: Any,
        property: JpaProperty,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
        fields: MutableMap<String, Node>,
    ) {
        if (property.kind == JpaPropertyKind.ASSOCIATION && depth >= config.associationDepth) {
            return
        }
        val value = property.read(instance, path)
        if (value == null) {
            if (config.nullPolicy == JpaNullPolicy.INCLUDE) {
                fields[property.nodeName] = NullNode.INSTANCE
            }
            return
        }
        fields[property.nodeName] = when (property.kind) {
            JpaPropertyKind.SCALAR -> if (property.transientProperty) {
                values.writeTransient(value, property, path)
            } else {
                values.writeProperty(value, property, path)
            }

            JpaPropertyKind.EMBEDDED -> writeEmbedded(value, property, depth, path, stack)
            JpaPropertyKind.ASSOCIATION -> writeAssociation(value, property, depth, path, stack)
            JpaPropertyKind.ELEMENT_COLLECTION -> writeElementCollection(value, property, depth, path, stack)
        }
    }

    private fun writeEmbedded(
        value: Any,
        property: JpaProperty,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): ObjectNode {
        val metadata = resolver.resolve(property.declaredType)
        val fields = LinkedHashMap<String, Node>(metadata.properties.size)
        writeObject(value, metadata, depth, path, stack, fields)
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

    private fun writeEntity(
        value: Any,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): ObjectNode {
        val metadata = resolver.resolve(value.javaClass)
        val fields = LinkedHashMap<String, Node>(metadata.properties.size)
        writeObject(value, metadata, depth, path, stack, fields)
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
        val elements = value as? Collection<*>
            ?: throw JpaMappingException(
                "@ElementCollection property '${property.name}' expects a Collection at $path " +
                    "but was ${value.javaClass.name}",
            )
        val nodes = ArrayList<Node>(elements.size)
        var index = 0
        for (element in elements) {
            val elementPath = "$path[$index]"
            nodes.add(
                if (element == null) {
                    NullNode.INSTANCE
                } else if (isScalarValueType(elementType)) {
                    values.writeElement(element, elementType, property.enumType, elementPath)
                } else {
                    writeEntity(element, depth, elementPath, stack)
                },
            )
            index++
        }
        return ArrayNode.from(nodes)
    }

    private fun readObject(
        node: ObjectNode,
        metadata: JpaEntityMetadata,
        depth: Int,
        path: String,
    ): Any {
        val instance = instantiate(metadata.entityClass, path)
        val assigned = HashSet<String>()
        for ((key, child) in node.asMap()) {
            val property = metadata.propertyFor(key)
            if (property == null) {
                if (config.unknownPropertyPolicy == JpaUnknownPropertyPolicy.FAIL) {
                    throw JpaMappingException(
                        "Unknown property '$key' for entity ${metadata.entityName} " +
                            "(${metadata.entityClass.name}) at $path",
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
            if (property.kind == JpaPropertyKind.ASSOCIATION && depth >= config.associationDepth) {
                continue
            }
            if (!property.canWrite) {
                continue
            }
            property.write(instance, readProperty(child, property, depth, "$path.$key"), path)
        }
        return instance
    }

    private fun readProperty(node: Node, property: JpaProperty, depth: Int, path: String): Any? {
        if (node.isNull) {
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
            JpaPropertyKind.SCALAR -> if (property.transientProperty) {
                values.readTransient(node, property, path)
            } else {
                values.readProperty(node, property, path)
            }

            JpaPropertyKind.EMBEDDED -> readEmbedded(node, property, depth, path)
            JpaPropertyKind.ASSOCIATION -> readAssociation(node, property, depth, path)
            JpaPropertyKind.ELEMENT_COLLECTION -> readElementCollection(node, property, depth, path)
        }
    }

    private fun readEmbedded(node: Node, property: JpaProperty, depth: Int, path: String): Any {
        val metadata = resolver.resolve(property.declaredType)
        return readObject(requireObject(node, path, property), metadata, depth, path)
    }

    private fun readAssociation(node: Node, property: JpaProperty, depth: Int, path: String): Any {
        val associationKind = property.associationKind
            ?: throw JpaConfigException("Association property '${property.name}' has no association kind")
        val targetType = property.elementType ?: property.declaredType
        val targetMetadata = resolver.resolve(targetType)
        if (associationKind == JpaAssociationKind.ONE_TO_ONE || associationKind == JpaAssociationKind.MANY_TO_ONE) {
            return readObject(requireObject(node, path, property), targetMetadata, depth + 1, path)
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
            collection.add(readObject(requireObject(element, elementPath), targetMetadata, depth + 1, elementPath))
        }
        return collection
    }

    private fun readElementCollection(node: Node, property: JpaProperty, depth: Int, path: String): Any {
        val elementType = property.elementType
            ?: throw JpaConfigException("@ElementCollection property '${property.name}' has no element type")
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
            val value = if (isScalarValueType(elementType)) {
                values.readElement(element, elementType, property.enumType, elementPath)
            } else {
                readObject(requireObject(element, elementPath), resolver.resolve(elementType), depth, elementPath)
            }
            collection.add(value)
        }
        return collection
    }

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
