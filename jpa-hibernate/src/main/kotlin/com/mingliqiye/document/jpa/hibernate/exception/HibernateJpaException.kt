package com.mingliqiye.document.jpa.hibernate.exception

import com.mingliqiye.document.exception.DocumentException

/**
 * Hibernate 集成异常基类。
 *
 * 所有 `jpa-hibernate` 模块抛出的 Hibernate 运行期异常都继承本类，
 * 因此可以统一按 [DocumentException] 捕获。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public open class HibernateJpaException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : DocumentException(message, throwable, enableSuppression, writableStackTrace)
