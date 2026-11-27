package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentBuild;
import com.mingliqiye.document.annotation.DocumentBuilder;
import com.mingliqiye.document.annotation.DocumentCreator;
import com.mingliqiye.document.annotation.DocumentName;

/**
 * 同时存在 {@code @DocumentCreator} 构造器与 {@code @DocumentBuilder} 工厂的测试模型。
 *
 * 构造器注入优先：Builder 的构建方法会给值加前缀，用于证明没有走 Builder 路径。
 */
public final class CreatorAndBuilderModel {

    private final String value;

    @DocumentCreator
    public CreatorAndBuilderModel(@DocumentName("value") String value) {
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

        @DocumentBuild
        public CreatorAndBuilderModel build() {
            return new CreatorAndBuilderModel("builder:" + value);
        }
    }
}
