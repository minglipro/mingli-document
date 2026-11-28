/**
 * {@code mingli-document} CSV 格式模块（format-csv）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.format.csv {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":core")) / api(project(":exception")) /
    // api(project(":schema")) / api(project(":serialization"))：公开 API 以 Node 树与
    // CsvSchema/DocumentSchema 为输入/输出，Mapper 继承 serialization 的 DocumentMapper。
    requires transitive com.mingliqiye.document.core;
    requires transitive com.mingliqiye.document.exception;
    requires transitive com.mingliqiye.document.schema;
    requires transitive com.mingliqiye.document.serialization;

    // commons-csv 1.10.0 没有显式 module-info，清单带
    // Automatic-Module-Name: org.apache.commons.csv，按自动模块 requires。
    requires org.apache.commons.csv;

    exports com.mingliqiye.document.format.csv;
    exports com.mingliqiye.document.format.csv.exception;
}
