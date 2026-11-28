/**
 * {@code mingli-document} Java record 支持模块（record-support）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>本模块主 classes 的目标字节码是 JDK 17（class file 61），只在 JDK 17+ 运行期使用；
 * 模块描述本身按 JDK 9 级别编译，放进 Multi-Release 目录后由 JDK 9+ 的模块系统读取。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.record.support {
    requires transitive kotlin.stdlib;

    // 对应 Gradle 的 api(project(":serialization"))：实现 serialization 模块导出的
    // JavaRecordSupport SPI，并使用 JavaRecordComponentInfo 等公开类型。
    requires transitive com.mingliqiye.document.serialization;

    // serialization 的 module-info 声明了 uses com.mingliqiye.document.serialization
    // .JavaRecordSupport；这里把 Jdk17RecordSupport 注册为服务实现。JDK 17+ 下把本 jar
    // 放到 module path 时，消费方需要解析本模块（requires 或 --add-modules）才能被
    // ServiceLoader 找到；classpath 使用方式继续走 META-INF/services 文件（保留）。
    provides com.mingliqiye.document.serialization.JavaRecordSupport
            with com.mingliqiye.document.serialization.record.Jdk17RecordSupport;

    exports com.mingliqiye.document.serialization.record;
}
