package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentCreator;
import com.mingliqiye.document.annotation.DocumentNaming;
import com.mingliqiye.document.annotation.DocumentNamingStrategy;

/**
 * {@link DocumentCreator} 构造参数上的 {@link DocumentNaming} 测试模型。
 *
 * 参数级命名策略优先于类级/全局策略，并同时用于读取与写出。
 */
public final class NamingParamBean {

    private final String userName;

    private final int itemCount;

    @DocumentCreator
    public NamingParamBean(
            @DocumentNaming(DocumentNamingStrategy.KebabCaseStrategy) String userName,
            @DocumentNaming(DocumentNamingStrategy.UpperCamelStrategy) int itemCount) {
        this.userName = userName;
        this.itemCount = itemCount;
    }

    public String getUserName() {
        return userName;
    }

    public int getItemCount() {
        return itemCount;
    }
}
