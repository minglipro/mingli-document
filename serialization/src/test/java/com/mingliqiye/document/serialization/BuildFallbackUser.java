package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentBuilder;

/**
 * 顶层 Builder 回退测试模型：Builder 未标注 {@code @DocumentBuild}，只有无参 {@code build()}。
 */
public final class BuildFallbackUser {

    private final String value;

    private BuildFallbackUser(Builder builder) {
        this.value = builder.value;
    }

    @DocumentBuilder
    public static Builder builder() {
        return new Builder();
    }

    public String getValue() {
        return value;
    }

    public static final class Builder {

        private String value;

        public Builder setValue(String value) {
            this.value = value;
            return this;
        }

        public BuildFallbackUser build() {
            return new BuildFallbackUser(this);
        }
    }
}
