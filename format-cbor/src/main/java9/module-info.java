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

    // 三个 上游实现 2.20.0 jar 都是 Multi-Release 显式模块：
    //   JSON Token 底层库          -> com.fasterxml.上游库.core
    //   数据绑定底层库      -> com.fasterxml.上游库.databind
    //   CBOR 底层库 -> com.fasterxml.上游库.dataformat.cbor
    // 本模块用 CBORFactory/CBORParser/CBORGenerator 读写，并读取
    // CBORParser.Feature / CBORGenerator.Feature，因此三个模块都要显式 requires。
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.cbor;

    exports com.mingliqiye.document.format.cbor;
    exports com.mingliqiye.document.format.cbor.exception;
}
