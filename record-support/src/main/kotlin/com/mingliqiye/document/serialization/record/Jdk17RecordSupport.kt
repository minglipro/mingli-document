package com.mingliqiye.document.serialization.record

import com.mingliqiye.document.serialization.JavaRecordComponentInfo
import com.mingliqiye.document.serialization.JavaRecordSupport

/**
 * JDK 17 的 [JavaRecordSupport] 实现，直接使用 Java 16+ 的 record 反射 API。
 *
 * 通过 `META-INF/services/com.mingliqiye.document.serialization.JavaRecordSupport`
 * 注册，[com.mingliqiye.document.serialization.DocumentMapper] 在 JDK 17+ 运行且本模块
 * 位于 classpath / module path 上时自动使用；未引入本模块时 record 类型按普通 Bean 处理。
 *
 * 实现无状态，实例可以被 ServiceLoader 缓存后复用。
 */
public class Jdk17RecordSupport : JavaRecordSupport {

    /**
     * 判断类型是否为 Java record。
     *
     * @param type 待判断的类型
     * @return 是 record 时返回 `true`
     */
    override fun isRecord(type: Class<*>): Boolean = type.isRecord

    /**
     * 读取 record 组件元数据。
     *
     * 不是 record 时返回空列表；空 record 同样返回空列表，调用方用 [isRecord] 区分。
     *
     * @param type 目标类型
     * @return 按声明顺序排列的组件元数据
     */
    override fun components(type: Class<*>): List<JavaRecordComponentInfo> {
        if (!type.isRecord) {
            return emptyList()
        }
        return type.recordComponents.map { component ->
            JavaRecordComponentInfo(
                name = component.name,
                type = component.genericType,
                accessor = component.accessor,
                element = component,
            )
        }
    }
}
