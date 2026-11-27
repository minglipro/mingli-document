package com.mingliqiye.document.serialization;

/**
 * Nested test model for property naming strategies.
 */
public class NamingChild {

    private String childName;
    private int zipCode;

    public NamingChild() {
    }

    public NamingChild(String childName, int zipCode) {
        this.childName = childName;
        this.zipCode = zipCode;
    }

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
