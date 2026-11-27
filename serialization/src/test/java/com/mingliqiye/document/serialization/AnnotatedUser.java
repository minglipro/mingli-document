package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentIgnore;
import com.mingliqiye.document.annotation.DocumentName;

public class AnnotatedUser {
    @DocumentName("display_name")
    private String name;

    @DocumentIgnore
    private int age;

    @DocumentName("city_name")
    private String city;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}
