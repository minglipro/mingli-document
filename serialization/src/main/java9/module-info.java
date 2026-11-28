/**
 * {@code mingli-document} 序列化模块（serialization）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.serialization {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":core")) / api(project(":exception"))：
    // 公开 API 以 Node 树为输入/输出，并抛出 DocumentException 家族异常。
    requires transitive com.mingliqiye.document.core;
    requires transitive com.mingliqiye.document.exception;
    // 对应 Gradle 的 api(project(":annotation"))：消费方给自己的 Bean 标注
    // @DocumentName / @DocumentCreator 等注解时需要可读。
    requires transitive com.mingliqiye.document.annotation;

    // alias-for 1.0.4 提供 Automatic-Module-Name=com.mingliqiye.utils.aliasfor（自动模块）。
    // 只在模块内部用于读取注解，消费方一般不需要直接读取它，因此用普通 requires。
    requires com.mingliqiye.utils.aliasfor;
    // mingli-exception 没有显式 module-info，按文件名解析为 mingli.exception（自动模块）；
    // 公开异常继承链含 MingLiBaseException，消费方直接使用该类型时需自行 requires。
    requires mingli.exception;

    // JavaBean 属性读取依赖 java.beans.Introspector（java.beans 属于 java.desktop）。
    requires java.desktop;
    // 日期时间转换器支持 java.sql.Date / java.sql.Timestamp（java.sql 模块）。
    requires java.sql;

    exports com.mingliqiye.document.serialization;
    exports com.mingliqiye.document.serialization.codegen;
    exports com.mingliqiye.document.serialization.exception;
}
