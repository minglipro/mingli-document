package com.mingliqiye.document.serialization;

public class Address {
    private String city;
    private int zipCode;

    public Address() {
    }

    public Address(String city, int zipCode) {
        this.city = city;
        this.zipCode = zipCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public int getZipCode() {
        return zipCode;
    }

    public void setZipCode(int zipCode) {
        this.zipCode = zipCode;
    }
}
