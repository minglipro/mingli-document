package com.mingliqiye.document.format.ion

import com.amazon.ion.IonSystem
import com.amazon.ion.system.IonSystemBuilder
import com.mingliqiye.document.core.Node
import java.io.ByteArrayInputStream

/**
 * 二进制 Ion 读取入口。
 *
 * ion-java 1.11.9 没有公开的 `newBinaryReader` 工厂方法；本类通过
 * `IonSystem.newReader(InputStream)` 读取字节内容，该 API 依据 Ion 版本标记
 * 自动识别二进制/文本 Ion（二进制输入为主）并支持 GZIP 解压。
 *
 * 行为与文本 [IonReader] 一致：只支持单文档单根；默认 blob / clob 映射为
 * `BinaryNode`。`preserveTypes = true` 时 timestamp / symbol / blob / clob 使用
 * 类型保真包装对象（见 [IonConfig.preserveTypes]），并按
 * [IonReadFeature.USE_NATIVE_TYPE_ID] 保留 Ion 注解。
 *
 * @param source 二进制 Ion 内容
 * @param maxDepth Node 树最大深度，0 表示不限制
 * @param preserveTypes 是否保留 Ion 专属类型，默认 `false`
 * @param readFeatures Ion 读取特性，默认对齐 上游实现（见 [IonConfig.DEFAULT_READ_FEATURES]）
 */
public class IonBinaryReader @JvmOverloads constructor(
    private val source: ByteArray,
    private val maxDepth: Int = 0,
    private val preserveTypes: Boolean = false,
    private val readFeatures: Set<IonReadFeature> = IonConfig.DEFAULT_READ_FEATURES,
) {

    /**
     * 解析并返回文档树。
     *
     * @throws IonParseException Ion 数据非法、根值缺失或存在多个根值时抛出
     */
    public fun read(): Node = readIonNode(
        ION_SYSTEM.newReader(ByteArrayInputStream(source)),
        maxDepth,
        preserveTypes,
        readFeatures,
    )

    public companion object {

        private val ION_SYSTEM: IonSystem = IonSystemBuilder.standard().build()

        /**
         * 解析二进制 Ion 字节。
         *
         * @param source 二进制 Ion 内容
         * @return 文档树
         * @throws IonParseException Ion 数据非法、根值缺失或存在多个根值时抛出
         */
        @JvmStatic
        public fun parse(source: ByteArray): Node = IonBinaryReader(source).read()
    }
}

/**
 * 将二进制 Ion 字节解析为文档树。
 *
 * @return 文档树
 * @throws IonParseException Ion 数据非法、根值缺失或存在多个根值时抛出
 */
public fun ByteArray.toIonNode(): Node = IonBinaryReader(this).read()
