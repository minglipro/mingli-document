/**
 * {@code mingli-document} Kotlin 序列化扩展模块（serialization-kotlin）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.serialization.kotlin {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":serialization"))：公开 API（KotlinModule、
    // KotlinDocumentMapperExtensions、KotlinDataClassInstantiator 等）直接扩展/实现
    // serialization 的 DocumentMapper/Module/PropertyMetadataResolver 等类型。
    requires transitive com.mingliqiye.document.serialization;
    // 公开 API 的入参/返回值直接使用 core 的 Node / ObjectNode / DocumentTypeReference。
    // serialization 已 requires transitive core，这里保留显式声明以表达直接依赖。
    requires transitive com.mingliqiye.document.core;

    // 模块内部读取 @DocumentIgnore / @DocumentName / @DocumentTypeName 等注解，
    // 注解类型不进入公开 API 签名，因此用普通 requires。
    requires com.mingliqiye.document.annotation;
    // 模块内部直接抛出/捕获 DocumentSerializationException（位于 serialization.exception 包），
    // 同样不进入公开 API 签名，用普通 requires。
    requires com.mingliqiye.document.exception;

    // kotlin-reflect 2.0.0 自带 Multi-Release 显式 module-info，模块名为 kotlin.reflect；
    // 本模块的 data class / value class / sealed class 解析依赖 kotlin.reflect.full 与
    // kotlin.reflect.jvm 的实现。
    requires kotlin.reflect;

    // Kotlin data class 属性读取复用 kotlin-stdlib 的 java.beans.Introspector（java.beans
    // 位于 java.desktop 模块）。
    requires java.desktop;

    exports com.mingliqiye.document.serialization.kotlin;
}
