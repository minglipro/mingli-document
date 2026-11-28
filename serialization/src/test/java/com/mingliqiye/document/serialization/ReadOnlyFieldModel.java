package com.mingliqiye.document.serialization;

/**
 * 只读字段测试模型：public final 字段没有 setter，反序列化时应跳过。
 */
public final class ReadOnlyFieldModel {

    public final String tag;

    public String name;

    public ReadOnlyFieldModel() {
        this.tag = "fixed";
    }
}
