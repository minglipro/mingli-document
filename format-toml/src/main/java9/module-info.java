/**
 * {@code mingli-document} TOML 格式模块（format-toml）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.format.toml {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":core")) / api(project(":exception")) /
    // api(project(":serialization"))：公开 API 以 Node 树为输入/输出，Mapper 继承
    // serialization 的 DocumentMapper。
    requires transitive com.mingliqiye.document.core;
    requires transitive com.mingliqiye.document.exception;
    requires transitive com.mingliqiye.document.serialization;

    // tomlj 1.0.0 没有显式 module-info，清单也没有 Automatic-Module-Name，
    // 由 jar 文件名推导为自动模块 tomlj（自动模块导出全部包）。
    // tomlj 的实现依赖 antlr4-runtime（自动模块），运行时 module path 上需要提供
    //（Gradle 传递依赖已包含）；本模块不直接引用 antlr 类型，因此不显式 requires。
    requires tomlj;

    exports com.mingliqiye.document.format.toml;
    exports com.mingliqiye.document.format.toml.exception;
}
