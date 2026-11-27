package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentBuild;
import com.mingliqiye.document.annotation.DocumentBuilder;
import java.util.List;

/**
 * Builder 模式不可变 Bean 测试模型。
 *
 * 目标类只有私有构造器，通过 {@link DocumentBuilder} 静态工厂创建 Builder，
 * 再通过 {@link DocumentBuild} 标注的构建方法得到实例。属性覆盖基本类型、
 * 嵌套 Builder Bean 与 Builder Bean 集合。
 */
public final class BuilderUser {

    private final String name;

    private final int age;

    private final BuilderAddress address;

    private final List<String> tags;

    private final List<BuilderAddress> formerAddresses;

    private BuilderUser(Builder builder) {
        this.name = builder.name;
        this.age = builder.age;
        this.address = builder.address;
        this.tags = builder.tags;
        this.formerAddresses = builder.formerAddresses;
    }

    /**
     * 创建 Builder 的静态工厂方法。
     */
    @DocumentBuilder
    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public BuilderAddress getAddress() {
        return address;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<BuilderAddress> getFormerAddresses() {
        return formerAddresses;
    }

    /**
     * Builder 模式构建器：字段可写，构建方法返回不可变的 {@link BuilderUser}。
     */
    public static final class Builder {

        private String name;

        private int age;

        private BuilderAddress address;

        private List<String> tags;

        private List<BuilderAddress> formerAddresses;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setAge(int age) {
            this.age = age;
            return this;
        }

        public Builder setAddress(BuilderAddress address) {
            this.address = address;
            return this;
        }

        public Builder setTags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Builder setFormerAddresses(List<BuilderAddress> formerAddresses) {
            this.formerAddresses = formerAddresses;
            return this;
        }

        @DocumentBuild
        public BuilderUser build() {
            return new BuilderUser(this);
        }
    }
}
