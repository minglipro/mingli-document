package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentCreator;
import com.mingliqiye.document.annotation.DocumentName;

/**
 * 不可变 Bean 测试模型：包含没有 setter、也不是构造参数的只读属性。
 *
 * 序列化时只读属性正常写出；反序列化时应跳过该字段而不是当作未知字段报错。
 */
public final class CreatorReadOnlyModel {

    private final String name;

    @DocumentCreator
    public CreatorReadOnlyModel(@DocumentName("name") String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @DocumentName("read_only_label")
    public String getReadOnlyLabel() {
        return name == null ? "?" : name.toUpperCase();
    }
}
