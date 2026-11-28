package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentInclude;
import com.mingliqiye.document.annotation.DocumentIncludePolicy;

/**
 * 类级 {@link DocumentInclude} ALWAYS 测试模型：即使全局关闭写出 null，也总是写出。
 */
@DocumentInclude(DocumentIncludePolicy.ALWAYS)
public class IncludeAlwaysBean {

    private String nullValue;

    private String text;

    public String getNullValue() {
        return nullValue;
    }

    public void setNullValue(String nullValue) {
        this.nullValue = nullValue;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
