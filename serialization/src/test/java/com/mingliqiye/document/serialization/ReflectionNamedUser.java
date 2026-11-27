package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentCreator;

/**
 * 不可变 Bean 测试模型：构造参数没有 {@link com.mingliqiye.document.annotation.DocumentName}，
 * 依赖编译时保留的参数名（测试源码按 {@code -parameters} 编译）。
 */
public final class ReflectionNamedUser {

    private final String name;

    private final int age;

    @DocumentCreator
    public ReflectionNamedUser(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }
}
