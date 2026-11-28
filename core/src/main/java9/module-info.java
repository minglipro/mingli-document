/**
 * {@code mingli-document} 核心模块（core）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.core {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":exception"))：core 的公开 API 会抛出
    // DocumentTypeException，消费方捕获它时需要可读 exception 模块。
    requires transitive com.mingliqiye.document.exception;

    exports com.mingliqiye.document.core;
}
