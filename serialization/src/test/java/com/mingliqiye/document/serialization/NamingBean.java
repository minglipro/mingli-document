package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentName;

import java.util.List;

/**
 * Test model for property naming strategies.
 *
 * <p>Contains standard Bean properties (getter/setter), public fields and an explicit
 * {@link DocumentName} annotation, so tests can verify that only unannotated fields
 * are renamed.
 */
public class NamingBean {

    private String firstName;
    private String lastName;
    private int userAge;
    private NamingChild homeAddress;
    private List<NamingChild> previousAddresses;
    private String getterNamed;

    @DocumentName("explicit_name")
    private String displayName;

    public String public_field;

    public String URLValue;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public int getUserAge() {
        return userAge;
    }

    public void setUserAge(int userAge) {
        this.userAge = userAge;
    }

    public NamingChild getHomeAddress() {
        return homeAddress;
    }

    public void setHomeAddress(NamingChild homeAddress) {
        this.homeAddress = homeAddress;
    }

    public List<NamingChild> getPreviousAddresses() {
        return previousAddresses;
    }

    public void setPreviousAddresses(List<NamingChild> previousAddresses) {
        this.previousAddresses = previousAddresses;
    }

    @DocumentName("getter_explicit")
    public String getGetterNamed() {
        return getterNamed;
    }

    public void setGetterNamed(String getterNamed) {
        this.getterNamed = getterNamed;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
