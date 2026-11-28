package com.mingliqiye.document.serialization

import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Type

/**
 * Java record 反射访问支持。
 *
 * 本模块以 JDK 8 为编译目标，不能直接引用 Java 16 新增的
 * `Class.isRecord()` / `Class.getRecordComponents()` / `RecordComponent`，
 * 因此全部通过反射按名称探测：
 * - 运行在 JDK 8 时相关方法/类型不存在，[components] 返回空列表，普通 Bean 行为不变；
 * - 运行在 JDK 16+ 时按 record 组件返回名称、泛型类型与访问器方法。
 *
 * 探测结果在类初始化时缓存；每个类型只在首次构建 Bean 元数据时解析一次。
 */
internal object JavaRecordSupport {

    private val isRecordMethod: Method? = classMethod("isRecord")

    private val getRecordComponentsMethod: Method? = classMethod("getRecordComponents")

    private val recordComponentType: Class<*>? = findRecordComponentType()

    private val componentNameMethod: Method? = recordComponentMethod("getName")

    private val componentAccessorMethod: Method? = recordComponentMethod("getAccessor")

    private val componentGenericTypeMethod: Method? = recordComponentMethod("getGenericType")

    /**
     * 当前运行环境是否提供 Java record 反射 API。
     *
     * 返回 `false` 时所有 record 相关探测都按“不是 record”处理。
     */
    val isAvailable: Boolean
        get() = isRecordMethod != null &&
            getRecordComponentsMethod != null &&
            componentNameMethod != null &&
            componentAccessorMethod != null &&
            componentGenericTypeMethod != null

    /**
     * 判断类型是否为 Java record。
     *
     * 运行在 JDK 8（没有 record 反射 API）时恒为 `false`。
     */
    fun isRecord(type: Class<*>): Boolean {
        val method = isRecordMethod ?: return false
        return try {
            method.invoke(type) as? Boolean ?: false
        } catch (_: ReflectiveOperationException) {
            false
        }
    }

    /**
     * 读取 record 组件元数据。
     *
     * 不是 record 或运行环境不支持时返回空列表；空列表也可能表示没有组件的
     * 空 record，调用方应先用 [isRecord] 区分。
     */
    fun components(type: Class<*>): List<JavaRecordComponent> {
        if (!isAvailable || !isRecord(type)) {
            return emptyList()
        }
        val method = getRecordComponentsMethod ?: return emptyList()
        val components = try {
            method.invoke(type) as? Array<*> ?: return emptyList()
        } catch (_: ReflectiveOperationException) {
            return emptyList()
        }
        return components.mapNotNull { component -> component?.let(::toComponent) }
    }

    private fun toComponent(component: Any): JavaRecordComponent? = try {
        val name = componentNameMethod?.invoke(component) as? String
        val accessor = componentAccessorMethod?.invoke(component) as? Method
        val genericType = componentGenericTypeMethod?.invoke(component) as? Type
        if (name == null || accessor == null || genericType == null) {
            null
        } else {
            JavaRecordComponent(name, genericType, accessor, component as AnnotatedElement)
        }
    } catch (_: ReflectiveOperationException) {
        null
    }

    private fun classMethod(name: String): Method? = try {
        Class::class.java.getMethod(name)
    } catch (_: NoSuchMethodException) {
        null
    }

    private fun findRecordComponentType(): Class<*>? = try {
        Class.forName("java.lang.reflect.RecordComponent")
    } catch (_: ClassNotFoundException) {
        null
    }

    private fun recordComponentMethod(name: String): Method? = try {
        recordComponentType?.getMethod(name)
    } catch (_: NoSuchMethodException) {
        null
    }
}

/**
 * 单个 Java record 组件的元数据。
 *
 * @property name 组件名，同时也是访问器方法名与规范构造器参数名
 * @property type 组件泛型类型，与访问器返回值类型一致
 * @property accessor 组件访问器方法，例如 `name()`
 * @property element 组件本身（`RecordComponent` 实现 `AnnotatedElement`；
 *   JDK 8 下不会创建该对象）
 */
internal class JavaRecordComponent(
    val name: String,
    val type: Type,
    val accessor: Method,
    val element: AnnotatedElement,
)
