package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentBuilder;

/**
 * Builder 缺少构建方法的测试模型：工厂方法存在，但 Builder 上既没有
 * {@code @DocumentBuild} 方法，也没有无参 {@code build()}，用于验证明确报错。
 */
public final class MissingBuildMethodModel {

    private final String value;

    private MissingBuildMethodModel(String value) {
        this.value = value;
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
    }
}
