package com.mingliqiye.document.serialization

import java.lang.reflect.Type
import java.util.function.BiConsumer
import java.util.function.Function

/**
 * 预编译的 Bean 属性访问器。
 *
 * 缓存 getter/setter 反射结果，避免每次序列化/反序列化重新查找属性。
 */
internal class BeanPropertyAccessor(
    val metadata: DocumentProperty,
    private val getter: Function<Any, Any?>?,
    private val setter: BiConsumer<Any, Any?>?,
    val valueType: Type,
) {

    /** 是否可读。 */
    val canRead: Boolean
        get() = getter != null

    /** 是否可写。 */
    val canWrite: Boolean
        get() = setter != null

    /** 读取属性值。 */
    fun read(bean: Any): Any? = getter?.apply(bean)

    /** 写入属性值。 */
    fun write(bean: Any, value: Any?) {
        setter?.accept(bean, value)
    }
}
