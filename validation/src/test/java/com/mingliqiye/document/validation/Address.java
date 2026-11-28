package com.mingliqiye.document.validation;

import jakarta.validation.constraints.NotNull;

/**
 * Bean Validation 测试模型：作为 {@link Person} 的嵌套对象参与级联校验。
 */
public class Address {
    @NotNull(message = "street must not be null")
    private String street;

    public Address() {
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }
}
