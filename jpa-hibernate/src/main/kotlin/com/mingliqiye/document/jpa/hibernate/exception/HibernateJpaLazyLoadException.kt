package com.mingliqiye.document.jpa.hibernate.exception

/**
 * 懒加载初始化异常。
 *
 * 当映射过程需要访问未初始化的 Hibernate 代理或持久化集合，而当前策略
 * 不允许初始化（[com.mingliqiye.document.jpa.hibernate.HibernateLazyLoadPolicy.FAIL]）、
 * 没有可用的 `Session`、`Session` 已关闭，或 `Hibernate.initialize` 失败时抛出。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public class HibernateJpaLazyLoadException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : HibernateJpaMappingException(message, throwable, enableSuppression, writableStackTrace)
