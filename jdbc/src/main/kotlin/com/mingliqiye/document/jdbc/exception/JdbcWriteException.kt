package com.mingliqiye.document.jdbc.exception

/**
 * `PreparedStatement` 参数绑定、SQL 生成或待写入值转换失败。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public class JdbcWriteException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : JdbcException(message, throwable, enableSuppression, writableStackTrace)
