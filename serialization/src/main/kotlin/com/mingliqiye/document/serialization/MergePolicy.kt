package com.mingliqiye.document.serialization

/**
 * [DocumentMapperApi.merge] 合并文档树到已有对象时的字段覆盖策略。
 *
 * 合并语义固定为“文档树中出现的字段覆盖目标对象，未出现的字段保留原值”；
 * 该策略只控制两类边界行为：
 * - [deepMerge]：嵌套 Bean 是整体替换还是递归合并；
 * - [skipNulls]：文档中的 `null` 是否覆盖目标对象已有值。
 *
 * 常量实例见 [REPLACE]、[DEEP]、[SKIP_NULLS]、[DEEP_SKIP_NULLS]，
 * 也可以用 [of] 组合出其他策略。
 */
public class MergePolicy private constructor(
    /**
     * 是否递归合并嵌套 Bean。
     *
     * 关闭时字段整体替换：文档中的嵌套对象会替换目标对象的整个嵌套对象；
     * 开启时只覆盖嵌套对象中出现的字段，未出现的嵌套字段保留。
     * 集合、Map、数组与多态声明字段始终整体替换。
     */
    public val deepMerge: Boolean,
    /**
     * 文档中的 `null` 是否跳过。
     *
     * 开启时 `null` 不覆盖目标对象已有值；关闭时 `null` 覆盖引用类型字段
     * （与反序列化一致，`null` 写入基本类型字段会抛异常）。
     */
    public val skipNulls: Boolean,
) {

    public companion object {

        /** 浅合并：文档中出现的字段整体替换，`null` 覆盖已有值。 */
        @JvmField
        public val REPLACE: MergePolicy = MergePolicy(deepMerge = false, skipNulls = false)

        /** 深合并：嵌套 Bean 递归合并，`null` 覆盖已有值。 */
        @JvmField
        public val DEEP: MergePolicy = MergePolicy(deepMerge = true, skipNulls = false)

        /** 浅合并并忽略文档中的 `null`。 */
        @JvmField
        public val SKIP_NULLS: MergePolicy = MergePolicy(deepMerge = false, skipNulls = true)

        /** 深合并并忽略文档中的 `null`。 */
        @JvmField
        public val DEEP_SKIP_NULLS: MergePolicy = MergePolicy(deepMerge = true, skipNulls = true)

        /**
         * 自定义组合。
         *
         * @param deepMerge 是否递归合并嵌套 Bean
         * @param skipNulls 是否忽略文档中的 `null`
         * @return 合并策略
         */
        @JvmStatic
        public fun of(deepMerge: Boolean, skipNulls: Boolean): MergePolicy = MergePolicy(deepMerge, skipNulls)
    }
}
