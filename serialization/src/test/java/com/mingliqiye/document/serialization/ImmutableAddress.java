package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentCreator;
import com.mingliqiye.document.annotation.DocumentName;

/**
 * 不可变 Bean 测试模型：字段全部 final，只能通过 {@link DocumentCreator} 构造器创建。
 */
public final class ImmutableAddress {

    private final String city;

    private final int zipCode;

    @DocumentCreator
    public ImmutableAddress(
            @DocumentName("city") String city,
            @DocumentName("zip_code") int zipCode) {
        this.city = city;
        this.zipCode = zipCode;
    }

    public String getCity() {
        return city;
    }

    public int getZipCode() {
        return zipCode;
    }
}
