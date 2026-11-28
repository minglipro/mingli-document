package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.serialization.DocumentMapperApi
import com.mingliqiye.document.serialization.DocumentObjectReader
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.nio.file.Path

/**
 * Kotlin 专用的文档映射扩展函数。
 *
 * 这些 API 依赖 `reified` 类型参数，因此放在 `serialization-kotlin` 模块，
 * 不进入纯 Java 的 `serialization` 模块。使用前需要添加：
 *
 * ```kotlin
 * implementation("com.mingliqiye.document:serialization-kotlin")
 * ```
 *
 * 非 reified 版本仍然位于 [DocumentMapperApi]。
 */

/**
 * 按 reified 泛型参数从文件解析 Java 实体。
 */
public inline fun <reified T> DocumentMapperApi.parse(file: File): T = parse(file, DocumentTypeReference.of<T>())

/**
 * 按 reified 泛型参数从路径解析 Java 实体。
 */
public inline fun <reified T> DocumentMapperApi.parse(path: Path): T = parse(path, DocumentTypeReference.of<T>())

/**
 * 按 reified 泛型参数从输入流解析 Java 实体。
 */
public inline fun <reified T> DocumentMapperApi.parse(input: InputStream): T = parse(input, DocumentTypeReference.of<T>())

/**
 * 按 reified 泛型参数从字符流解析 Java 实体。
 */
public inline fun <reified T> DocumentMapperApi.parse(reader: Reader): T = parse(reader, DocumentTypeReference.of<T>())

/**
 * 按 reified 泛型参数把文档树反序列化为 Java 实体。
 */
public inline fun <reified T> DocumentMapperApi.fromNode(node: Node): T = fromNode(node, DocumentTypeReference.of<T>())

/**
 * 按 reified 泛型参数把文本解析为 Java 实体。
 */
public inline fun <reified T> DocumentMapperApi.parse(source: String): T = parse(source, DocumentTypeReference.of<T>())

/**
 * 按 reified 泛型参数把字节数组解析为 Java 实体。
 */
public inline fun <reified T> DocumentMapperApi.parse(source: ByteArray): T = parse(source, DocumentTypeReference.of<T>())

/**
 * 按 reified 泛型参数把任意对象转换为 Java 实体。
 */
public inline fun <reified T> DocumentMapperApi.convert(source: Any?): T = convert(source, DocumentTypeReference.of<T>())

/**
 * 按 reified 泛型参数创建对象读取器。
 */
public inline fun <reified T> DocumentMapperApi.readerFor(): DocumentObjectReader<T> = readerFor(DocumentTypeReference.of<T>())
