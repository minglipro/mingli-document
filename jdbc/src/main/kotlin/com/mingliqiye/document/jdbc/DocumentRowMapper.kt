package com.mingliqiye.document.jdbc

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.jdbc.internal.NodeConversions
import com.mingliqiye.document.serialization.DocumentMapper

/**
 * 行映射器：把 [JdbcDocumentReader] 读取到的一行对象节点转换为目标类型。
 *
 * 可以用 lambda 实现自定义映射，也可以用内置工厂：
 * - [of]：使用 [DocumentMapper] 把对象节点反序列化为 Bean；
 * - [nodes]：原样返回对象节点；
 * - [maps]：转换为普通 Java Map（对象 -> Map、数组 -> List、数字 -> BigDecimal、
 *   二进制 -> ByteArray）。
 */
public fun interface DocumentRowMapper<T> {

    /**
     * 映射一行数据。
     *
     * @param row 当前行的对象节点
     * @return 映射结果
     */
    public fun mapRow(row: ObjectNode): T

    public companion object {

        /**
         * 创建返回对象节点的行映射器。
         */
        @JvmStatic
        public fun nodes(): DocumentRowMapper<ObjectNode> = DocumentRowMapper { row -> row }

        /**
         * 创建返回普通 Java Map 的行映射器。
         */
        @JvmStatic
        public fun maps(): DocumentRowMapper<Map<String, Any?>> = DocumentRowMapper { row -> NodeConversions.toJavaMap(row) }

        /**
         * 创建使用 [DocumentMapper] 反序列化 Bean 的行映射器。
         *
         * @param targetType 目标类型
         * @param documentMapper 文档映射器
         * @return 行映射器
         */
        @JvmStatic
        public fun <T> of(targetType: Class<T>, documentMapper: DocumentMapper): DocumentRowMapper<T> = DocumentRowMapper { row ->
            documentMapper.fromNode(row, targetType)
        }

        /**
         * 创建使用 [DocumentMapper] 反序列化 Bean 的行映射器。
         *
         * @param targetType 目标泛型类型
         * @param documentMapper 文档映射器
         * @return 行映射器
         */
        @JvmStatic
        public fun <T> of(targetType: DocumentTypeReference<T>, documentMapper: DocumentMapper): DocumentRowMapper<T> = DocumentRowMapper { row ->
            documentMapper.fromNode(row, targetType)
        }
    }
}
