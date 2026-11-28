package com.mingliqiye.document.serialization.record;

import com.mingliqiye.document.annotation.DocumentCreator;
import com.mingliqiye.document.annotation.DocumentName;

/**
 * 测试用 record：显式标注 {@code @DocumentCreator} 的 canonical constructor，
 * 构造参数上的 {@code @DocumentName} 同时用于读写字段名。
 */
public record AnnotatedRecord(String id, int count) {

    @DocumentCreator
    public AnnotatedRecord(
            @DocumentName("record_id") String id,
            @DocumentName("record_count") int count) {
        this.id = id;
        this.count = count;
    }
}
