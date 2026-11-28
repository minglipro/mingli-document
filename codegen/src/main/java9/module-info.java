/**
 * {@code mingli-document} KSP 代码生成模块（codegen）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.codegen {
    requires transitive kotlin.stdlib;

    // symbol-processing-api 2.0.0-1.0.21 没有显式 module-info、也没有
    // Automatic-Module-Name，按文件名推导出的自动模块名是 symbol.processing.api。
    // 本模块直接实现 com.google.devtools.ksp.processing.SymbolProcessorProvider /
    // SymbolProcessor 等 SPI，消费方把 codegen 放到 module path 时也必须同时提供
    // KSP API jar（通常在 KSP 构建插件里以 classpath 方式处理处理器，不受影响）。
    requires symbol.processing.api;

    // KSP 通过 META-INF/services（classpath）或 module-info 的 provides（module path）
    // 发现处理器；这里是 module path 场景的服务声明，classpath 场景仍使用
    // src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider。
    provides com.google.devtools.ksp.processing.SymbolProcessorProvider
            with com.mingliqiye.document.codegen.DocumentEnumCodecProcessorProvider;

    exports com.mingliqiye.document.codegen;
}
