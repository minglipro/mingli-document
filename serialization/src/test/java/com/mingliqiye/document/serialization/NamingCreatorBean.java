package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentCreator;
import com.mingliqiye.document.annotation.DocumentNaming;
import com.mingliqiye.document.annotation.DocumentNamingStrategy;

/**
 * 类级 {@link DocumentNaming} + {@link DocumentCreator} 构造器注入测试模型。
 *
 * 构造参数没有 {@link com.mingliqiye.document.annotation.DocumentName}，依赖
 * 类级 snake_case 把参数名（编译期 -parameters 保留）转换为文档字段名，读写对称。
 */
@DocumentNaming(DocumentNamingStrategy.SnakeCaseStrategy)
public final class NamingCreatorBean {

    private final String userName;

    private final int itemCount;

    @DocumentCreator
    public NamingCreatorBean(String userName, int itemCount) {
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
