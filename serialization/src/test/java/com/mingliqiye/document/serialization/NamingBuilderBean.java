package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentBuild;
import com.mingliqiye.document.annotation.DocumentBuilder;
import com.mingliqiye.document.annotation.DocumentNaming;
import com.mingliqiye.document.annotation.DocumentNamingStrategy;

/**
 * 类级 {@link DocumentNaming} + Builder 模式测试模型。
 *
 * 目标类标注类级 snake_case；Builder 是目标类的嵌套类，反序列化时通过封闭类上的
 * 类级注解继承命名策略，保证读写字段名对称。
 */
@DocumentNaming(DocumentNamingStrategy.SnakeCaseStrategy)
public final class NamingBuilderBean {

    private final String userName;

    private final int itemCount;

    private NamingBuilderBean(Builder builder) {
        this.userName = builder.userName;
        this.itemCount = builder.itemCount;
    }

    @DocumentBuilder
    public static NamingBuilderBean.Builder builder() {
        return new NamingBuilderBean.Builder();
    }

    public String getUserName() {
        return userName;
    }

    public int getItemCount() {
        return itemCount;
    }

    /** Builder。 */
    public static final class Builder {

        private String userName;

        private int itemCount;

        public Builder setUserName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder setItemCount(int itemCount) {
            this.itemCount = itemCount;
            return this;
        }

        @DocumentBuild
        public NamingBuilderBean build() {
            return new NamingBuilderBean(this);
        }
    }
}
