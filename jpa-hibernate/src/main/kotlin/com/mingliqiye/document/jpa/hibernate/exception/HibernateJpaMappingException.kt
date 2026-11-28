package com.mingliqiye.document.jpa.hibernate.exception

/**
 * Hibernate 运行期映射异常。
 *
 * 用于代理处理失败、循环引用、未知实体、已关闭的 `EntityManager` 等
 * 由 Hibernate 运行期状态导致的映射错误。jpa 模块自身的元数据/映射错误
 * （`JpaException` 子类）会原样向上抛出，不会被包装成本类。
 *
 * @param message 异常信息
 * @param throwable 异常原因
 * @param enableSuppression 是否启用异常抑制
 * @param writableStackTrace 是否生成堆栈信息
 */
public open class HibernateJpaMappingException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null,
    enableSuppression: Boolean = false,
    writableStackTrace: Boolean = true,
) : HibernateJpaException(message, throwable, enableSuppression, writableStackTrace)
