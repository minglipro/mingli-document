package com.mingliqiye.document.registry

import com.mingliqiye.document.core.Node
import com.mingliqiye.document.registry.exception.SchemaSerializationException
import com.mingliqiye.document.schema.DocumentSchema
import java.util.Collections
import java.util.LinkedHashMap

/**
 * registry 中已注册的 Schema 快照。
 *
 * 对象不可变：`schema` 为 registry 保存的规范化负载节点（本地注册时为 JSON Schema 节点，
 * 见 `DocumentSchemaCodec`），`metadata` 为不可变副本。
 *
 * @property subject 所属 subject
 * @property id 全局 id
 * @property version subject 内版本号
 * @property schema 规范化 Schema 负载节点
 * @property type 负载格式
 */
public class RegisteredSchema(
    public val subject: SchemaSubject,
    public val id: SchemaId,
    public val version: SchemaVersion,
    public val schema: Node,
    public val type: SchemaFormat = SchemaFormat.JSON,
    metadata: Map<String, String> = emptyMap(),
) {

    /** 注册时附带的元数据（不可变副本）。 */
    public val metadata: Map<String, String> = Collections.unmodifiableMap(LinkedHashMap(metadata))

    /**
     * 把负载反解为 [DocumentSchema]。
     *
     * 只有 [SchemaFormat.JSON] 负载可反解；反解时缺少 `title` 则回退到 subject 名。
     *
     * @throws SchemaSerializationException 负载格式不是 JSON 或节点结构非法时抛出
     */
    public fun documentSchema(): DocumentSchema {
        if (type != SchemaFormat.JSON) {
            throw SchemaSerializationException(
                "Schema type ${type.id} of ${subject.value}#${version.value} cannot be decoded to DocumentSchema",
            )
        }
        return DocumentSchemaCodec.decode(schema, subject.value)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is RegisteredSchema) {
            return false
        }
        return subject == other.subject &&
            id == other.id &&
            version == other.version &&
            type == other.type &&
            schema == other.schema &&
            metadata == other.metadata
    }

    override fun hashCode(): Int {
        var result = subject.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + version.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + schema.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }

    override fun toString(): String = "RegisteredSchema(subject=${subject.value}, id=${id.value}, version=${version.value}, type=${type.id})"
}
