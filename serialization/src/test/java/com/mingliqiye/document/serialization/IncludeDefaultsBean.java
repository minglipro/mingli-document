package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentInclude;
import com.mingliqiye.document.annotation.DocumentIncludePolicy;

/**
 * 字段级 {@link DocumentInclude} USE_DEFAULTS 测试模型：没有类级策略时回退全局
 * {@code writeNulls}。
 */
public class IncludeDefaultsBean {

    @DocumentInclude(DocumentIncludePolicy.USE_DEFAULTS)
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
