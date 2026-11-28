/**
 * {@code mingli-document} CBOR 格式模块（format-cbor）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.format.cbor {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":core")) / api(project(":exception")) /
    // api(project(":serialization"))：公开 API 以 Node 树为输入/输出，Mapper 继承
    // serialization 的 BinaryDocumentMapper，并抛出 DocumentException 家族异常。
    requires transitive com.mingliqiye.document.core;
    requires transitive com.mingliqiye.document.exception;
    requires transitive com.mingliqiye.document.serialization;

    // 三个 Jackson 2.15.3 jar 都是 Multi-Release 显式模块：
    //   jackson-core          -> com.fasterxml.jackson.core
    //   jackson-databind      -> com.fasterxml.jackson.databind
    //   jackson-dataformat-cbor -> com.fasterxml.jackson.dataformat.cbor
    // 本模块直接用 tree 模型（JsonNode/ObjectMapper）桥接 jackson-databind，
    // 用 JsonParser/JsonToken 与 CBORFactory 读写，因此三个模块都要显式 requires。
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.cbor;

    exports com.mingliqiye.document.format.cbor;
    exports com.mingliqiye.document.format.cbor.exception;
}
