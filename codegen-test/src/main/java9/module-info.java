/**
 * {@code mingli-document} 代码生成测试模块（codegen-test）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>本模块是 codegen 的示例/测试支持模块：主源码里的注解触发 KSP 在编译期生成
 * {@code com.mingliqiye.document.generated} 包，生成代码随主 jar 一起发布。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.codegen.test {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":annotation")) / api(project(":serialization"))：
    // 主源码的 @DocumentEnumCodec 等注解来自 annotation，生成代码实现/使用 serialization
    // 的 DocumentModule、DocumentBaseConverter 等类型（生成代码中的 core 类型由
    // serialization 的 requires transitive 提供可读性）。
    requires transitive com.mingliqiye.document.annotation;
    requires transitive com.mingliqiye.document.serialization;

    // codegen 是 KSP 处理器，只在编译期由 ksp(...) 配置参与，运行期不需要 require。

    exports com.mingliqiye.document.codegen.test;
    exports com.mingliqiye.document.generated;
    exports com.mingliqiye.document.generated.custom;
}
