package com.mingliqiye.document.format.ion

import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.DocumentMapperConfig
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.PropertyNamingStrategy
import com.mingliqiye.document.serialization.RemoteReferenceResolver
import java.nio.charset.Charset

/**
 * [IonConfig] 构建器。
 *
 * 通用选项直接委托 [DocumentMapperConfig] 构建器，Ion 专属选项单独保存。
 */
public class IonConfigBuilder {

    private val base = DocumentMapperConfig.builder()
    private var preserveTypes = false

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
    ): IonConfigBuilder {
        base.field(declaringClass, name, converter)
        return this
    }

    /**
     * 设置缺少字段时的处理策略。
     */
    public fun missingFieldPolicy(policy: MissingFieldPolicy): IonConfigBuilder {
        base.missingFieldPolicy(policy)
        return this
    }

    /**
     * 设置遇到未知字段时是否抛出异常。
     */
    public fun failOnUnknownFields(fail: Boolean): IonConfigBuilder {
        base.failOnUnknownFields(fail)
        return this
    }

    /**
     * 设置 Bean 字段为 null 时是否写出。
     */
    public fun writeNulls(write: Boolean): IonConfigBuilder {
        base.writeNulls(write)
        return this
    }

    /**
     * 设置字段命名策略。
     */
    public fun namingStrategy(strategy: PropertyNamingStrategy): IonConfigBuilder {
        base.namingStrategy(strategy)
        return this
    }

    /**
     * 设置文本文件/流读写字符集。
     */
    public fun fileCharset(charset: Charset): IonConfigBuilder {
        base.fileCharset(charset)
        return this
    }

    /**
     * 设置写文件时是否原子替换。
     */
    public fun atomicFileWrite(atomic: Boolean): IonConfigBuilder {
        base.atomicFileWrite(atomic)
        return this
    }

    /**
     * 设置写文件时是否自动创建父目录。
     */
    public fun createParentDirectories(create: Boolean): IonConfigBuilder {
        base.createParentDirectories(create)
        return this
    }

    /**
     * 设置单次输入最大字节数，0 表示不限制。
     */
    public fun maxInputSize(max: Long): IonConfigBuilder {
        base.maxInputSize(max)
        return this
    }

    /**
     * 设置 Node 树最大深度，0 表示不限制。
     */
    public fun maxNodeDepth(max: Int): IonConfigBuilder {
        base.maxNodeDepth(max)
        return this
    }

    /**
     * 设置 Node 树最大节点数，0 表示不限制。
     */
    public fun maxNodeCount(max: Long): IonConfigBuilder {
        base.maxNodeCount(max)
        return this
    }

    /**
     * 设置反序列化前是否解析本地 `$ref`。
     */
    public fun resolveReferences(resolve: Boolean): IonConfigBuilder {
        base.resolveReferences(resolve)
        return this
    }

    /**
     * 设置 [com.mingliqiye.document.serialization.DocumentMapper.toNode]
     * 序列化时是否按对象身份写出本地 `$ref`。
     */
    public fun writeReferences(write: Boolean): IonConfigBuilder {
        base.writeReferences(write)
        return this
    }

    /**
     * 设置远程 `$ref` 解析器；`null` 表示禁用远程引用。
     */
    public fun remoteReferenceResolver(resolver: RemoteReferenceResolver?): IonConfigBuilder {
        base.remoteReferenceResolver(resolver)
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
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>): IonConfigBuilder {
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
    public fun subtype(baseClass: Class<*>, typeName: String, subtypeClass: Class<*>, typeProperty: String): IonConfigBuilder {
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
    public fun typeProperty(baseClass: Class<*>, typeProperty: String): IonConfigBuilder {
        base.typeProperty(baseClass, typeProperty)
        return this
    }

    /**
     * 设置是否保留 Ion 专属类型（timestamp / symbol / clob / blob）。
     *
     * @param preserve 是否保留类型，默认 `false`
     * @return 当前构建器
     */
    public fun preserveTypes(preserve: Boolean): IonConfigBuilder {
        preserveTypes = preserve
        return this
    }

    /**
     * 构建配置。
     */
    public fun build(): IonConfig = IonConfig(base.build(), preserveTypes)
}
