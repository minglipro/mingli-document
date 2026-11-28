package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentName;
import com.mingliqiye.document.annotation.DocumentNaming;
import com.mingliqiye.document.annotation.DocumentNamingStrategy;

/**
 * 类级/字段级 {@link DocumentNaming} 测试模型。
 *
 * 类级为 snake_case；{@code displayName} 字段级覆盖为 kebab-case；
 * {@code getterNamed} 的 getter 上覆盖为 UpperCamel；{@code explicitName}
 * 使用 {@link DocumentName} 显式命名。
 */
@DocumentNaming(DocumentNamingStrategy.SnakeCaseStrategy)
public class NamingOverrideBean {

    private String userName;

    private String lastLoginAt;

    @DocumentNaming(DocumentNamingStrategy.KebabCaseStrategy)
    private String displayName;

    @DocumentName("explicit_name")
    private String explicitValue;

    private String getterNamed;

    private NamingOverrideChild child;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(String lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getExplicitValue() {
        return explicitValue;
    }

    public void setExplicitValue(String explicitValue) {
        this.explicitValue = explicitValue;
    }

    @DocumentNaming(DocumentNamingStrategy.UpperCamelStrategy)
    public String getGetterNamed() {
        return getterNamed;
    }

    public void setGetterNamed(String getterNamed) {
        this.getterNamed = getterNamed;
    }

    public NamingOverrideChild getChild() {
        return child;
    }

    public void setChild(NamingOverrideChild child) {
        this.child = child;
    }
}
