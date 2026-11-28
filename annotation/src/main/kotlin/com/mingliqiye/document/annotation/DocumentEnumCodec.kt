package com.mingliqiye.document.annotation

/**
 * 标记需要生成枚举转换器与文档模块的枚举。
 *
 * 编译期由 `mingli-document-codegen` 中的 KSP 处理器扫描并生成 Kotlin 源码：
 *
 * - `<枚举名>DocumentConverter : DocumentBaseConverter<枚举>`：序列化时写出枚举
 *   `name`，反序列化时按 `valueOf` 还原，非法名称返回 `null`；
 * - 一个 `DocumentModule`：在 `setupModule` 中注册全部生成的转换器，默认类名为
 *   `GeneratedDocumentModule`，默认包名为 `com.mingliqiye.document.generated`。
 *
 * 注解只保留在源码期（[AnnotationRetention.SOURCE]），处理器读取源码元数据后即丢弃，
 * 不进入运行时字节码。被标注的枚举必须是 public 顶层枚举或 public 嵌套枚举，
 * 且生成的转换器类名与包名在同一个编译单元内不能冲突。
 *
 * @property converterPackage 生成转换器的包名；空字符串表示使用默认包
 *   `com.mingliqiye.document.generated`
 * @property converterName 生成转换器的类名；空字符串表示 `<枚举名>DocumentConverter`
 * @property moduleName 生成文档模块的类名；空字符串表示默认模块名
 *   `GeneratedDocumentModule`。多个枚举同时指定时必须使用相同的包名与类名，
 *   模块包名取自定义该参数的枚举的 [converterPackage]
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
public annotation class DocumentEnumCodec(
    val converterPackage: String = "",
    val converterName: String = "",
    val moduleName: String = "",
)
