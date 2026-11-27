package com.mingliqiye.document.serialization;

/**
 * 无法实例化的 Bean 测试模型：没有 {@code @DocumentCreator}、没有无参构造器，
 * 也没有 {@code @DocumentBuilder} 静态工厂，用于验证明确的报错信息。
 */
public final class NoBuilderFactoryModel {

    private final String value;

    public NoBuilderFactoryModel(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
