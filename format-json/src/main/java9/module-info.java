/**
 * {@code mingli-document} JSON 格式模块（format-json）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.format.json {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":core")) / api(project(":exception")) /
    // api(project(":serialization"))：公开 API 以 Node 树为输入/输出，JsonMapper 继承
    // serialization 的 DocumentMapper，并抛出 DocumentException 家族异常。
    requires transitive com.mingliqiye.document.core;
    requires transitive com.mingliqiye.document.exception;
    requires transitive com.mingliqiye.document.serialization;

    // jackson-core 2.15.3 自带 Multi-Release 显式 module-info，模块名 com.fasterxml.jackson.core。
    //
    // 已知限制：本模块的 JsonStreamWriter / JsonUtf8StreamWriter 直接使用 jackson-core 的
    // 内部包 com.fasterxml.jackson.core.io.schubfach.DoubleToDecimal 输出最短浮点表示，
    // 而 jackson-core 的 module-info 没有导出该包（只在模块内部使用）。因此显式模块化后，
    // 在 module path 上写出 Double 需要消费方追加：
    //   --add-exports com.fasterxml.jackson.core/com.fasterxml.jackson.core.io.schubfach=com.mingliqiye.document.format.json
    // 整数、字符串、布尔等其它类型以及 classpath 使用方式不受影响；修复需要 format-json
    // 主源码不再引用 jackson 内部包，属于后续工作（见 docs/guide/jpms.md 的限制）。
    requires com.fasterxml.jackson.core;

    exports com.mingliqiye.document.format.json;
    exports com.mingliqiye.document.format.json.exception;
    exports com.mingliqiye.document.format.json.schema;
}
