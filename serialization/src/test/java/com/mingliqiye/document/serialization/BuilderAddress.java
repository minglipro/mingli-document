package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentBuilder;

/**
 * 嵌套 Builder 模式不可变 Bean 测试模型。
 *
 * Builder 上没有 {@code @DocumentBuild}，构建方法为无参 {@code build()}，
 * 覆盖未标注时的回退逻辑以及作为嵌套属性时的递归 Builder 反序列化。
 */
public final class BuilderAddress {

    private final String city;

    private final int zipCode;

    private BuilderAddress(Builder builder) {
        this.city = builder.city;
        this.zipCode = builder.zipCode;
    }

    @DocumentBuilder
    public static Builder builder() {
        return new Builder();
    }

    public String getCity() {
        return city;
    }

    public int getZipCode() {
        return zipCode;
    }

    /**
     * 未标注 {@code @DocumentBuild} 的 Builder：Mapper 回退到无参 {@code build()}。
     */
    public static final class Builder {

        private String city;

        private int zipCode;

        public Builder setCity(String city) {
            this.city = city;
            return this;
        }

        public Builder setZipCode(int zipCode) {
            this.zipCode = zipCode;
            return this;
        }

        public BuilderAddress build() {
            return new BuilderAddress(this);
        }
    }
}
