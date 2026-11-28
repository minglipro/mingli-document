package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentNaming;
import com.mingliqiye.document.annotation.DocumentNamingStrategy;

/**
 * 嵌套在 {@link NamingOverrideBean} 中的子 Bean：类级 kebab-case。
 */
@DocumentNaming(DocumentNamingStrategy.KebabCaseStrategy)
public class NamingOverrideChild {

    private String childName;

    private int zipCode;

    public String getChildName() {
        return childName;
    }

    public void setChildName(String childName) {
        this.childName = childName;
    }

    public int getZipCode() {
        return zipCode;
    }

    public void setZipCode(int zipCode) {
        this.zipCode = zipCode;
    }
}
