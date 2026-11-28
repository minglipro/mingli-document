package com.mingliqiye.document.registry

import com.mingliqiye.document.schema.DocumentSchema

/**
 * `DocumentSchema` 注册表。
 *
 * 语义：
 *
 * - `subject` 是版本演进单位，版本号从 `1` 开始按 subject 自增；
 * - `id` 是全局 id，相同 Schema 内容（规范化节点相等）复用同一个 id；
 * - 重复注册与最新版本完全相同的 Schema（内容与元数据都相同）是幂等的，
 *   返回已有记录，不产生新版本；
 * - 查询方法对未知 subject/id 返回 `null`/空列表，[deleteSubject] 对未知 subject
 *   抛 [com.mingliqiye.document.registry.exception.SchemaNotFoundException]。
 *
 * 本地实现见 [InMemoryDocumentSchemaRegistry]，远程适配见 [RemoteDocumentSchemaRegistry]。
 */
public interface DocumentSchemaRegistry {

    /**
     * 注册 Schema。
     *
     * 本地注册会把 [DocumentSchema] 编码为 JSON Schema 节点，并按当前兼容性策略
     * 与 subject 最新版本比较。
     *
     * @param subject 逻辑 Schema 名
     * @param schema 待注册 Schema
     * @param metadata 附加元数据
     * @return 注册后的 Schema 快照
     * @throws com.mingliqiye.document.registry.exception.SchemaCompatibilityException
     * 与最新版本不兼容时抛出
     * @throws com.mingliqiye.document.registry.exception.SchemaSerializationException
     * Schema 无法编码时抛出
     */
    public fun register(
        subject: SchemaSubject,
        schema: DocumentSchema,
        metadata: Map<String, String> = emptyMap(),
    ): RegisteredSchema

    /**
     * 查询 subject 的最新版本。
     *
     * @param subject 逻辑 Schema 名
     * @return 最新版本；subject 不存在时返回 `null`
     */
    public fun latest(subject: SchemaSubject): RegisteredSchema?

    /**
     * 按全局 id 查询 Schema。
     *
     * @param id 全局 id
     * @return Schema；id 不存在时返回 `null`
     */
    public fun byId(id: SchemaId): RegisteredSchema?

    /**
     * 查询 subject 的全部版本号，按注册顺序升序。
     *
     * @param subject 逻辑 Schema 名
     * @return 版本号列表；subject 不存在时返回空列表
     */
    public fun versions(subject: SchemaSubject): List<SchemaVersion>

    /**
     * 删除 subject 及其全部版本。
     *
     * 已分配的全局 id 不会被回收（软删除语义：`byId` 仍可查到被删除版本的 Schema）。
     *
     * @param subject 逻辑 Schema 名
     * @return 被删除的版本号，按注册顺序升序
     * @throws com.mingliqiye.document.registry.exception.SchemaNotFoundException subject 不存在时抛出
     */
    public fun deleteSubject(subject: SchemaSubject): List<SchemaVersion>
}
