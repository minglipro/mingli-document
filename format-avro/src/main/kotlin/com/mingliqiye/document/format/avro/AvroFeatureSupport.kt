package com.mingliqiye.document.format.avro

import org.apache.avro.Schema
import org.apache.avro.io.BinaryDecoder
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.Encoder
import org.apache.avro.io.EncoderFactory
import java.io.InputStream
import java.io.OutputStream

/**
 * Avro 编解码器工厂与 上游实现 Avro format feature 的映射。
 *
 * `AVRO_BUFFERING` 对齐 上游实现 `AvroParser.Feature` / `AvroGenerator.Feature`
 * （上游实现 3.x 中为 `AvroReadFeature` / `AvroWriteFeature`）：
 * - 开启：`EncoderFactory.binaryEncoder` / `DecoderFactory.binaryDecoder`（缓冲）；
 * - 关闭：`EncoderFactory.directBinaryEncoder` / `DecoderFactory.directBinaryDecoder`
 *   （直接写出、尽量只读取最少字节）。
 *
 * 两种模式只改变内部缓冲策略，不改变 wire 语义与输出字节。
 */
internal object AvroFeatureSupport {

    /**
     * 按 `AVRO_BUFFERING` 开关创建二进制编码器。
     *
     * @param output 目标输出流
     * @param buffering 是否使用缓冲编码器
     * @return Avro 编码器（缓冲或 direct）
     */
    fun createEncoder(output: OutputStream, buffering: Boolean): Encoder = if (buffering) {
        EncoderFactory.get().binaryEncoder(output, null)
    } else {
        EncoderFactory.get().directBinaryEncoder(output, null)
    }

    /**
     * 创建缓冲二进制解码器（`AVRO_BUFFERING` 开启，或需要数组缓冲的路径）。
     *
     * @param source 完整 Avro binary 字节
     * @return 缓冲解码器
     */
    fun createBufferedDecoder(source: ByteArray): BinaryDecoder = DecoderFactory.get().binaryDecoder(source, null)

    /**
     * 创建 direct 二进制解码器（`AVRO_BUFFERING` 关闭）。
     *
     * direct 解码器只从流中读取解码所需的最小字节；尾随字节校验由调用方通过
     * 底层流的剩余字节判断。
     *
     * @param input 输入流
     * @return direct 解码器
     */
    fun createDirectDecoder(input: InputStream): BinaryDecoder = DecoderFactory.get().directBinaryDecoder(input, null)

    /**
     * 按配置创建裸 wire 写出器（[AvroWriteFeature.AVRO_BUFFERING] 生效）。
     *
     * @param schema writer Schema
     * @param config Avro 配置
     * @return 裸 wire 写出器
     */
    fun wireWriter(schema: Schema, config: AvroConfig): AvroWireWriter = AvroWireWriter(schema, config.avroBufferingOnWrite)

    /**
     * 按配置创建裸 wire 读取器（[AvroReadFeature.AVRO_BUFFERING] 生效）。
     *
     * @param writerSchema writer Schema
     * @param readerSchema reader Schema
     * @param maxDepth Node 最大嵌套深度，0 表示不限制
     * @param config Avro 配置
     * @return 裸 wire 读取器
     */
    fun wireReader(
        writerSchema: Schema,
        readerSchema: Schema,
        maxDepth: Int,
        config: AvroConfig,
    ): AvroWireReader = AvroWireReader(writerSchema, readerSchema, maxDepth, config.avroBufferingOnRead)
}
