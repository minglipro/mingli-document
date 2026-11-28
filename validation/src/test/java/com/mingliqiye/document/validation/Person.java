package com.mingliqiye.document.validation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Bean Validation 测试模型：字段级约束 + 嵌套对象级联校验。
 */
public class Person {
    @NotNull(message = "name must not be null")
    private String name;

    @NotNull(message = "age must not be null")
    private Integer age;

    @Size(min = 1, max = 3, message = "tags size must be between 1 and 3")
    private List<String> tags;

    @Pattern(regexp = "[a-z]+", message = "code must match [a-z]+")
    private String code;

    @Valid
    private Address address;

    public Person() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }
}
