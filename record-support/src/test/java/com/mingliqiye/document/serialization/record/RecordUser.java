package com.mingliqiye.document.serialization.record;

import com.mingliqiye.document.annotation.DocumentIgnore;
import com.mingliqiye.document.annotation.DocumentName;
import java.util.List;
import java.util.Map;

/**
 * 测试用 record：涵盖 {@code @DocumentName}、{@code @DocumentIgnore}、嵌套 record、
 * 泛型集合与 Map、可空组件与基本类型组件。
 */
public record RecordUser(
        @DocumentName("display_name") String name,
        int age,
        RecordAddress address,
        List<String> tags,
        Map<String, Integer> scores,
        @DocumentIgnore String internalTag,
        String nickname) {
}
