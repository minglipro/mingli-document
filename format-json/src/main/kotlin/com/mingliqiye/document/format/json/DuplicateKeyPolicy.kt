package com.mingliqiye.document.format.json

/**
 * JSON 对象中重复 key 的处理策略。
 *
 * 同一对象内出现两个相同 key 时按此策略处理；树、Token、Bean 三条解析路径保持一致。
 */
public enum class DuplicateKeyPolicy {

    /**
     * 后出现的值覆盖先出现的值。
     *
     * 默认策略，保持历史兼容行为。
     */
    LAST_WINS,

    /**
     * 保留先出现的值，忽略后续重复 key 的值。
     */
    FIRST_WINS,

    /**
     * 遇到重复 key 立即抛出
     * [com.mingliqiye.document.format.json.exception.JsonParseException]。
     */
    FAIL,
}
