/**
 * {@code mingli-document} JPA + Hibernate 模块（jpa-hibernate）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.jpa.hibernate {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":jpa"))：公开 API 的构造参数与属性直接暴露
    // JpaConfig / JpaDocumentMapper / JpaNullPolicy，消费方需要可读 jpa 模块。
    requires transitive com.mingliqiye.document.jpa;
    // 公开 API 的返回值 / 参数直接使用 core 的 Node、ObjectNode。
    requires transitive com.mingliqiye.document.core;
    // 公开异常继承 DocumentException（exception 模块），本模块直接引用该类型。
    // core / jpa 已以 requires transitive 传递可读性，这里保留显式 requires。
    requires com.mingliqiye.document.exception;

    // 对应 Gradle 的 api("jakarta.persistence:jakarta.persistence-api:3.1.0")：
    // jakarta.persistence-api 3.1.0 自带显式 module-info，模块名为 jakarta.persistence，
    // 公开方法直接接收 EntityManager，并抛出 PersistenceException。
    requires transitive jakarta.persistence;

    // 对应 Gradle 的 api("org.hibernate.orm:hibernate-core:6.5.3.Final")：
    // Hibernate 6.5.3 没有显式 module-info，清单带
    // Automatic-Module-Name: org.hibernate.orm.core（自动模块），公开方法直接接收
    // org.hibernate.Session。按本库对自动模块的约定只用普通 requires：消费方若直接调用
    // Session 重载或使用 Hibernate 类型，需要自行 requires org.hibernate.orm.core。
    // Hibernate 的其它运行期依赖（byte-buddy、jboss-logging 等）也都是自动模块，
    // 运行时 module path 上必须提供（Gradle 传递依赖已包含）。
    requires org.hibernate.orm.core;

    // 实体列映射复用 jpa 模块：@Temporal / java.sql 类型与 java.beans 属性读取的
    // requires java.sql / java.desktop 已由 jpa 模块声明（jpa requires java.sql、java.desktop，
    // 且 jakarta.persistence requires transitive java.sql），本模块自身不直接引用这两个模块的类。

    exports com.mingliqiye.document.jpa.hibernate;
    exports com.mingliqiye.document.jpa.hibernate.exception;
}
