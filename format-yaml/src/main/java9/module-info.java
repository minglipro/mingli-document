/**
 * {@code mingli-document} YAML 格式模块（format-yaml）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.format.yaml {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":core")) / api(project(":exception")) /
    // api(project(":serialization"))：公开 API 以 Node 树为输入/输出，Mapper 继承
    // serialization 的 DocumentMapper。
    requires transitive com.mingliqiye.document.core;
    requires transitive com.mingliqiye.document.exception;
    requires transitive com.mingliqiye.document.serialization;

    // snakeyaml 2.6 自带 Multi-Release 显式 module-info，模块名 org.yaml.snakeyaml
    //（它的 module-info 对 java.desktop / java.sql 只是 requires static，本模块无需声明）。
    requires org.yaml.snakeyaml;

    exports com.mingliqiye.document.format.yaml;
    exports com.mingliqiye.document.format.yaml.exception;
}
