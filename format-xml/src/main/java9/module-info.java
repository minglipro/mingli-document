/**
 * {@code mingli-document} XML 格式模块（format-xml）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.format.xml {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":core")) / api(project(":exception")) /
    // api(project(":serialization"))：公开 API 以 Node 树为输入/输出，Mapper 继承
    // serialization 的 DocumentMapper。
    requires transitive com.mingliqiye.document.core;
    requires transitive com.mingliqiye.document.exception;
    requires transitive com.mingliqiye.document.serialization;

    // 对应 Gradle 的 implementation(project(":annotation"))：只在模块内部读取
    // @DocumentRootName，消费方一般不直接读取该注解；annotation 也可经 serialization 的
    // requires transitive 传递可见，消费方仍建议按需自行 requires。
    requires com.mingliqiye.document.annotation;

    // XML 读写使用 javax.xml.parsers（DocumentBuilderFactory）、org.w3c.dom 与 org.xml.sax，
    // 它们都在 JDK 的 java.xml 模块中。
    requires java.xml;

    exports com.mingliqiye.document.format.xml;
    exports com.mingliqiye.document.format.xml.exception;
}
