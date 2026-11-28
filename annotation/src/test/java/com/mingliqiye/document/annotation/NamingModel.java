package com.mingliqiye.document.annotation;

/**
 * Test model for {@link DocumentNaming} and {@link DocumentInclude} annotations.
 */
@DocumentNaming(DocumentNamingStrategy.SnakeCaseStrategy)
public class NamingModel {

    @DocumentNaming(DocumentNamingStrategy.KebabCaseStrategy)
    private String userName;

    @DocumentInclude(DocumentIncludePolicy.NON_EMPTY)
    private String nickname;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
