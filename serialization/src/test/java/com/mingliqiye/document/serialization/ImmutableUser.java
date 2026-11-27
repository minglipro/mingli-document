package com.mingliqiye.document.serialization;

import com.mingliqiye.document.annotation.DocumentCreator;
import com.mingliqiye.document.annotation.DocumentName;
import java.util.List;
import java.util.Map;

/**
 * 不可变 Bean 测试模型。
 *
 * 覆盖构造器注入的泛型集合、Map、嵌套不可变 Bean、可空引用与基本类型；
 * 构造参数名与属性名一致时，参数上的 {@link DocumentName} 同时用于序列化写出。
 */
public final class ImmutableUser {

    private final String name;

    private final int age;

    private final ImmutableAddress address;

    private final List<String> tags;

    private final Map<String, Integer> scores;

    private final String nickname;

    private final List<ImmutableAddress> formerAddresses;

    @DocumentCreator
    public ImmutableUser(
            @DocumentName("display_name") String name,
            @DocumentName("age") int age,
            @DocumentName("address") ImmutableAddress address,
            @DocumentName("tags") List<String> tags,
            @DocumentName("scores") Map<String, Integer> scores,
            @DocumentName("nickname") String nickname,
            @DocumentName("former_addresses") List<ImmutableAddress> formerAddresses) {
        this.name = name;
        this.age = age;
        this.address = address;
        this.tags = tags;
        this.scores = scores;
        this.nickname = nickname;
        this.formerAddresses = formerAddresses;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public ImmutableAddress getAddress() {
        return address;
    }

    public List<String> getTags() {
        return tags;
    }

    public Map<String, Integer> getScores() {
        return scores;
    }

    public String getNickname() {
        return nickname;
    }

    public List<ImmutableAddress> getFormerAddresses() {
        return formerAddresses;
    }
}
