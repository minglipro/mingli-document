package com.mingliqiye.document.registry

import com.mingliqiye.document.schema.DocumentSchema

/**
 * 基于 [SchemaRegistryClient] 的远程 [DocumentSchemaRegistry] 适配器。
 *
 * 适配器负责把 [DocumentSchema] 编码为 JSON Schema 节点（[SchemaFormat.JSON]）
 * 后交给远程客户端；查询方法原样透传远程结果。兼容性检查由远端 registry
 * 负责（远端返回的错误可由具体客户端包装为
 * [com.mingliqiye.document.registry.exception.SchemaRegistryException]）。
 *
 * 需要本地缓存、重试、兼容性预检等能力时，继承本类并覆盖对应方法：
 *
 * ```kotlin
 * class CachingSchemaRegistry(client: SchemaRegistryClient) : RemoteDocumentSchemaRegistry(client) {
 *     override fun byId(id: SchemaId): RegisteredSchema? = cache[id] ?: super.byId(id)?.also { cache[id] = it }
 * }
 * ```
 *
 * @param client 远程通信客户端
 */
public open class RemoteDocumentSchemaRegistry(
    protected val client: SchemaRegistryClient,
) : DocumentSchemaRegistry {

    override fun register(
        subject: SchemaSubject,
        schema: DocumentSchema,
        metadata: Map<String, String>,
    ): RegisteredSchema = client.register(subject, DocumentSchemaCodec.encode(schema), SchemaFormat.JSON, metadata)

    override fun latest(subject: SchemaSubject): RegisteredSchema? = client.latest(subject)

    override fun byId(id: SchemaId): RegisteredSchema? = client.byId(id)

    override fun versions(subject: SchemaSubject): List<SchemaVersion> = client.versions(subject)

    override fun deleteSubject(subject: SchemaSubject): List<SchemaVersion> = client.deleteSubject(subject)
}
