package com.mingliqiye.document.validation;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Bean Validation 测试模型：集合元素上的容器元素约束。
 */
public class TagBag {
    private List<@NotNull(message = "element must not be null") String> tags;

    public TagBag() {
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
