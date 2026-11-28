package com.mingliqiye.document.format.properties

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.format.properties.exception.PropertiesParseException

/**
 * Properties 读取入口。
 *
 * 平铺 key 按 [PropertiesConfig] 的格式选项还原成对象/数组：
 * 默认路径分隔符 `.`、下标标记 `[index]`、简单数字下标（上游实现 `JavaPropsSchema`
 * 默认行为），并保留本模块的百分号编码扩展；数组为稠密数组（下标只用于排序）。
 *
 * 解析深度：key 路径令牌数决定嵌套深度（根对象深度为 `1`，`a.b.c=1` 最深层级为 `4`）。
 * 超过 [PropertiesConfig.maxDepth] 时抛出 [PropertiesParseException]，并在构建中间
 * 结构前完成校验，避免超深输入 StackOverflow。
 *
 * @param source Properties 文本
 * @param config Properties 配置
 * @param maxDepth 解析最大嵌套深度；默认取 [PropertiesConfig.maxDepth]（512），
 *   `0` 或负数表示不限制（显式关闭保护）
 */
public class PropertiesReader(
    private val source: String,
    private val config: PropertiesConfig,
    private val maxDepth: Int = config.maxDepth,
) {

    /**
     * 使用默认格式选项与指定解析深度创建读取器。
     *
     * @param source Properties 文本
     * @param maxDepth 最大嵌套深度，默认 [PropertiesMapper.DEFAULT_MAX_DEPTH]（512）；
     *   `0` 或负数表示不限制（显式关闭保护）
     */
    public constructor(source: String, maxDepth: Int = PropertiesMapper.DEFAULT_MAX_DEPTH) :
        this(source, PropertiesConfig.empty(), maxDepth)

    /**
     * 解析并返回文档树。
     *
     * @throws PropertiesParseException Properties 格式非法、百分号转义非法或路径超深时抛出
     */
    public fun read(): Node = PropertiesTreeReader(config, maxDepth).readNode(source)

    public companion object {

        /**
         * 解析 Properties 字符串（默认配置）。
         *
         * @param source Properties 文本
         * @return 文档树
         * @throws PropertiesParseException Properties 格式非法时抛出
         */
        @JvmStatic
        public fun parse(source: String): Node = PropertiesReader(source).read()
    }
}

/**
 * 将字符串解析为文档树（默认配置）。
 *
 * @return 文档树
 * @throws PropertiesParseException Properties 格式非法时抛出
 */
public fun String.toPropertiesNode(): Node = PropertiesReader(this).read()

/**
 * 将字符串按 Properties 配置解析为文档树。
 *
 * @param config Properties 配置
 * @return 文档树
 * @throws PropertiesParseException Properties 格式非法时抛出
 */
public fun String.toPropertiesNode(config: PropertiesConfig): Node = PropertiesReader(this, config).read()
