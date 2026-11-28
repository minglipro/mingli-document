package com.mingliqiye.document.format.json

import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import java.nio.charset.Charset

/**
 * [JsonConfig] 构建器。
 *
 * 除 JSON 专属选项（注释/重复 key/深度/读写出特性）外，转发全部通用
 * [DocumentMapperConfig] 选项，因此始终使用 [JsonConfigBuilder] 也不会丢失通用配置。
 */
public class JsonConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var allowComments = true
    private var pretty = false
    private var duplicateKeyPolicy = DuplicateKeyPolicy.LAST_WINS
    private var maxDepth = JsonConfig.DEFAULT_MAX_DEPTH
    private val readFeatures = JsonConfig.DEFAULT_READ_FEATURES.toMutableSet()
    private val writeFeatures = JsonConfig.DEFAULT_WRITE_FEATURES.toMutableSet()

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
     * 设置 Bean 字段为 null 时是否写出（全局 `JsonInclude` 等价开关）。
     */
    public fun writeNulls(write: Boolean): JsonConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * 是否允许 JSONC 注释（等价于 [JsonReadFeature.ALLOW_JAVA_COMMENTS]）。
     */
    public fun allowComments(allowComments: Boolean): JsonConfigBuilder {
        this.allowComments = allowComments
        return this
    }

    /**
     * `format()` 是否默认输出格式化 JSON（等价于 上游实现 的 `INDENT_OUTPUT`）。
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
     * 开启 JSON 读取特性。
     *
     * [JsonReadFeature.ALLOW_JAVA_COMMENTS] 与 [JsonReadFeature.STRICT_DUPLICATE_DETECTION]
     * 分别联动 [allowComments] 与 [duplicateKeyPolicy]（等价于
     * [DuplicateKeyPolicy.FAIL]）；其余特性加入读取特性集合。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: JsonReadFeature): JsonConfigBuilder {
        when (feature) {
            JsonReadFeature.ALLOW_JAVA_COMMENTS -> allowComments = true
            JsonReadFeature.STRICT_DUPLICATE_DETECTION -> duplicateKeyPolicy = DuplicateKeyPolicy.FAIL
            else -> readFeatures.add(feature)
        }
        return this
    }

    /**
     * 关闭 JSON 读取特性。
     *
     * 关闭 [JsonReadFeature.STRICT_DUPLICATE_DETECTION] 时若当前策略为
     * [DuplicateKeyPolicy.FAIL] 则恢复为 [DuplicateKeyPolicy.LAST_WINS]。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: JsonReadFeature): JsonConfigBuilder {
        when (feature) {
            JsonReadFeature.ALLOW_JAVA_COMMENTS -> allowComments = false
            JsonReadFeature.STRICT_DUPLICATE_DETECTION -> {
                if (duplicateKeyPolicy == DuplicateKeyPolicy.FAIL) {
                    duplicateKeyPolicy = DuplicateKeyPolicy.LAST_WINS
                }
            }

            else -> readFeatures.remove(feature)
        }
        return this
    }

    /**
     * 按布尔值开启或关闭 JSON 读取特性。
     *
     * @param feature 读取特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: JsonReadFeature, enabled: Boolean): JsonConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启 JSON 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: JsonWriteFeature): JsonConfigBuilder {
        writeFeatures.add(feature)
        return this
    }

    /**
     * 关闭 JSON 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: JsonWriteFeature): JsonConfigBuilder {
        writeFeatures.remove(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭 JSON 写出特性。
     *
     * @param feature 写出特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: JsonWriteFeature, enabled: Boolean): JsonConfigBuilder = if (enabled) enable(feature) else disable(feature)

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
     * 设置未标注字段命名注解的字段命名策略。
     *
     * @param strategy 命名策略
     * @return 当前构建器
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): JsonConfigBuilder {
        base.namingStrategy(strategy)
        return this
    }

    /**
     * 设置文本文件/流读写字符集。
     *
     * @param charset 字符集
     * @return 当前构建器
     */
    public fun fileCharset(charset: Charset): JsonConfigBuilder {
        base.fileCharset(charset)
        return this
    }

    /**
     * 设置写文件时是否使用临时文件 + 原子替换。
     *
     * @param atomic 是否原子写入
     * @return 当前构建器
     */
    public fun atomicFileWrite(atomic: Boolean): JsonConfigBuilder {
        base.atomicFileWrite(atomic)
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     *
     * @param create 是否创建父目录
     * @return 当前构建器
     */
    public fun createParentDirectories(create: Boolean): JsonConfigBuilder {
        base.createParentDirectories(create)
        return this
    }

    /**
     * 设置单次输入最大字节数，0 表示不限制。
     *
     * @param max 最大字节数
     * @return 当前构建器
     * @throws IllegalArgumentException 参数为负数时抛出
     */
    public fun maxInputSize(max: Long): JsonConfigBuilder {
        base.maxInputSize(max)
        return this
    }

    /**
     * 设置 Node 树中单个字符串最大长度，0 表示不限制。
     *
     * @param max 最大长度
     * @return 当前构建器
     * @throws IllegalArgumentException 参数为负数时抛出
     */
    public fun maxStringLength(max: Int): JsonConfigBuilder {
        base.maxStringLength(max)
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制。
     *
     * 与 JSON 文本解析深度 [maxDepth] 相互独立：本项由通用 Node 路径使用。
     *
     * @param max 最大深度
     * @return 当前构建器
     * @throws IllegalArgumentException 参数为负数时抛出
     */
    public fun maxNodeDepth(max: Int): JsonConfigBuilder {
        base.maxNodeDepth(max)
        return this
    }

    /**
     * 设置 Node 树最大节点数，0 表示不限制。
     *
     * @param max 最大节点数
     * @return 当前构建器
     * @throws IllegalArgumentException 参数为负数时抛出
     */
    public fun maxNodeCount(max: Long): JsonConfigBuilder {
        base.maxNodeCount(max)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 `$ref`。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): JsonConfigBuilder {
        base.resolveReferences(resolve)
        return this
    }

    /**
     * 设置序列化时是否输出对象身份 `$ref`。
     *
     * @param write 是否输出引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): JsonConfigBuilder {
        base.writeReferences(write)
        return this
    }

    /**
     * 设置远程 `$ref` 解析器。
     *
     * @param resolver 解析器；`null` 表示禁用远程引用
     * @return 当前构建器
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): JsonConfigBuilder {
        base.remoteReferenceResolver(resolver)
        return this
    }

    /**
     * 设置根对象 Bean 校验器。
     *
     * @param validator 校验器；`null` 表示不校验
     * @return 当前构建器
     */
    public fun beanValidator(validator: DocumentBeanValidator?): JsonConfigBuilder {
        base.beanValidator(validator)
        return this
    }

    /**
     * 开启通用反序列化特性（[DeserializationFeature]）。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun enable(feature: DeserializationFeature): JsonConfigBuilder {
        base.enable(feature)
        return this
    }

    /**
     * 关闭通用反序列化特性（[DeserializationFeature]）。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun disable(feature: DeserializationFeature): JsonConfigBuilder {
        base.disable(feature)
        return this
    }

    /**
     * 按布尔值开启或关闭通用反序列化特性（[DeserializationFeature]）。
     *
     * @param feature 反序列化特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: DeserializationFeature, enabled: Boolean): JsonConfigBuilder {
        base.configure(feature, enabled)
        return this
    }

    /**
     * 便捷设置 [DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES]。
     *
     * @param fail 是否在 `null` 赋给基本类型时抛出异常
     * @return 当前构建器
     */
    public fun failOnNullForPrimitives(fail: Boolean): JsonConfigBuilder {
        base.failOnNullForPrimitives(fail)
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): JsonConfig = JsonConfig(
        base.build(),
        allowComments,
        pretty,
        duplicateKeyPolicy,
        maxDepth,
        readFeatures.toSet(),
        writeFeatures.toSet(),
    )
}
