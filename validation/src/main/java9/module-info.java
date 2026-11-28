/**
 * {@code mingli-document} Bean Validation 模块（validation）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.validation {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":serialization"))：公开类实现 DocumentBeanValidator，
    // 并抛出 DocumentSerializationException。
    requires transitive com.mingliqiye.document.serialization;
    // jakarta.validation-api 3.0.2 自带显式 module-info，模块名为 jakarta.validation。
    // 公开构造器与静态方法直接暴露 Validator / ConstraintViolation /
    // ConstraintViolationException，消费方需要可读。
    requires transitive jakarta.validation;

    exports com.mingliqiye.document.validation;
}
