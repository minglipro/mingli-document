package com.mingliqiye.document.jdbc

import com.mingliqiye.document.core.Node

/**
 * 自定义列转换器：把 `ResultSet` 当前行的原始列值转换为 [Node]。
 *
 * 通过 [JdbcConfigBuilder.columnConverter] 按列标签或列下标注册；注册后优先于内置类型
 * 推断执行。返回 `null` 表示回退内置转换；需要把 SQL NULL 自定义为其它节点时返回
 * 目标节点（例如 [com.mingliqiye.document.core.NullNode.INSTANCE]）。
 *
 * 转换器抛出 [com.mingliqiye.document.exception.DocumentException] 时原样传播，其它异常
 * 由读取器包装为
 * [com.mingliqiye.document.jdbc.exception.JdbcReadException]。
 */
public fun interface ColumnConverter {

    /**
     * 转换当前列的原始值。
     *
     * @param value `ResultSet.getObject` 返回的原始值；SQL NULL 时为 `null`
     * @param column 列元数据
     * @return 转换后的节点；`null` 表示回退内置转换
     */
    public fun toNode(value: Any?, column: JdbcColumn): Node?
}
