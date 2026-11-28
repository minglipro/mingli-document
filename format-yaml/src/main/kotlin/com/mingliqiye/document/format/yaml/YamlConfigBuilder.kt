package com.mingliqiye.document.format.yaml

import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy

/**
 * [YamlConfig] 构建器。
 */
public class YamlConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var pretty = false
    private var maxAliases = 0
    private var allowCustomTags = false

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
    ): YamlConfigBuilder {
        base.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): YamlConfigBuilder {
        base.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     */
    public fun failOnUnknownFields(fail: Boolean): YamlConfigBuilder {
        base.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     */
    public fun writeNulls(write: Boolean): YamlConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * `format()` 是否默认输出块格式 YAML。
     */
    public fun pretty(pretty: Boolean): YamlConfigBuilder {
        this.pretty = pretty
        return this
    }

    /**
     * 设置单文档允许的非标量 alias 最大数量，0 表示使用 SnakeYAML 默认值。
     *
     * 该限制作用于原生 Token 路径与 Node 回退路径（[YamlReader]）。
     *
     * @param maxAliases 最大 alias 数量
     * @return 当前构建器
     */
    public fun maxAliases(maxAliases: Int): YamlConfigBuilder {
        require(maxAliases >= 0) { "maxAliases must be >= 0" }
        this.maxAliases = maxAliases
        return this
    }

    /**
     * 设置是否允许未知自定义 tag（如 `!foo`、`!env`）。
     *
     * 默认 `false`，未知 tag 抛出
     * [com.mingliqiye.document.format.yaml.exception.YamlParseException]；开启后
     * 标量 -> StringNode，序列 -> ArrayNode，映射 -> ObjectNode。该选项同时作用于
     * Node 路径（[YamlReader]）与原生 Token 路径（[YamlMapper.parse]）。
     *
     * @param allow 是否允许未知自定义 tag
     * @return 当前构建器
     */
    public fun allowCustomTags(allow: Boolean): YamlConfigBuilder {
        this.allowCustomTags = allow
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
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>): YamlConfigBuilder {
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
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>, typeProperty: String): YamlConfigBuilder {
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
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): YamlConfigBuilder {
        base.typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 $ref。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): YamlConfigBuilder {
        base.resolveReferences(resolve)
        return this
    }

    /**
     * 设置序列化时是否输出对象身份 $ref。
     *
     * @param write 是否输出引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): YamlConfigBuilder {
        base.writeReferences(write)
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): YamlConfig = YamlConfig(base.build(), pretty, maxAliases, allowCustomTags)
}
