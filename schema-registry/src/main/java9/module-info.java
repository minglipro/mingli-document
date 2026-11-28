/**
 * {@code mingli-document} Schema Registry 模块（schema-registry）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.schema.registry {
    requires transitive kotlin.stdlib;

    requires transitive com.mingliqiye.document.core;
    requires transitive com.mingliqiye.document.exception;
    requires transitive com.mingliqiye.document.schema;

    exports com.mingliqiye.document.registry;
    exports com.mingliqiye.document.registry.exception;
}
