/**
 * {@code mingli-document} 异常模块（exception）的 JPMS 模块描述。
 *
 * <p>本文件位于 {@code src/main/java9}，不参与 JDK 8 的主源码编译：构建时由 JDK 17 工具链的
 * {@code javac --release 9} 单独编译，并打入 Multi-Release jar 的
 * {@code META-INF/versions/9/module-info.class}。JDK 8 会完全忽略该版本目录，行为不变。</p>
 *
 * <p>模块名必须与主 jar 清单中的 {@code Automatic-Module-Name} 保持一致。</p>
 */
module com.mingliqiye.document.exception {
    requires transitive kotlin.stdlib;

    // mingli-exception 没有显式 module-info，按 Automatic-Module-Name 解析为 mingli.exception。
    // 它属于自动模块：这里只做普通 requires（不使用 requires transitive），
    // 消费方若直接使用 MingLiBaseException 需自行 requires mingli.exception。
    requires mingli.exception;

    exports com.mingliqiye.document.exception;
}
