package com.mingliqiye.document.format.properties

import com.mingliqiye.document.serialization.DeserializationFeature
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.DocumentMapperConfigBuilder
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import java.nio.charset.Charset

/**
 * [PropertiesConfig] 构建器。
 *
 * 提供三层 API：
 * 1. 上游实现 `JavaPropsSchema` 同名格式选项（`pathSeparator`、`indexMarker`、
 *    `firstArrayOffset`、`writeIndexUsingMarkers`、`keyValueSeparator`、`lineEnding`、
 *    `lineIndentation`、`header`、`prefix` 等）；
 * 2. 上游实现 风格特性开关：`enable` / `disable` / `configure`
 *    （[PropertiesReadFeature]、[PropertiesWriteFeature]、[DeserializationFeature]）；
 * 3. 通用 [DocumentMapperConfigBuilder] 便捷方法（命名策略、字符集、文件写选项、
 *    Node 限制、引用、Bean 校验、多态子类型等），免去手工组合通用 Builder。
 */
public class PropertiesConfigBuilder {

    private val base: DocumentMapperConfigBuilder = DocumentMapperConfig.builder()
    private var pathSeparator = PropertiesConfig.DEFAULT_PATH_SEPARATOR
    private var pathSeparatorEscapeChar = PropertiesConfig.DEFAULT_PATH_SEPARATOR_ESCAPE_CHAR
    private var indexMarker: PropertiesIndexMarker? = PropertiesConfig.DEFAULT_INDEX_MARKER
    private var firstArrayOffset = PropertiesConfig.DEFAULT_FIRST_ARRAY_OFFSET
    private var keyValueSeparator = PropertiesConfig.DEFAULT_KEY_VALUE_SEPARATOR
    private var lineEnding = PropertiesConfig.DEFAULT_LINE_ENDING
    private var lineIndentation = PropertiesConfig.DEFAULT_LINE_INDENTATION
    private var header = PropertiesConfig.DEFAULT_HEADER
    private var prefix: String? = null
    private var maxDepth = PropertiesConfig.DEFAULT_MAX_DEPTH
    private val readFeatures: MutableSet<PropertiesReadFeature> = PropertiesConfig.DEFAULT_READ_FEATURES.toMutableSet()
    private val writeFeatures: MutableSet<PropertiesWriteFeature> = PropertiesConfig.DEFAULT_WRITE_FEATURES.toMutableSet()

    // ------------------------------------------------------------------
    // 格式选项：JavaPropsSchema 对齐
    // ------------------------------------------------------------------

    /**
     * 设置逻辑路径分隔符，默认 `.`。
     *
     * 空字符串表示不按分隔符拆分嵌套（等价 [withoutPathSeparator]）；
     * 支持多字符分隔符。
     *
     * @param separator 路径分隔符，可为空字符串
     * @return 当前构建器
     */
    public fun pathSeparator(separator: String): PropertiesConfigBuilder {
        pathSeparator = separator
        return this
    }

    /**
     * 关闭路径分隔符拆分（等价 上游实现 `withoutPathSeparator()`）。
     *
     * 后续仍可使用 [indexMarker] 标记下标，但不再按 `.` 等分隔符推断嵌套。
     *
     * @return 当前构建器
     */
    public fun withoutPathSeparator(): PropertiesConfigBuilder = pathSeparator("")

    /**
     * 设置路径分隔符转义字符，默认 `'\u0000'`（关闭）。
     *
     * 仅当 [pathSeparator] 为单个字符时生效，用于让路径段包含分隔符：
     * 分隔符 `.`、转义字符 `#` 时，`a#.b` 解析为字段 `a.b`。
     *
     * @param escapeChar 转义字符；`'\u0000'` 表示关闭
     * @return 当前构建器
     */
    public fun pathSeparatorEscapeChar(escapeChar: Char): PropertiesConfigBuilder {
        pathSeparatorEscapeChar = escapeChar
        return this
    }

    /**
     * 设置数组下标标记，默认 `[` `]`。
     *
     * @param marker 下标标记
     * @return 当前构建器
     */
    public fun indexMarker(marker: PropertiesIndexMarker): PropertiesConfigBuilder {
        indexMarker = marker
        return this
    }

    /**
     * 设置数组下标标记起止字符串，默认 `[` `]`。
     *
     * @param start 起始标记，不能为空
     * @param end 结束标记，不能为空
     * @return 当前构建器
     * @throws IllegalArgumentException 标记为空时抛出
     */
    public fun indexMarker(start: String, end: String): PropertiesConfigBuilder = indexMarker(PropertiesIndexMarker.create(start, end))

    /**
     * 关闭数组下标标记（等价 上游实现 `withoutIndexMarker()`）。
     *
     * 读取时 `tags[0]` 不再表示数组下标；写出时强制使用简单下标写法。
     *
     * @return 当前构建器
     */
    public fun withoutIndexMarker(): PropertiesConfigBuilder {
        indexMarker = null
        return this
    }

    /**
     * 设置数组起始下标（仅写出），默认 `1`。
     *
     * @param offset 起始下标
     * @return 当前构建器
     */
    public fun firstArrayOffset(offset: Int): PropertiesConfigBuilder {
        firstArrayOffset = offset
        return this
    }

    /**
     * 设置键值分隔字符串（仅写出），默认 `=`。
     *
     * @param separator 键值分隔字符串
     * @return 当前构建器
     */
    public fun keyValueSeparator(separator: String): PropertiesConfigBuilder {
        keyValueSeparator = separator
        return this
    }

    /**
     * 设置行结尾字符串（仅写出），默认 `"\n"`。
     *
     * @param ending 行结尾
     * @return 当前构建器
     */
    public fun lineEnding(ending: String): PropertiesConfigBuilder {
        lineEnding = ending
        return this
    }

    /**
     * 设置行缩进字符串（仅写出），默认空串。
     *
     * @param indentation 每行前缀缩进
     * @return 当前构建器
     */
    public fun lineIndentation(indentation: String): PropertiesConfigBuilder {
        lineIndentation = indentation
        return this
    }

    /**
     * 关闭行缩进（等价 上游实现 `withoutLineIndentation()`）。
     *
     * @return 当前构建器
     */
    public fun withoutLineIndentation(): PropertiesConfigBuilder = lineIndentation("")

    /**
     * 设置输出头部原始文本（仅写出，默认空串）。
     *
     * 内容原样输出在所有条目之前，不自动追加换行；通常写成
     * `"# generated by mingli-document\n"` 这样的注释行。
     *
     * @param header 头部文本
     * @return 当前构建器
     */
    public fun header(header: String): PropertiesConfigBuilder {
        this.header = header
        return this
    }

    /**
     * 关闭输出头部（等价 上游实现 `withoutHeader()`）。
     *
     * @return 当前构建器
     */
    public fun withoutHeader(): PropertiesConfigBuilder = header("")

    /**
     * 设置写出前缀，默认 `null`（不启用）。
     *
     * 写出时根路径为 `prefix + pathSeparator + ...`；读取时从 key 剥离该前缀。
     * 传入 `null` 或空串表示不启用。
     *
     * @param prefix 路径前缀
     * @return 当前构建器
     */
    public fun prefix(prefix: String?): PropertiesConfigBuilder {
        this.prefix = prefix
        return this
    }

    /**
     * 关闭路径前缀（等价 上游实现 默认行为）。
     *
     * @return 当前构建器
     */
    public fun withoutPrefix(): PropertiesConfigBuilder = prefix(null)

    /**
     * 设置解析最大键路径深度，默认 [PropertiesConfig.DEFAULT_MAX_DEPTH]（512）。
     *
     * 根对象深度为 `1`，`a.b.c=1` 的最深层级为 `4`；超限时解析抛
     * [com.mingliqiye.document.format.properties.exception.PropertiesParseException]。
     *
     * @param maxDepth 最大嵌套深度，必须为正数
     * @return 当前构建器
     * @throws IllegalArgumentException `maxDepth` 小于等于 0 时抛出
     */
    public fun maxDepth(maxDepth: Int): PropertiesConfigBuilder {
        require(maxDepth > 0) { "maxDepth must be positive, but was $maxDepth" }
        this.maxDepth = maxDepth
        return this
    }

    // ------------------------------------------------------------------
    // Properties 特性开关
    // ------------------------------------------------------------------

    /**
     * 开启 Properties 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun enable(feature: PropertiesReadFeature): PropertiesConfigBuilder {
        readFeatures.add(feature)
        return this
    }

    /**
     * 关闭 Properties 读取特性。
     *
     * @param feature 读取特性
     * @return 当前构建器
     */
    public fun disable(feature: PropertiesReadFeature): PropertiesConfigBuilder {
        readFeatures.remove(feature)
        return this
    }

    /**
     * 设置 Properties 读取特性开关。
     *
     * @param feature 读取特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: PropertiesReadFeature, enabled: Boolean): PropertiesConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 开启 Properties 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun enable(feature: PropertiesWriteFeature): PropertiesConfigBuilder {
        writeFeatures.add(feature)
        return this
    }

    /**
     * 关闭 Properties 写出特性。
     *
     * @param feature 写出特性
     * @return 当前构建器
     */
    public fun disable(feature: PropertiesWriteFeature): PropertiesConfigBuilder {
        writeFeatures.remove(feature)
        return this
    }

    /**
     * 设置 Properties 写出特性开关。
     *
     * @param feature 写出特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: PropertiesWriteFeature, enabled: Boolean): PropertiesConfigBuilder = if (enabled) enable(feature) else disable(feature)

    /**
     * 是否把纯数字路径段解析为数组下标（对齐 上游实现 `parseSimpleIndexes`），默认 `true`。
     *
     * 等价于 `configure(PropertiesReadFeature.PARSE_SIMPLE_INDEXES, enabled)`。
     *
     * @param enabled 是否启用
     * @return 当前构建器
     */
    public fun parseSimpleIndexes(enabled: Boolean): PropertiesConfigBuilder = configure(PropertiesReadFeature.PARSE_SIMPLE_INDEXES, enabled)

    /**
     * 数组下标是否使用下标标记写出（对齐 上游实现 `writeIndexUsingMarkers`），默认 `false`。
     *
     * 等价于 `configure(PropertiesWriteFeature.WRITE_INDEX_USING_MARKERS, enabled)`；
     * 未配置 [indexMarker] 时不生效。
     *
     * @param enabled 是否使用标记
     * @return 当前构建器
     */
    public fun writeIndexUsingMarkers(enabled: Boolean): PropertiesConfigBuilder = configure(PropertiesWriteFeature.WRITE_INDEX_USING_MARKERS, enabled)

    // ------------------------------------------------------------------
    // 通用 DocumentMapperConfig 便捷方法（转发 base）
    // ------------------------------------------------------------------

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
    ): PropertiesConfigBuilder {
        base.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     *
     * @param policy 缺失字段策略
     * @return 当前构建器
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): PropertiesConfigBuilder {
        base.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     *
     * @param fail 是否失败
     * @return 当前构建器
     */
    public fun failOnUnknownFields(fail: Boolean): PropertiesConfigBuilder {
        base.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     *
     * @param write 是否写出 null 字段
     * @return 当前构建器
     */
    public fun writeNulls(write: Boolean): PropertiesConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * 设置未标注 `@DocumentName` 的字段命名策略。
     *
     * @param strategy 字段命名策略
     * @return 当前构建器
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): PropertiesConfigBuilder {
        base.namingStrategy(strategy)
        return this
    }

    /**
     * 设置文本文件/流读写字符集，默认 UTF-8。
     *
     * 注意：上游实现 Properties 字节输入固定按 ISO-8859-1 读取；本模块统一使用该
     * 字符集编解码字节，因此默认 UTF-8（差异见文档）。
     *
     * @param charset 字符集
     * @return 当前构建器
     */
    public fun fileCharset(charset: Charset): PropertiesConfigBuilder {
        base.fileCharset(charset)
        return this
    }

    /**
     * 设置写文件时是否原子替换。
     *
     * @param atomic 是否原子写入
     * @return 当前构建器
     */
    public fun atomicFileWrite(atomic: Boolean): PropertiesConfigBuilder {
        base.atomicFileWrite(atomic)
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     *
     * @param create 是否创建父目录
     * @return 当前构建器
     */
    public fun createParentDirectories(create: Boolean): PropertiesConfigBuilder {
        base.createParentDirectories(create)
        return this
    }

    /**
     * 设置单次输入最大字节数，0 表示不限制。
     *
     * @param max 最大字节数
     * @return 当前构建器
     */
    public fun maxInputSize(max: Long): PropertiesConfigBuilder {
        base.maxInputSize(max)
        return this
    }

    /**
     * 设置 Node 树中单个字符串最大长度，0 表示不限制。
     *
     * @param max 最大长度
     * @return 当前构建器
     */
    public fun maxStringLength(max: Int): PropertiesConfigBuilder {
        base.maxStringLength(max)
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制。
     *
     * 与 [maxDepth]（键路径解析深度）相互独立。
     *
     * @param max 最大深度
     * @return 当前构建器
     */
    public fun maxNodeDepth(max: Int): PropertiesConfigBuilder {
        base.maxNodeDepth(max)
        return this
    }

    /**
     * 设置 Node 树最大节点数，0 表示不限制。
     *
     * @param max 最大节点数
     * @return 当前构建器
     */
    public fun maxNodeCount(max: Long): PropertiesConfigBuilder {
        base.maxNodeCount(max)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 `$ref`。
     *
     * @param resolve 是否解析引用
     * @return 当前构建器
     */
    public fun resolveReferences(resolve: Boolean): PropertiesConfigBuilder {
        base.resolveReferences(resolve)
        return this
    }

    /**
     * 设置序列化时是否按对象身份写出本地 `$ref`。
     *
     * @param write 是否写出引用
     * @return 当前构建器
     */
    public fun writeReferences(write: Boolean): PropertiesConfigBuilder {
        base.writeReferences(write)
        return this
    }

    /**
     * 设置远程 `$ref` 解析器。
     *
     * @param resolver 远程引用解析器；`null` 表示禁用
     * @return 当前构建器
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): PropertiesConfigBuilder {
        base.remoteReferenceResolver(resolver)
        return this
    }

    /**
     * 设置根对象 Bean 校验器。
     *
     * @param validator 根对象校验器；`null` 表示不校验
     * @return 当前构建器
     */
    public fun beanValidator(validator: DocumentBeanValidator?): PropertiesConfigBuilder {
        base.beanValidator(validator)
        return this
    }

    /**
     * 开启通用反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun enable(feature: DeserializationFeature): PropertiesConfigBuilder {
        base.enable(feature)
        return this
    }

    /**
     * 关闭通用反序列化特性。
     *
     * @param feature 反序列化特性
     * @return 当前构建器
     */
    public fun disable(feature: DeserializationFeature): PropertiesConfigBuilder {
        base.disable(feature)
        return this
    }

    /**
     * 设置通用反序列化特性开关。
     *
     * @param feature 反序列化特性
     * @param enabled 是否开启
     * @return 当前构建器
     */
    public fun configure(feature: DeserializationFeature, enabled: Boolean): PropertiesConfigBuilder {
        base.configure(feature, enabled)
        return this
    }

    /**
     * 便捷设置 [DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES]。
     *
     * @param fail 是否在 `null` 赋给基本类型时抛出异常
     * @return 当前构建器
     */
    public fun failOnNullForPrimitives(fail: Boolean): PropertiesConfigBuilder {
        base.failOnNullForPrimitives(fail)
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
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>): PropertiesConfigBuilder {
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
    public fun subtype(
        baseClass: Class<*>,
        typeName: String,
        subtypeClass: Class<*>,
        typeProperty: String,
    ): PropertiesConfigBuilder {
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
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): PropertiesConfigBuilder {
        base.typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 构建不可变 Properties 配置。
     *
     * @return Properties 配置
     * @throws IllegalArgumentException [maxDepth] 非法（小于等于 0）时抛出
     */
    public fun build(): PropertiesConfig = PropertiesConfig(
        base.build(),
        pathSeparator,
        pathSeparatorEscapeChar,
        indexMarker,
        firstArrayOffset,
        keyValueSeparator,
        lineEnding,
        lineIndentation,
        header,
        prefix,
        maxDepth,
        readFeatures.toSet(),
        writeFeatures.toSet(),
    )
}
