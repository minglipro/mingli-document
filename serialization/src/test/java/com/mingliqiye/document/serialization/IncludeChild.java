package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentInclude;
import com.mingliqiye.document.annotation.DocumentIncludePolicy;

/**
 * 嵌套/集合元素中的类级 {@link DocumentInclude} 测试模型：NON_NULL。
 */
@DocumentInclude(DocumentIncludePolicy.NON_NULL)
public class IncludeChild {

    private String name;

    private String nickname;

    private String emptyText;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmptyText() {
        return emptyText;
    }

    public void setEmptyText(String emptyText) {
        this.emptyText = emptyText;
    }
}
