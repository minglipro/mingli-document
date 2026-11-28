package com.mingliqiye.document.serialization

import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import java.lang.reflect.Type

/**
 * Java record 反射访问 SPI。
 *
 * `serialization` 模块以 JDK 8 为字节码目标，不能直接引用 Java 16 新增的
 * `Class.isRecord()` / `Class.getRecordComponents()` / `RecordComponent`，因此把
 * record 探测能力抽成本接口，由独立的 `record-support` 模块（JDK 17 字节码）实现并通过
 * [java.util.ServiceLoader] 注册。
 *
 * 加载规则：
 * - JDK 16+ 且 classpath / module path 上存在 `record-support` 时使用其实现，record 映射
 *   行为与此前内嵌实现一致；
 * - JDK 8-15，或 JDK 16+ 但找不到实现时，Mapper 按「没有 record 特殊支持」处理，
 *   普通 Bean 行为不变，不会抛异常。
 *
 * 实现类必须有无参构造器；建议保持无状态（Mapper 会在内部缓存元数据）。
 */
public interface JavaRecordSupport {

    /**
     * 判断类型是否为 Java record。
     *
     * @param type 待判断的类型
     * @return 是 record 时返回 `true`
     */
    public fun isRecord(type: Class<*>): Boolean

    /**
     * 读取 record 组件元数据。
     *
     * 不是 record 时返回空列表；空 record 同样返回空列表，调用方应先通过
     * [isRecord] 区分两种情况。
     *
     * @param type 目标类型
     * @return 按声明顺序排列的组件元数据
     */
    public fun components(type: Class<*>): List<JavaRecordComponentInfo>
}

/**
 * 单个 Java record 组件的元数据。
 *
 * @property name 组件名，同时也是访问器方法名与 canonical constructor 参数名
 * @property type 组件泛型类型，与访问器返回值类型一致
 * @property accessor 组件访问器方法，例如 `name()`
 * @property element 组件本身（`java.lang.reflect.RecordComponent` 实现
 *   [AnnotatedElement]，可读取组件上的注解）
 */
public class JavaRecordComponentInfo(
    public val name: String,
    public val type: Type,
    public val accessor: Method,
    public val element: AnnotatedElement,
)
