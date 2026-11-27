package com.mingliqiye.document.format.json

import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy

/**
 * [JsonConfig] 构建器。
 */
public class JsonConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var allowComments = true
    private var pretty = false
    private var duplicateKeyPolicy = DuplicateKeyPolicy.LAST_WINS
    private var maxDepth = JsonConfig.DEFAULT_MAX_DEPTH

    /**
     * 为指定字段绑定转换器。
     *
     * @param declaringClass 声明类
     * @param name 属性名
     * @param converter 字段转换器
     * @return 当前构建器
     */
    public fun field(
        declaringClass: Class<*>,
        name: String,
        converter: DocumentBaseConverter<*>,
    ): JsonConfigBuilder {
        base.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): JsonConfigBuilder {
        base.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     */
    public fun failOnUnknownFields(fail: Boolean): JsonConfigBuilder {
        base.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     */
    public fun writeNulls(write: Boolean): JsonConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * 是否允许 JSONC 注释。
     */
    public fun allowComments(allowComments: Boolean): JsonConfigBuilder {
        this.allowComments = allowComments
        return this
    }

    /**
     * `format()` 是否默认输出格式化 JSON。
     */
    public fun pretty(pretty: Boolean): JsonConfigBuilder {
        this.pretty = pretty
        return this
    }

    /**
     * 设置对象内重复 key 的处理策略。
     *
     * @param policy 重复 key 策略，默认 [DuplicateKeyPolicy.LAST_WINS]
     * @return 当前构建器
     */
    public fun duplicateKeyPolicy(policy: DuplicateKeyPolicy): JsonConfigBuilder {
        this.duplicateKeyPolicy = policy
        return this
    }

    /**
     * 设置对象/数组最大嵌套深度（对象与数组统一计数）。
     *
     * @param maxDepth 最大嵌套深度，必须为正数
     * @return 当前构建器
     * @throws IllegalArgumentException `maxDepth` 小于等于 0 时抛出
     */
    public fun maxDepth(maxDepth: Int): JsonConfigBuilder {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
        this.maxDepth = maxDepth
        return this
    }

    /**
     * 注册多态子类型。
     *
     * @param baseClass 基类/接口
     * @param typeName 类型名
     * @param subtypeClass 子类型
     * @return 当前构建器
     */
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>): JsonConfigBuilder {
        base.subtype(baseClass, typeName, subtypeClass)
        return this
    }

    /**
     * 注册多态子类型并覆盖类型属性名。
     *
     * @param baseClass 基类/接口
     * @param typeName 类型名
     * @param subtypeClass 子类型
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>, typeProperty: String): JsonConfigBuilder {
        base.subtype(baseClass, typeName, subtypeClass, typeProperty)
        return this
    }

    /**
     * 覆盖指定多态基类的类型属性名。
     *
     * @param baseClass 基类/接口
     * @param typeProperty 类型属性名
     * @return 当前构建器
     */
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): JsonConfigBuilder {
        base.typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): JsonConfig = JsonConfig(base.build(), allowComments, pretty, duplicateKeyPolicy, maxDepth)
}
