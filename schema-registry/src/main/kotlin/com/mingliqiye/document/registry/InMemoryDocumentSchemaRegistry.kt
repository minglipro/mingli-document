package com.mingliqiye.document.registry

import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.registry.exception.SchemaCompatibilityException
import com.mingliqiye.document.registry.exception.SchemaNotFoundException
import com.mingliqiye.document.registry.exception.SchemaRegistryException
import com.mingliqiye.document.schema.DocumentSchema
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 线程安全的内存 [DocumentSchemaRegistry]。
 *
 * - 版本、id 分配与兼容性检查在同一把锁内完成，并发注册不会产生重复版本号或 id；
 * - 读操作返回不可变快照，可安全跨线程共享；
 * - 默认兼容性策略为 [SchemaCompatibilityPolicy.BACKWARD]（与 Confluent JSON Schema
 *   registry 默认值一致），可通过构造参数替换为任意 [SchemaCompatibilityChecker]；
 * - 相同 Schema 内容在不同 subject 下共享同一个全局 id。
 *
 * ```kotlin
 * val registry = InMemoryDocumentSchemaRegistry()
 * val registered = registry.register(SchemaSubject.of("user"), schema)
 * registry.latest(SchemaSubject.of("user")) // 最新版本
 * ```
 *
 * @param compatibilityPolicy 新版本兼容性检查钩子
 */
public class InMemoryDocumentSchemaRegistry(
    private val compatibilityPolicy: SchemaCompatibilityChecker = SchemaCompatibilityPolicy.BACKWARD,
) : DocumentSchemaRegistry {

    private val lock = ReentrantLock()
    private val versionsBySubject = LinkedHashMap<SchemaSubject, MutableList<RegisteredSchema>>()
    private val schemasById = LinkedHashMap<SchemaId, RegisteredSchema>()
    private val idsByContent = HashMap<ObjectNode, SchemaId>()
    private var nextId = 1L

    override fun register(
        subject: SchemaSubject,
        schema: DocumentSchema,
        metadata: Map<String, String>,
    ): RegisteredSchema {
        val candidate = DocumentSchemaCodec.encode(schema)
        val immutableMetadata = Collections.unmodifiableMap(LinkedHashMap(metadata))
        return lock.withLock {
            val versions = versionsBySubject[subject]
            val latest = versions?.lastOrNull()
            if (latest != null && latest.schema == candidate && latest.metadata == immutableMetadata) {
                return@withLock latest
            }
            if (latest != null) {
                val previous = latest.schema.asObjectOrNull()
                    ?: throw SchemaRegistryException(
                        "Latest schema of subject '${subject.value}' is not a JSON object node",
                    )
                val result = compatibilityPolicy.check(previous, candidate)
                if (!result.compatible) {
                    throw SchemaCompatibilityException(
                        "Schema is not compatible with subject '${subject.value}' " +
                            "version ${latest.version.value}: ${result.violations.joinToString("; ")}",
                    )
                }
            }
            val id = idsByContent.getOrPut(candidate) { allocateId() }
            val version = SchemaVersion((versions?.size ?: 0) + 1)
            val registered = RegisteredSchema(subject, id, version, candidate, SchemaFormat.JSON, immutableMetadata)
            if (versions == null) {
                versionsBySubject[subject] = arrayListOf(registered)
            } else {
                versions.add(registered)
            }
            schemasById.putIfAbsent(id, registered)
            registered
        }
    }

    override fun latest(subject: SchemaSubject): RegisteredSchema? = lock.withLock {
        versionsBySubject[subject]?.lastOrNull()
    }

    override fun byId(id: SchemaId): RegisteredSchema? = lock.withLock {
        schemasById[id]
    }

    override fun versions(subject: SchemaSubject): List<SchemaVersion> = lock.withLock {
        val versions = versionsBySubject[subject] ?: return@withLock emptyList()
        versions.map { it.version }
    }

    override fun deleteSubject(subject: SchemaSubject): List<SchemaVersion> = lock.withLock {
        val versions = versionsBySubject.remove(subject)
            ?: throw SchemaNotFoundException("Subject '${subject.value}' not found")
        versions.map { it.version }
    }

    private fun allocateId(): SchemaId {
        val id = SchemaId(nextId)
        nextId += 1
        return id
    }
}
