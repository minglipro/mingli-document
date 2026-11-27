package com.mingliqiye.document.annotation;

public class AnnotationModel {
    @DocumentName("display_name")
    @DocumentIgnore
    private String value;

    @DocumentConverter(SampleConverter.class)
    private String converted;

    @DocumentSerializer(SampleSerializer.class)
    private String serialized;

    @DocumentDeserializer(SampleDeserializer.class)
    private String deserialized;

    public String getValue() {
        return value;
    }

    public String getConverted() {
        return converted;
    }

    public String getSerialized() {
        return serialized;
    }

    public String getDeserialized() {
        return deserialized;
    }
}