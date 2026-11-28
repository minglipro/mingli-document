/**
 * {@code mingli-document} JPA 模块（jpa）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.jpa {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":core")) / api(project(":serialization"))：
    // 公开 API 以 Node 树为输入/输出，并复用 PropertyNamingStrategy。
    requires transitive com.mingliqiye.document.core;
    requires transitive com.mingliqiye.document.serialization;
    // 公开异常继承 DocumentException（exception 模块），本模块直接引用该类型。
    // core / serialization 已以 requires transitive 传递可读性，这里保留显式 requires。
    requires com.mingliqiye.document.exception;

    // 对应 Gradle 的 api("jakarta.persistence:jakarta.persistence-api:3.1.0")：
    // jakarta.persistence-api 3.1.0 自带显式 module-info，模块名为 jakarta.persistence。
    // 消费方实体的 @Entity/@Column 等注解来自它，保持与 classpath 使用方式一致的传递可见性。
    requires transitive jakarta.persistence;

    // JavaBean 属性读取依赖 java.beans.Introspector（java.beans 属于 java.desktop）。
    requires java.desktop;
    // @Temporal 转换支持 java.sql.Date / java.sql.Time / java.sql.Timestamp。
    requires java.sql;

    exports com.mingliqiye.document.jpa;
    exports com.mingliqiye.document.jpa.exception;
}
