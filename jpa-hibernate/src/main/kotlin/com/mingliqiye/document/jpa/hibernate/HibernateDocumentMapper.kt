package com.mingliqiye.document.jpa.hibernate

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.jpa.JpaConfig
import com.mingliqiye.document.jpa.JpaDocumentMapper
import com.mingliqiye.document.jpa.JpaNullPolicy
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaMappingException
import com.mingliqiye.document.jpa.hibernate.internal.HibernateRuntime
import com.mingliqiye.document.jpa.hibernate.internal.associationTargetClass
import com.mingliqiye.document.jpa.hibernate.internal.columnNameOf
import com.mingliqiye.document.jpa.hibernate.internal.hasExplicitConvertedAssociation
import com.mingliqiye.document.jpa.hibernate.internal.idClassAttributesOf
import com.mingliqiye.document.jpa.hibernate.internal.identifierValue
import com.mingliqiye.document.jpa.hibernate.internal.mapKeyEntityClass
import com.mingliqiye.document.jpa.hibernate.internal.mapKeyFieldName
import com.mingliqiye.document.jpa.hibernate.internal.mapKeyValue
import com.mingliqiye.document.jpa.hibernate.internal.newCollectionInstance
import com.mingliqiye.document.jpa.hibernate.internal.newMapInstance
import com.mingliqiye.document.jpa.hibernate.internal.readAttributeValue
import com.mingliqiye.document.jpa.hibernate.internal.singleIdAttribute
import com.mingliqiye.document.jpa.hibernate.internal.writeAttributeValue
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import jakarta.persistence.metamodel.Attribute
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.MapAttribute
import jakarta.persistence.metamodel.PluralAttribute
import jakarta.persistence.metamodel.SingularAttribute
import org.hibernate.Hibernate
import org.hibernate.Session
import java.util.IdentityHashMap
import java.util.LinkedHashMap

/**
 * Hibernate `Session` / `EntityManager` 与 [Node] 树之间的映射器。
 *
 * 本类是 jpa 模块 [JpaDocumentMapper] 的运行期包装：
 * - 注解元数据、列名/枚举/日期/嵌入映射、命名策略、null 策略等全部复用 jpa 模块，
 *   不重复实现元数据；
 * - 传入 `EntityManager`/`Session` 时额外处理 Hibernate 运行期语义：
 *   通过 `Hibernate.initialize` + `Hibernate.unproxy` 初始化懒加载集合/代理、
 *   按关联深度递归、检测循环引用、并把只含标识符的关联节点解析为持久化上下文中的
 *   受管实例（[fromNode]）；
 * - 不打开/关闭 `Session`、不开启/提交事务、不持久化/合并实体，`Session` 生命周期与
 *   事务完全由调用方管理。
 *
 * 线程安全性：映射器本身不保存可变状态（每次调用创建独立的运行期上下文），
 * 可跨线程复用；但传入的 `EntityManager`/`Session` 不是线程安全的。
 *
 * @param jpaConfig jpa 模块映射配置（命名策略、transient、关联深度、null 策略、
 * 未知属性策略、访问策略）；关联深度在本模块中对 `EntityManager` 感知的映射同样生效
 * @param config Hibernate 运行期配置（懒加载策略、循环引用策略）
 */
public class HibernateDocumentMapper @JvmOverloads constructor(
    public val jpaConfig: JpaConfig = JpaConfig.builder().build(),
    public val config: HibernateConfig = HibernateConfig.builder().build(),
) {

    private val jpaMapper: JpaDocumentMapper = JpaDocumentMapper(jpaConfig)
    private val baseMapper: JpaDocumentMapper = JpaDocumentMapper(baseConfig(jpaConfig))

    // ------------------------------------------------------------------
    // 实体 -> Node
    // ------------------------------------------------------------------

    /**
     * 将实体转换为对象节点，不传入 `EntityManager`/`Session`。
     *
     * 该路径完全委托给 jpa 模块，只适用于已经 detach 且所需的关联/集合都已初始化的
     * 对象图：不会调用 `Hibernate.unproxy`/`Hibernate.initialize`，遇到未初始化代理时
     * 按 jpa 模块的字段反射行为处理（可能得到 `null`，或在遍历未初始化集合时抛
     * Hibernate 的懒加载异常）。
     *
     * @param entity 实体对象，不能为 `null`
     * @return 对象节点
     */
    public fun toNode(entity: Any): ObjectNode = jpaMapper.toNode(entity)

    /**
     * 将实体转换为对象节点；传入 `EntityManager` 时启用 Hibernate 运行期语义。
     *
     * 传入的 `EntityManager` 必须处于打开状态。为 `null` 时等价于 [toNode] 的单参数
     * 重载。处理规则：
     * - 根实体和递归访问到的关联值都会先经过 `Hibernate.initialize`（按
     *   [HibernateConfig.lazyLoadPolicy]）与 `Hibernate.unproxy`，因此
     *   `Session.get`/`getReference` 得到的代理可以直接映射；
     * - 非关联的懒加载集合（`@ElementCollection`）与字节码增强懒加载属性同样按
     *   [HibernateConfig.lazyLoadPolicy] 处理；
     * - 关联按 [JpaConfig.associationDepth] 展开（根实体的关联是第 1 层）；
     * - 同一对象出现在递归路径上时按 [HibernateConfig.cyclePolicy] 抛异常或省略；
     * - 非关联字段的映射（列名、枚举、日期、`@Embedded`、`@ElementCollection` 等）
     *   仍由 jpa 模块完成。
     *
     * 映射过程可能触发额外 SELECT 以初始化懒加载数据；不会修改持久化状态，
     * 但初始化懒加载集合属于对实体状态的可见副作用。
     *
     * @param entity 实体对象或其 Hibernate 代理，不能为 `null`
     * @param entityManager 可选的 `EntityManager`（Hibernate `Session` 也是
     * `EntityManager`，两种入口都可以传入）
     * @return 对象节点
     * @throws com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaLazyLoadException
     * 需要初始化懒加载数据但无法初始化时抛出
     * @throws HibernateJpaMappingException 循环引用、代理还原失败、实体不受当前
     * `EntityManager` 管理、`EntityManager` 已关闭等情况下抛出
     */
    public fun toNode(entity: Any, entityManager: EntityManager?): ObjectNode = if (entityManager == null) {
        toNode(entity)
    } else {
        toNodeWithManager(entity, entityManager)
    }

    /**
     * 将实体转换为对象节点，显式传入 Hibernate [Session]。
     *
     * 语义与 [toNode] 的 `EntityManager` 重载一致；该重载只用于让 Session 入口更直观。
     *
     * @param entity 实体对象或其 Hibernate 代理，不能为 `null`
     * @param session 打开状态的 Hibernate `Session`
     * @return 对象节点
     */
    public fun toNode(entity: Any, session: Session): ObjectNode = toNodeWithManager(entity, session)

    // ------------------------------------------------------------------
    // Node -> 实体
    // ------------------------------------------------------------------

    /**
     * 将对象节点反序列化为实体，不传入 `EntityManager`/`Session`。
     *
     * 完全委托给 jpa 模块：返回全新构建的 detach 实体图，关联按
     * [JpaConfig.associationDepth] 递归构建为普通新实例。
     *
     * @param node 文档节点，必须是对象节点
     * @param entityType 目标实体类型
     * @return 实体对象
     */
    public fun <T : Any> fromNode(node: Node, entityType: Class<T>): T = jpaMapper.fromNode(node, entityType)

    /**
     * 将对象节点反序列化为实体；传入 `EntityManager` 时启用引用解析。
     *
     * 返回的根实体始终是根据节点新建的 detach 实例（不会被持久化、合并或附加到
     * 持久化上下文）。传入打开的 `EntityManager` 时额外提供：
     * - 当关联值是只含标识符的引用对象（例如 `{"id": 5}`）时，通过
     *   `EntityManager.find` 解析为持久化上下文中的受管实例；找不到时退化为只带
     *   标识符的 detach 实例；
     * - 其余关联值按节点内容递归构建 detach 实体（受 [JpaConfig.associationDepth]
     *   限制）；
     * - 非关联字段的映射与校验仍由 jpa 模块完成。
     *
     * 引用解析只支持单个、非复合（`@Id` 基本类型）标识符，且节点字段名必须是
     * 标识符属性名/列名的别名。
     *
     * 为 `null` 时等价于 [fromNode] 的两参数重载。
     *
     * @param node 文档节点，必须是对象节点
     * @param entityType 目标实体类型
     * @param entityManager 可选的 `EntityManager`（Hibernate `Session` 也是
     * `EntityManager`）
     * @return 实体对象
     * @throws HibernateJpaMappingException 关联节点类型不匹配、必填关联为 null、
     * 目标类型不受 `EntityManager` 管理、`EntityManager` 已关闭等情况下抛出
     */
    public fun <T : Any> fromNode(node: Node, entityType: Class<T>, entityManager: EntityManager?): T = if (entityManager == null) {
        fromNode(node, entityType)
    } else {
        fromNodeWithManager(node, entityType, entityManager)
    }

    /**
     * 将对象节点反序列化为实体，显式传入 Hibernate [Session]。
     *
     * 语义与 [fromNode] 的 `EntityManager` 重载一致。
     *
     * @param node 文档节点，必须是对象节点
     * @param entityType 目标实体类型
     * @param session 打开状态的 Hibernate `Session`
     * @return 实体对象
     */
    public fun <T : Any> fromNode(node: Node, entityType: Class<T>, session: Session): T = fromNodeWithManager(node, entityType, session)

    // ------------------------------------------------------------------
    // 按标识符加载并转换
    // ------------------------------------------------------------------

    /**
     * 通过 `EntityManager.find` 按标识符加载实体并转换为对象节点。
     *
     * 使用传入的 `EntityManager` 初始化懒加载数据，因此加载到的关联代理可以在
     * 同一次调用中展开。
     *
     * @param entityType 实体类型
     * @param id 标识符值
     * @param entityManager 打开状态的 `EntityManager`
     * @return 对象节点；实体不存在时返回 `null`
     * @throws HibernateJpaMappingException `EntityManager` 已关闭时抛出
     */
    public fun <T : Any> findAsNode(entityType: Class<T>, id: Any, entityManager: EntityManager): ObjectNode? {
        requireOpenEntityManager(entityManager)
        val entity = entityManager.find(entityType, id) ?: return null
        return toNodeWithManager(entity, entityManager)
    }

    /**
     * 通过 `Session.get` 按标识符加载实体并转换为对象节点。
     *
     * @param entityType 实体类型
     * @param id 标识符值
     * @param session 打开状态的 Hibernate `Session`
     * @return 对象节点；实体不存在时返回 `null`
     * @throws HibernateJpaMappingException `Session` 已关闭时抛出
     */
    public fun <T : Any> getAsNode(entityType: Class<T>, id: Any, session: Session): ObjectNode? {
        requireOpenEntityManager(session)
        val entity = session.get(entityType, id) ?: return null
        return toNodeWithManager(entity, session)
    }

    // ------------------------------------------------------------------
    // 内部实现：实体 -> Node
    // ------------------------------------------------------------------

    private fun toNodeWithManager(entity: Any, entityManager: EntityManager): ObjectNode {
        val runtime = HibernateRuntime(entityManager, config)
        val resolved = runtime.resolveEntity(entity, "$")
        return writeObjectNode(resolved, runtime, 0, "$", IdentityHashMap())
    }

    private fun writeObjectNode(
        entity: Any,
        runtime: HibernateRuntime,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): ObjectNode {
        val entityType = runtime.entityTypeOf(Hibernate.getClass(entity))
        val previous = stack.put(entity, path)
        if (previous != null) {
            throw circularReference(path, entity, previous)
        }
        try {
            runtime.prepareBaseState(entity, entityType, path)
            val fields = LinkedHashMap<String, Node>(entityType.attributes.size + 1)
            fields.putAll(baseMapper.toNode(entity).asMap())
            if (depth < jpaConfig.associationDepth) {
                for (attribute in entityType.attributes) {
                    if (!attribute.isAssociation || hasExplicitConvertedAssociation(attribute)) {
                        // 显式 @Convert 的关系属性已由 jpa 元数据映射为基本值。
                        continue
                    }
                    writeAssociation(entity, attribute, runtime, depth, path, stack, fields)
                }
            }
            return ObjectNode.from(fields)
        } finally {
            stack.remove(entity)
        }
    }

    private fun writeAssociation(
        owner: Any,
        attribute: Attribute<*, *>,
        runtime: HibernateRuntime,
        depth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
        fields: MutableMap<String, Node>,
    ) {
        val key = jpaConfig.namingStrategy.translate(attribute.name)
        val attributePath = "$path.$key"
        val value = readAttributeValue(owner, attribute, attributePath)
        if (value == null) {
            if (jpaConfig.nullPolicy == JpaNullPolicy.INCLUDE) {
                fields[key] = NullNode.INSTANCE
            }
            return
        }
        val node = if (attribute is PluralAttribute<*, *, *>) {
            writeAssociationCollection(value, attribute, runtime, depth + 1, attributePath, stack)
        } else {
            writeAssociationEntity(value, runtime, depth + 1, attributePath, stack)
        }
        if (node != null) {
            fields[key] = node
        }
    }

    private fun writeAssociationCollection(
        value: Any,
        attribute: PluralAttribute<*, *, *>,
        runtime: HibernateRuntime,
        childDepth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): Node {
        if (!Hibernate.isInitialized(value)) {
            runtime.initializeLazy(value, "Association collection '${attribute.name}'", path)
        }
        if (attribute is MapAttribute<*, *, *> && value is Map<*, *>) {
            return writeAssociationMap(value, attribute, runtime, childDepth, path, stack)
        }
        val elements = value as? Collection<*>
            ?: throw HibernateJpaMappingException(
                "Association '${attribute.name}' expects a Collection at $path but was ${value.javaClass.name}",
            )
        val nodes = ArrayList<Node>(elements.size)
        var index = 0
        for (element in elements) {
            val elementPath = "$path[$index]"
            if (element == null) {
                nodes.add(NullNode.INSTANCE)
            } else {
                writeAssociationEntity(element, runtime, childDepth, elementPath, stack)?.let(nodes::add)
            }
            index++
        }
        return ArrayNode.from(nodes)
    }

    /**
     * 写出 Map 关联：对象节点，字段名为字符串化的 Map 键，值按关联实体递归写出。
     *
     * 键格式与 jpa 模块一致（`@MapKey`/`@MapKeyColumn` 只影响 JPA 持久化、
     * 不改变文档键；`@MapKeyEnumerated`/`@MapKeyTemporal` 决定枚举/日期键文本）。
     * 同一文档字段名重复（键字符串冲突）时抛异常。
     */
    private fun writeAssociationMap(
        value: Map<*, *>,
        attribute: MapAttribute<*, *, *>,
        runtime: HibernateRuntime,
        childDepth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): ObjectNode {
        val keyEntityClass = mapKeyEntityClass(attribute)
        val fields = LinkedHashMap<String, Node>(value.size)
        if (keyEntityClass != null) {
            for ((key, element) in value) {
                if (key == null) {
                    throw HibernateJpaMappingException("Map key must not be null at $path")
                }
                // 懒加载代理键先还原，实体键文本按标识符生成（与 jpa 模块规则一致）。
                val resolvedKey = runtime.resolveEntity(key, "$path.<key>")
                val keyText = baseMapper.entityKeyText(resolvedKey, keyEntityClass)
                if (fields.containsKey(keyText)) {
                    throw HibernateJpaMappingException(
                        "Map key collision at $path: multiple keys of '${attribute.name}' " +
                            "are serialized as '$keyText'",
                    )
                }
                if (element == null) {
                    fields[keyText] = NullNode.INSTANCE
                } else {
                    writeAssociationEntity(element, runtime, childDepth, "$path[$keyText]", stack)?.let {
                        fields[keyText] = it
                    }
                }
            }
            return ObjectNode.from(fields)
        }
        for ((key, element) in value) {
            val keyText = mapKeyFieldName(attribute, key, path)
            if (fields.containsKey(keyText)) {
                throw HibernateJpaMappingException(
                    "Map key collision at $path: multiple keys of '${attribute.name}' " +
                        "are serialized as '$keyText'",
                )
            }
            if (element == null) {
                fields[keyText] = NullNode.INSTANCE
            } else {
                writeAssociationEntity(element, runtime, childDepth, "$path[$keyText]", stack)?.let {
                    fields[keyText] = it
                }
            }
        }
        return ObjectNode.from(fields)
    }

    private fun writeAssociationEntity(
        value: Any,
        runtime: HibernateRuntime,
        childDepth: Int,
        path: String,
        stack: IdentityHashMap<Any, String>,
    ): ObjectNode? {
        val resolved = runtime.resolveEntity(value, path)
        val previous = stack[resolved]
        if (previous != null) {
            return when (config.cyclePolicy) {
                HibernateCyclePolicy.FAIL -> throw circularReference(path, resolved, previous)
                HibernateCyclePolicy.OMIT -> null
            }
        }
        return writeObjectNode(resolved, runtime, childDepth, path, stack)
    }

    private fun circularReference(path: String, instance: Any, previousPath: String): HibernateJpaMappingException = HibernateJpaMappingException(
        "Circular reference detected at $path: the same ${instance.javaClass.name} instance " +
            "was already visited at $previousPath",
    )

    // ------------------------------------------------------------------
    // 内部实现：Node -> 实体
    // ------------------------------------------------------------------

    private fun <T : Any> fromNodeWithManager(node: Node, entityType: Class<T>, entityManager: EntityManager): T {
        val runtime = HibernateRuntime(entityManager, config)
        @Suppress("UNCHECKED_CAST")
        return readEntity(node, entityType, runtime, 0, "$") as T
    }

    private fun readEntity(
        node: Node,
        entityType: Class<*>,
        runtime: HibernateRuntime,
        depth: Int,
        path: String,
    ): Any {
        val objectNode = node.asObjectOrNull()
            ?: throw HibernateJpaMappingException(
                "Expected object node for entity ${entityType.name} at $path but was ${node.type}",
            )
        runtime.entityTypeOf(entityType)
        // baseMapper 按 jpa 元数据构建非关联字段；继承层次中会按 discriminator 选择子类，
        // 因此关联遍历使用实例的实际实体类型（子类可能声明额外关联）。
        val instance = baseMapper.fromNode(objectNode, entityType)
        val actualEntityType = runtime.entityTypeOf(instance.javaClass)
        if (depth < jpaConfig.associationDepth) {
            for (attribute in actualEntityType.attributes) {
                if (!attribute.isAssociation || hasExplicitConvertedAssociation(attribute)) {
                    // 显式 @Convert 的关系属性已由 jpa 元数据映射为基本值。
                    continue
                }
                readAssociation(objectNode, attribute, instance, runtime, depth, path)
            }
        }
        return instance
    }

    private fun readAssociation(
        objectNode: ObjectNode,
        attribute: Attribute<*, *>,
        instance: Any,
        runtime: HibernateRuntime,
        depth: Int,
        path: String,
    ) {
        val key = jpaConfig.namingStrategy.translate(attribute.name)
        val valueNode = objectNode[key] ?: objectNode[attribute.name] ?: return
        val attributePath = "$path.$key"
        val value = readAssociationNode(valueNode, attribute, runtime, depth + 1, attributePath)
        writeAttributeValue(instance, attribute, value)
    }

    private fun readAssociationNode(
        node: Node,
        attribute: Attribute<*, *>,
        runtime: HibernateRuntime,
        childDepth: Int,
        path: String,
    ): Any? {
        if (node.isNull) {
            if (attribute is SingularAttribute<*, *> && !attribute.isOptional) {
                throw HibernateJpaMappingException("Property '${attribute.name}' does not allow null at $path")
            }
            return null
        }
        val targetType = associationTargetClass(attribute, path)
        if (attribute is MapAttribute<*, *, *>) {
            return readAssociationMap(node, attribute, targetType, runtime, childDepth, path)
        }
        if (attribute is PluralAttribute<*, *, *>) {
            val arrayNode = node.asArrayOrNull()
                ?: throw HibernateJpaMappingException(
                    "Expected array node for association '${attribute.name}' at $path but was ${node.type}",
                )
            val collection = newCollectionInstance(attribute.javaType, path)
            for (index in 0 until arrayNode.size) {
                val element = arrayNode[index]
                val elementPath = "$path[$index]"
                if (element.isNull) {
                    collection.add(null)
                } else {
                    collection.add(readAssociationTarget(element, targetType, runtime, childDepth, elementPath))
                }
            }
            return collection
        }
        return readAssociationTarget(node, targetType, runtime, childDepth, path)
    }

    /**
     * 读入 Map 关联：对象节点字段名为字符串化的 Map 键，按 jpa 模块相同的键规则
     * 反向转换为 Map 键；同一键文本解析出重复键时抛异常。
     */
    private fun readAssociationMap(
        node: Node,
        attribute: MapAttribute<*, *, *>,
        targetType: Class<*>,
        runtime: HibernateRuntime,
        depth: Int,
        path: String,
    ): Any {
        val objectNode = node.asObjectOrNull()
            ?: throw HibernateJpaMappingException(
                "Expected object node for Map association '${attribute.name}' at $path but was ${node.type}",
            )
        val map = newMapInstance(attribute.javaType, path)
        val keyEntityClass = mapKeyEntityClass(attribute)
        if (keyEntityClass != null) {
            val canonicalKeys = HashSet<String>()
            for ((keyText, child) in objectNode.asMap()) {
                val detachKey = baseMapper.entityKeyFromText(keyText, keyEntityClass)
                val canonical = baseMapper.entityKeyText(detachKey, keyEntityClass)
                if (!canonicalKeys.add(canonical)) {
                    throw HibernateJpaMappingException(
                        "Map key collision at $path: multiple document keys of '${attribute.name}' " +
                            "resolve to the same key",
                    )
                }
                val key = resolveEntityKeyReference(detachKey, keyEntityClass, runtime, "$path.$keyText")
                val elementPath = "$path[$keyText]"
                map[key] = if (child.isNull) {
                    null
                } else {
                    readAssociationTarget(child, targetType, runtime, depth, elementPath)
                }
            }
            return map
        }
        for ((keyText, child) in objectNode.asMap()) {
            val key = mapKeyValue(attribute, keyText, "$path.$keyText")
            if (map.containsKey(key)) {
                throw HibernateJpaMappingException(
                    "Map key collision at $path: multiple document keys of '${attribute.name}' " +
                        "resolve to the same key",
                )
            }
            map[key] = if (child.isNull) {
                null
            } else {
                readAssociationTarget(child, targetType, runtime, depth, "$path[$keyText]")
            }
        }
        return map
    }

    /**
     * 实体 Map 键（`@MapKeyJoinColumn`）：detach 键实体 → 持久化上下文中的受管实例。
     *
     * 找不到时回退为只带标识符的 detach 实例（与关联引用解析语义一致）。
     */
    private fun resolveEntityKeyReference(
        detachKey: Any,
        keyType: Class<*>,
        runtime: HibernateRuntime,
        path: String,
    ): Any {
        val entityType = runtime.entityTypeOf(keyType)
        val id = identifierValue(detachKey, entityType, path) ?: return detachKey
        return try {
            runtime.entityManager.find(keyType, id) ?: detachKey
        } catch (e: PersistenceException) {
            throw HibernateJpaMappingException("Cannot resolve Map key reference at $path", e)
        } catch (e: IllegalArgumentException) {
            throw HibernateJpaMappingException("Cannot resolve Map key reference at $path", e)
        } catch (e: IllegalStateException) {
            throw HibernateJpaMappingException(
                "Cannot resolve Map key reference at $path: the EntityManager is closed",
                e,
            )
        }
    }

    private fun readAssociationTarget(
        node: Node,
        targetType: Class<*>,
        runtime: HibernateRuntime,
        depth: Int,
        path: String,
    ): Any = referenceTarget(node, targetType, runtime, path) ?: readEntity(node, targetType, runtime, depth, path)

    /**
     * 尝试把只含标识符的关联节点解析为持久化上下文中的受管实例。
     *
     * 支持：
     * - 单基本类型主键：`{"id": 5}`（行为与旧版本一致）；
     * - `@EmbeddedId` 复合主键：`{"key": {"orderNo": "A-1", "region": "EU"}}`；
     * - `@IdClass` 复合主键：`{"idA": 1, "idB": 2}`（节点键均为 id 属性别名）。
     *
     * @return 解析结果；节点不是标识符引用时返回 `null`
     */
    private fun referenceTarget(
        node: Node,
        targetType: Class<*>,
        runtime: HibernateRuntime,
        path: String,
    ): Any? {
        val objectNode = node.asObjectOrNull() ?: return null
        val entityMetadata = runtime.entityTypeOf(targetType)
        if (!isReferenceNode(objectNode, entityMetadata)) {
            return null
        }
        val detached = baseMapper.fromNode(objectNode, targetType)
        val id = identifierValue(detached, entityMetadata, path) ?: return detached
        return try {
            runtime.entityManager.find(targetType, id) ?: detached
        } catch (e: PersistenceException) {
            throw HibernateJpaMappingException("Cannot resolve association reference at $path", e)
        } catch (e: IllegalArgumentException) {
            throw HibernateJpaMappingException("Cannot resolve association reference at $path", e)
        } catch (e: IllegalStateException) {
            throw HibernateJpaMappingException(
                "Cannot resolve association reference at $path: the EntityManager is closed",
                e,
            )
        }
    }

    /**
     * 判断对象节点是否只由目标实体的标识符属性组成（单主键/`@EmbeddedId`/`@IdClass`）。
     */
    private fun isReferenceNode(objectNode: ObjectNode, entityType: EntityType<*>): Boolean {
        val singleId = singleIdAttribute(entityType)
        if (singleId != null) {
            return objectNode.size == 1 && objectNode.keys.first() in idAliases(singleId)
        }
        val idClassAttributes = idClassAttributesOf(entityType)
        if (idClassAttributes.isEmpty()) {
            return false
        }
        val aliasGroups = idClassAttributes.map { idAliases(it) }
        val keys = objectNode.keys
        if (keys.isEmpty()) {
            return false
        }
        if (keys.any { key -> aliasGroups.none { key in it } }) {
            return false
        }
        return aliasGroups.all { aliases -> keys.any { it in aliases } }
    }

    private fun idAliases(attribute: SingularAttribute<*, *>): Set<String> {
        val aliases = LinkedHashSet<String>()
        aliases.add(jpaConfig.namingStrategy.translate(attribute.name))
        aliases.add(attribute.name)
        val column = columnNameOf(attribute)
        if (column != null) {
            aliases.add(jpaConfig.namingStrategy.translate(column))
            aliases.add(column)
        }
        return aliases
    }

    private fun requireOpenEntityManager(entityManager: EntityManager) {
        if (!entityManager.isOpen) {
            throw HibernateJpaMappingException("EntityManager is closed; an open EntityManager/Session is required")
        }
    }
}

/**
 * 构造只映射非关联属性的 jpa 配置。
 *
 * 关联属性由本模块基于 Hibernate 运行期元数据遍历，因此基础映射器把关联深度设为 0，
 * 避免 jpa 模块按字段反射访问 Hibernate 代理/懒加载集合。
 *
 * 其余配置（包括 autoApply 转换器与用于多态读入的实体注册）原样透传，保证
 * Hibernate 感知路径与纯 jpa 路径的标量/转换器/多态语义一致。
 */
private fun baseConfig(config: JpaConfig): JpaConfig {
    val builder = JpaConfig.builder()
        .namingStrategy(config.namingStrategy)
        .includeTransient(config.includeTransient)
        .associationDepth(0)
        .nullPolicy(config.nullPolicy)
        .unknownPropertyPolicy(config.unknownPropertyPolicy)
        .accessType(config.accessType)
    config.autoApplyConverters.forEach { builder.autoApplyConverter(it) }
    config.entityTypes.forEach { builder.entityType(it) }
    return builder.build()
}
