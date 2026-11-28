package com.mingliqiye.document.registry

import com.mingliqiye.document.registry.exception.SchemaRegistryException

/**
 * Schema 负载格式。
 *
 * 本模块本地注册的 Schema 统一编码为 JSON Schema 节点（[JSON]）；
 * [AVRO]/[PROTOBUF] 只用于透传远程 registry 返回的异构负载，
 * `RegisteredSchema.documentSchema()` 无法把这类负载反解为 `DocumentSchema`。
 *
 * @property id 与 Confluent Schema Registry `schemaType` 对齐的格式名
 */
public enum class SchemaFormat(public val id: String) {
    JSON("JSON"),
    AVRO("AVRO"),
    PROTOBUF("PROTOBUF"),
    ;

    public companion object {

        /**
         * 按格式名查找格式（忽略大小写）。
         *
         * @param id 格式名，例如 `JSON`
         * @throws SchemaRegistryException 格式名未知时抛出
         */
        @JvmStatic
        public fun fromId(id: String): SchemaFormat = values().firstOrNull { it.id.equals(id, ignoreCase = true) }
            ?: throw SchemaRegistryException("Unknown schema format: $id")
    }
}
