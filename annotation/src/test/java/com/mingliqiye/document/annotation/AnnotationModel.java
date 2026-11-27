package com.mingliqiye.document.annotation;

public class AnnotationModel {
    @DocumentName("display_name")
    @DocumentIgnore
    private String value;

    @DocumentConverter(SampleConverter.class)
    private String converted;

    public String getValue() {
        return value;
    }

    public String getConverted() {
        return converted;
    }
}
