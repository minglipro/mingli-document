package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentInclude;
import com.mingliqiye.document.annotation.DocumentIncludePolicy;

/**
 * 类级 {@link DocumentInclude} 非空策略测试模型。
 *
 * 类级 NON_EMPTY 省略 null 与各类空值；字段级 {@link DocumentInclude} 覆盖类级：
 * {@code alwaysNull} 总是写出、{@code nonNullField} 仅非 null 写出、
 * {@code defaultsField} 使用 USE_DEFAULTS 回退到全局配置。
 */
@DocumentInclude(DocumentIncludePolicy.NON_EMPTY)
public class IncludeBean {

    private String nullText;

    private String emptyText;

    private String text;

    private java.util.List<String> emptyList;

    private java.util.List<String> tags;

    private java.util.Map<String, String> emptyMap;

    private int[] emptyArray;

    private int[] numbers;

    @DocumentInclude(DocumentIncludePolicy.ALWAYS)
    private String alwaysNull;

    @DocumentInclude(DocumentIncludePolicy.NON_NULL)
    private String nonNullField;

    @DocumentInclude(DocumentIncludePolicy.USE_DEFAULTS)
    private String defaultsField;

    private IncludeChild child;

    private java.util.List<IncludeChild> children;

    public String getNullText() {
        return nullText;
    }

    public void setNullText(String nullText) {
        this.nullText = nullText;
    }

    public String getEmptyText() {
        return emptyText;
    }

    public void setEmptyText(String emptyText) {
        this.emptyText = emptyText;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public java.util.List<String> getEmptyList() {
        return emptyList;
    }

    public void setEmptyList(java.util.List<String> emptyList) {
        this.emptyList = emptyList;
    }

    public java.util.List<String> getTags() {
        return tags;
    }

    public void setTags(java.util.List<String> tags) {
        this.tags = tags;
    }

    public java.util.Map<String, String> getEmptyMap() {
        return emptyMap;
    }

    public void setEmptyMap(java.util.Map<String, String> emptyMap) {
        this.emptyMap = emptyMap;
    }

    public int[] getEmptyArray() {
        return emptyArray;
    }

    public void setEmptyArray(int[] emptyArray) {
        this.emptyArray = emptyArray;
    }

    public int[] getNumbers() {
        return numbers;
    }

    public void setNumbers(int[] numbers) {
        this.numbers = numbers;
    }

    public String getAlwaysNull() {
        return alwaysNull;
    }

    public void setAlwaysNull(String alwaysNull) {
        this.alwaysNull = alwaysNull;
    }

    public String getNonNullField() {
        return nonNullField;
    }

    public void setNonNullField(String nonNullField) {
        this.nonNullField = nonNullField;
    }

    public String getDefaultsField() {
        return defaultsField;
    }

    public void setDefaultsField(String defaultsField) {
        this.defaultsField = defaultsField;
    }

    public IncludeChild getChild() {
        return child;
    }

    public void setChild(IncludeChild child) {
        this.child = child;
    }

    public java.util.List<IncludeChild> getChildren() {
        return children;
    }

    public void setChildren(java.util.List<IncludeChild> children) {
        this.children = children;
    }
}
