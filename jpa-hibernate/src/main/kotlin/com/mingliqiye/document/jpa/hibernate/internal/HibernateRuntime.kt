package com.mingliqiye.document.jpa.hibernate.internal

import com.mingliqiye.document.jpa.hibernate.HibernateConfig
import com.mingliqiye.document.jpa.hibernate.HibernateLazyLoadPolicy
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaLazyLoadException
import com.mingliqiye.document.jpa.hibernate.exception.HibernateJpaMappingException
import jakarta.persistence.Column
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import jakarta.persistence.metamodel.Attribute
import jakarta.persistence.metamodel.EntityType
import jakarta.persistence.metamodel.Metamodel
import jakarta.persistence.metamodel.PluralAttribute
import org.hibernate.Hibernate
import org.hibernate.HibernateException
import org.hibernate.Session
import org.hibernate.proxy.HibernateProxy
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.LinkedHashSet
import java.util.TreeSet

/**
 * 单次映射调用使用的 Hibernate 运行期上下文。
 *
 * 封装 `EntityManager`/`Session`、JPA 元模型、代理还原与懒加载初始化。
 * 每次调用创建新实例，不跨线程共享。
 */
internal class HibernateRuntime(
    val entityManager: EntityManager,
    val config: HibernateConfig,
) {

    init {
        if (!entityManager.isOpen) {
            throw HibernateJpaMappingException(
                "EntityManager is closed; Hibernate-aware mapping requires an open EntityManager/Session. " +
                    "Map a detached entity graph without passing an EntityManager instead.",
            )
        }
    }

    /** 由传入 `EntityManager` 的工厂得到的 JPA 元模型。 */
    val metamodel: Metamodel = entityManager.entityManagerFactory.metamodel

    private var sessionResolved: Boolean = false
    private var resolvedSession: Session? = null

    /**
     * 返回 `EntityManager` 对应的 Hibernate [Session]；非 Hibernate 实现返回 `null`。
     */
    fun sessionOrNull(): Session? {
        if (!sessionResolved) {
            sessionResolved = true
            resolvedSession = try {
                entityManager.unwrap(Session::class.java)
            } catch (_: PersistenceException) {
                null
            } catch (_: IllegalStateException) {
                null
            }
        }
        return resolvedSession
    }

    /**
     * 获取实体元数据；实体类未被当前 `EntityManager` 管理时抛映射异常。
     *
     * @param entityClass 实体类（必须是实体实现类，而不是代理类）
     */
    fun entityTypeOf(entityClass: Class<*>): EntityType<*> = try {
        metamodel.entity(entityClass)
    } catch (e: IllegalArgumentException) {
        throw HibernateJpaMappingException(
            "Class ${entityClass.name} is not a managed entity type of the supplied EntityManager",
            e,
        )
    }

    /**
     * 还原代理并确保实体已初始化：`Hibernate.initialize` + `Hibernate.unproxy`。
     *
     * @param entity 可能是 Hibernate 代理的实体
     * @param path 当前文档路径
     * @return 实体实现对象（非代理）
     */
    fun resolveEntity(entity: Any, path: String): Any {
        if (!Hibernate.isInitialized(entity)) {
            initializeLazy(entity, describeLazy(entity), path)
        }
        if (entity !is HibernateProxy) {
            return entity
        }
        val implementation = try {
            Hibernate.unproxy(entity)
        } catch (e: HibernateException) {
            throw HibernateJpaMappingException("Cannot remove the Hibernate proxy at $path", e)
        } ?: throw HibernateJpaMappingException("Hibernate.unproxy returned null at $path")
        if (implementation is HibernateProxy) {
            throw HibernateJpaMappingException("Hibernate.unproxy did not return the entity implementation at $path")
        }
        return implementation
    }

    /**
     * 按 [HibernateConfig.lazyLoadPolicy] 初始化未初始化的代理或持久化集合。
     *
     * @param target 代理/持久化集合/未加载实体
     * @param description 目标描述，用于异常信息
     * @param path 当前文档路径
     */
    fun initializeLazy(target: Any, description: String, path: String) {
        if (config.lazyLoadPolicy == HibernateLazyLoadPolicy.FAIL) {
            throw HibernateJpaLazyLoadException(
                "$description at $path is not initialized and HibernateLazyLoadPolicy.FAIL forbids lazy loading",
            )
        }
        val session = sessionOrNull()
            ?: throw HibernateJpaLazyLoadException(
                "$description at $path is not initialized; the supplied EntityManager does not expose a Hibernate Session",
            )
        if (!session.isOpen) {
            throw HibernateJpaLazyLoadException(
                "$description at $path is not initialized and the Hibernate Session is closed",
            )
        }
        try {
            Hibernate.initialize(target)
        } catch (e: HibernateException) {
            throw HibernateJpaLazyLoadException("Cannot initialize $description at $path", e)
        }
        if (!Hibernate.isInitialized(target)) {
            throw HibernateJpaLazyLoadException(
                "$description at $path is still uninitialized after Hibernate.initialize",
            )
        }
    }

    /**
     * 序列化前准备实体的非关联状态：
     * 初始化字节码增强的懒加载属性与未初始化的非关联持久化集合。
     *
     * @param entity 已还原代理的实体实现
     * @param entityType 实体元数据
     * @param path 当前文档路径
     */
    fun prepareBaseState(entity: Any, entityType: EntityType<*>, path: String) {
        for (attribute in entityType.attributes) {
            if (attribute.isAssociation) {
                continue
            }
            if (!Hibernate.isPropertyInitialized(entity, attribute.name)) {
                initializeLazyProperty(entity, attribute, path)
            }
            if (!attribute.isCollection) {
                continue
            }
            val value = readAttributeValue(entity, attribute, path) ?: continue
            if (!Hibernate.isInitialized(value)) {
                initializeLazy(value, "Lazy collection property '${attribute.name}'", "$path.${attribute.name}")
            }
        }
    }

    private fun initializeLazyProperty(entity: Any, attribute: Attribute<*, *>, path: String) {
        val description = "Lazy property '${attribute.name}'"
        if (config.lazyLoadPolicy == HibernateLazyLoadPolicy.FAIL) {
            throw HibernateJpaLazyLoadException(
                "$description at $path is not initialized and HibernateLazyLoadPolicy.FAIL forbids lazy loading",
            )
        }
        try {
            Hibernate.initialize(entity)
        } catch (e: HibernateException) {
            throw HibernateJpaLazyLoadException("Cannot initialize $description at $path", e)
        }
        if (!Hibernate.isPropertyInitialized(entity, attribute.name)) {
            throw HibernateJpaLazyLoadException(
                "$description at $path is not initialized and cannot be initialized through Hibernate.initialize; " +
                    "initialize it before mapping",
            )
        }
    }

    private fun describeLazy(value: Any): String = if (value is HibernateProxy) {
        "Hibernate proxy"
    } else {
        "Lazy entity of type ${value.javaClass.name}"
    }
}

/**
 * 读取元模型属性值：字段直接反射读取，getter 通过方法调用读取。
 *
 * @param instance 目标实体
 * @param attribute 元模型属性
 * @param path 当前文档路径，用于异常信息
 */
internal fun readAttributeValue(instance: Any, attribute: Attribute<*, *>, path: String): Any? {
    val member = attribute.javaMember
    return try {
        when (member) {
            is Field -> {
                member.isAccessible = true
                member.get(instance)
            }

            is Method -> {
                member.isAccessible = true
                member.invoke(instance)
            }

            else -> throw HibernateJpaMappingException(
                "Unsupported member type ${member.javaClass.name} for attribute '${attribute.name}' at $path",
            )
        }
    } catch (e: InvocationTargetException) {
        throw HibernateJpaMappingException("Accessor of attribute '${attribute.name}' failed at $path", e.cause ?: e)
    } catch (e: ReflectiveOperationException) {
        throw HibernateJpaMappingException("Cannot read attribute '${attribute.name}' at $path", e)
    } catch (e: IllegalArgumentException) {
        throw HibernateJpaMappingException("Cannot read attribute '${attribute.name}' at $path", e)
    }
}

/**
 * 写入元模型属性值：优先写非 final 字段，否则查找 setter。
 *
 * @return `true` 表示已写入；`false` 表示属性只读（与 jpa 模块的 fromNode 一致，调用方跳过）
 */
internal fun writeAttributeValue(instance: Any, attribute: Attribute<*, *>, value: Any?): Boolean {
    val member = attribute.javaMember
    if (member is Field && !Modifier.isFinal(member.modifiers)) {
        try {
            member.isAccessible = true
            member.set(instance, value)
            return true
        } catch (e: ReflectiveOperationException) {
            throw HibernateJpaMappingException("Cannot write attribute '${attribute.name}' of ${instance.javaClass.name}", e)
        } catch (e: IllegalArgumentException) {
            throw HibernateJpaMappingException(
                "Cannot assign value of type ${value?.javaClass?.name} to attribute '${attribute.name}'",
                e,
            )
        }
    }
    val setter = findSetter(instance.javaClass, attribute.name, attribute.javaType)
    if (setter != null) {
        try {
            setter.isAccessible = true
            setter.invoke(instance, value)
            return true
        } catch (e: InvocationTargetException) {
            throw HibernateJpaMappingException("Setter of attribute '${attribute.name}' failed", e.cause ?: e)
        } catch (e: ReflectiveOperationException) {
            throw HibernateJpaMappingException("Cannot write attribute '${attribute.name}' of ${instance.javaClass.name}", e)
        } catch (e: IllegalArgumentException) {
            throw HibernateJpaMappingException(
                "Cannot assign value of type ${value?.javaClass?.name} to attribute '${attribute.name}'",
                e,
            )
        }
    }
    return false
}

private fun findSetter(owner: Class<*>, propertyName: String, valueType: Class<*>): Method? {
    val setterName = "set" + propertyName.replaceFirstChar { it.uppercaseChar() }
    var current: Class<*>? = owner
    while (current != null && current != Any::class.java) {
        for (method in current.declaredMethods) {
            if (method.name != setterName || method.parameterCount != 1) {
                continue
            }
            val parameterType = method.parameterTypes[0]
            if (parameterType.isAssignableFrom(valueType) || valueType.isAssignableFrom(parameterType)) {
                return method
            }
        }
        current = current.superclass
    }
    return null
}

/**
 * 解析关联属性的目标实体类型。
 *
 * @param attribute 元模型关联属性
 * @param path 当前文档路径，用于异常信息
 */
internal fun associationTargetClass(attribute: Attribute<*, *>, path: String): Class<*> {
    val javaType = if (attribute is PluralAttribute<*, *, *>) {
        attribute.elementType.javaType
    } else {
        attribute.javaType
    }
    if (javaType == Any::class.java) {
        throw HibernateJpaMappingException(
            "Cannot determine the target entity type of association '${attribute.name}' at $path",
        )
    }
    return javaType
}

/**
 * 按属性声明类型创建集合实例：`SortedSet` -> `TreeSet`、`Set` -> `LinkedHashSet`、
 * 具体集合类使用无参构造器，其余使用 `ArrayList`。
 */
internal fun newCollectionInstance(declaredType: Class<*>, path: String): MutableCollection<Any?> {
    val collection: MutableCollection<Any?> = when {
        java.util.SortedSet::class.java.isAssignableFrom(declaredType) -> TreeSet()
        Set::class.java.isAssignableFrom(declaredType) -> LinkedHashSet()
        !declaredType.isInterface &&
            !Modifier.isAbstract(declaredType.modifiers) &&
            Collection::class.java.isAssignableFrom(declaredType) -> try {
            @Suppress("UNCHECKED_CAST")
            declaredType.getDeclaredConstructor().newInstance() as MutableCollection<Any?>
        } catch (e: ReflectiveOperationException) {
            throw HibernateJpaMappingException(
                "Cannot instantiate collection type ${declaredType.name} at $path",
                e,
            )
        }

        else -> ArrayList()
    }
    return collection
}

/**
 * 读取元模型属性上的 `@Column(name)`，用于识别节点字段别名。
 *
 * @return 列名；没有 `@Column` 或名称为空时返回 `null`
 */
internal fun columnNameOf(attribute: Attribute<*, *>): String? {
    val member = attribute.javaMember
    val column = (member as? java.lang.reflect.AnnotatedElement)?.getAnnotation(Column::class.java) ?: return null
    return column.name.takeIf { it.isNotEmpty() }
}
