package com.mingliqiye.document.registry

import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.SchemaField

/**
 * 内置 Schema 兼容性策略。
 *
 * 兼容性按“数据读写方向”定义（与 Avro/Confluent 的 BACKWARD/FORWARD 语义一致）：
 *
 * - `BACKWARD`：用新 Schema 读取旧数据。允许删除字段、新增可选字段；
 * - `FORWARD`：用旧 Schema 读取新数据。允许新增字段、删除可选字段；
 * - `FULL`：同时满足 `BACKWARD` 与 `FORWARD`；
 * - `NONE`：不做检查，任何变更都允许。
 *
 * 本实现基于 `DocumentSchema` 的字段模型判断，规则如下：
 *
 * | 变更 | BACKWARD | FORWARD |
 * | --- | --- | --- |
 * | 新增必填字段 | 不允许 | 允许 |
 * | 新增可选字段 | 允许 | 允许 |
 * | 删除必填字段 | 允许 | 不允许 |
 * | 删除可选字段 | 允许 | 允许 |
 * | 字段类型改变 | 不允许 | 不允许 |
 * | 可选 -> 必填 | 不允许 | 允许 |
 * | 必填 -> 可选 | 允许 | 不允许 |
 * | 非空 -> 可空 | 允许 | 不允许 |
 * | 可空 -> 非空 | 不允许 | 允许 |
 *
 * Schema 名（`title`）变化不影响数据兼容性，因此不参与检查。
 */
public enum class SchemaCompatibilityPolicy : SchemaCompatibilityChecker {

    /** 不做兼容性检查。 */
    NONE {
        override fun check(previous: ObjectNode, candidate: ObjectNode): SchemaCompatibilityResult = SchemaCompatibilityResult.compatible()
    },

    /** 新 Schema 必须能读取旧数据。 */
    BACKWARD {
        override fun check(previous: ObjectNode, candidate: ObjectNode): SchemaCompatibilityResult = evaluate(previous, candidate, checkBackward = true, checkForward = false)
    },

    /** 旧 Schema 必须能读取新数据。 */
    FORWARD {
        override fun check(previous: ObjectNode, candidate: ObjectNode): SchemaCompatibilityResult = evaluate(previous, candidate, checkBackward = false, checkForward = true)
    },

    /** 双向兼容。 */
    FULL {
        override fun check(previous: ObjectNode, candidate: ObjectNode): SchemaCompatibilityResult = evaluate(previous, candidate, checkBackward = true, checkForward = true)
    },
}

private fun evaluate(
    previousNode: ObjectNode,
    candidateNode: ObjectNode,
    checkBackward: Boolean,
    checkForward: Boolean,
): SchemaCompatibilityResult {
    val previous = DocumentSchemaCodec.decode(previousNode, "previous")
    val candidate = DocumentSchemaCodec.decode(candidateNode, "candidate")
    val previousFields = fieldsByName(previous)
    val candidateFields = fieldsByName(candidate)
    val violations = LinkedHashSet<String>()
    for ((name, previousField) in previousFields) {
        val candidateField = candidateFields[name]
        if (candidateField == null) {
            if (checkForward && previousField.required) {
                violations.add("forward: required field '$name' was removed")
            }
            continue
        }
        if (previousField.type != candidateField.type) {
            violations.add("field '$name' changed type from ${previousField.type} to ${candidateField.type}")
            continue
        }
        if (checkBackward && !previousField.required && candidateField.required) {
            violations.add("backward: field '$name' changed from optional to required")
        }
        if (checkForward && previousField.required && !candidateField.required) {
            violations.add("forward: field '$name' changed from required to optional")
        }
        if (checkBackward && previousField.nullable && !candidateField.nullable) {
            violations.add("backward: field '$name' changed from nullable to non-nullable")
        }
        if (checkForward && !previousField.nullable && candidateField.nullable) {
            violations.add("forward: field '$name' changed from non-nullable to nullable")
        }
    }
    for ((name, candidateField) in candidateFields) {
        if (name !in previousFields && checkBackward && candidateField.required) {
            violations.add("backward: required field '$name' was added")
        }
    }
    return if (violations.isEmpty()) {
        SchemaCompatibilityResult.compatible()
    } else {
        SchemaCompatibilityResult.incompatible(violations.toList())
    }
}

private fun fieldsByName(schema: DocumentSchema): Map<String, SchemaField> {
    val fields = LinkedHashMap<String, SchemaField>()
    for (field in schema.fields) {
        fields[field.name] = field
    }
    return fields
}
