package com.mingliqiye.document.registry

import com.mingliqiye.document.core.Node

/**
 * 远程 Schema Registry 通信 SPI。
 *
 * 该接口只负责“传输”，职责与 Confluent Schema Registry 客户端一致：
 * 接收规范化 Schema 负载节点，返回 registry 分配的 id/版本。实现方可以对接
 * Confluent REST、Apicurio 等具体 registry；[RemoteDocumentSchemaRegistry]
 * 会在调用前把 `DocumentSchema` 编码为 JSON Schema 节点。
 *
 * 说明：
 *
 * - 远端返回的负载可能是异构格式（[SchemaFormat.AVRO]/[SchemaFormat.PROTOBUF]），
 *   本接口原样透传到 [RegisteredSchema]；
 * - 兼容性通常由远端 registry 自身配置决定，适配器默认不做本地兼容性检查。
 *
 * ```kotlin
 * class ConfluentSchemaRegistryClient(private val http: HttpClient) : SchemaRegistryClient {
 *     override fun register(subject: SchemaSubject, schema: Node, format: SchemaFormat, metadata: Map<String, String>): RegisteredSchema {
 *         // 通过 HTTP 调用 POST /subjects/{subject}/versions，把 schema 写成 JSON 文本
 *     }
 *     // ...
 * }
 * ```
 */
public interface SchemaRegistryClient {

    /**
     * 在远程 registry 注册 Schema。
     *
     * @param subject 逻辑 Schema 名
     * @param schema 规范化 Schema 负载节点
     * @param format 负载格式
     * @param metadata 附加元数据
     * @return 远程 registry 返回的 Schema 快照
     */
    public fun register(
        subject: SchemaSubject,
        schema: Node,
        format: SchemaFormat,
        metadata: Map<String, String> = emptyMap(),
    ): RegisteredSchema

    /**
     * 查询远程 subject 的最新版本。
     *
     * @param subject 逻辑 Schema 名
     * @return 最新版本；不存在时返回 `null`
     */
    public fun latest(subject: SchemaSubject): RegisteredSchema?

    /**
     * 按远程全局 id 查询 Schema。
     *
     * @param id 全局 id
     * @return Schema；不存在时返回 `null`
     */
    public fun byId(id: SchemaId): RegisteredSchema?

    /**
     * 查询远程 subject 的全部版本号。
     *
     * @param subject 逻辑 Schema 名
     * @return 版本号列表；不存在时返回空列表
     */
    public fun versions(subject: SchemaSubject): List<SchemaVersion>

    /**
     * 删除远程 subject。
     *
     * @param subject 逻辑 Schema 名
     * @return 被删除的版本号
     */
    public fun deleteSubject(subject: SchemaSubject): List<SchemaVersion>
}
