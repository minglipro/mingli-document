package com.mingliqiye.document.format.json.schema

import com.mingliqiye.document.core.ArrayNode
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NodeType
import com.mingliqiye.document.core.NumberNode
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.json.JsonReader
import com.mingliqiye.document.format.json.exception.JsonSchemaException
import java.math.BigDecimal
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * 轻量 JSON Schema 校验器。
 *
 * 支持常用 draft-07 子集：
 *
 * - `type`、`enum`、`const`
 * - `properties`、`required`、`additionalProperties`、`patternProperties`
 * - `items`、`minItems`、`maxItems`、`uniqueItems`
 * - `minimum`、`maximum`、`exclusiveMinimum`、`exclusiveMaximum`、`multipleOf`
 * - `minLength`、`maxLength`、`pattern`
 * - `allOf`、`anyOf`、`oneOf`、`not`
 * - 本地 `$ref`（`#/...`）
 *
 * 不支持远程 `$ref`、`format`、`if/then/else`、`dependentSchemas` 等高级关键字。
 */
public class JsonSchema private constructor(
    private val root: Node,
) {

    /**
     * 校验文档树。
     *
     * @param value 待校验文档树
     * @return 校验错误；为空表示通过
     */
    public fun validate(value: Node): List<JsonSchemaValidationError> {
        val errors = mutableListOf<JsonSchemaValidationError>()
        validateNode(root, value, "", errors, 0)
        return errors
    }

    /**
     * 校验并在失败时抛出异常。
     *
     * @param value 待校验文档树
     * @return 原文档树
     * @throws JsonSchemaValidationException 校验失败时抛出
     */
    public fun validateOrThrow(value: Node): Node {
        val errors = validate(value)
        if (errors.isNotEmpty()) {
            throw JsonSchemaValidationException(errors)
        }
        return value
    }

    /**
     * 校验是否通过。
     *
     * @param value 待校验文档树
     * @return 是否通过
     */
    public fun isValid(value: Node): Boolean = validate(value).isEmpty()

    public companion object {

        /**
         * 编译 JSON Schema 文本。
         *
         * @param source Schema 文本
         * @return Schema 实例
         * @throws com.mingliqiye.document.format.json.exception.JsonParseException JSON 非法时抛出
         */
        @JvmStatic
        public fun compile(source: String): JsonSchema = JsonSchema(JsonReader.parse(source))

        /**
         * 使用已有文档树作为 Schema。
         *
         * @param schema Schema 文档树
         * @return Schema 实例
         */
        @JvmStatic
        public fun of(schema: Node): JsonSchema = JsonSchema(schema)
    }

    private fun validateNode(
        schema: Node,
        value: Node,
        path: String,
        errors: MutableList<JsonSchemaValidationError>,
        depth: Int,
    ) {
        if (depth > MAX_SCHEMA_DEPTH) {
            throw JsonSchemaException("JSON Schema nesting exceeds $MAX_SCHEMA_DEPTH")
        }
        if (schema.type == NodeType.BOOLEAN) {
            if (!schema.asBoolean()) {
                errors += JsonSchemaValidationError(path, "must not match the false schema")
            }
            return
        }
        val objectSchema = schema.objectOrNull()
            ?: throw JsonSchemaException("JSON Schema must be an object or boolean")
        objectSchema["${'$'}ref"]?.stringOrNull()?.let { ref ->
            validateNode(resolveRef(ref), value, path, errors, depth + 1)
            return
        }

        objectSchema["type"]?.let { typeSchema ->
            if (!matchesType(typeSchema, value)) {
                errors += JsonSchemaValidationError(path, "must be ${typeDescription(typeSchema)}")
                return
            }
        }

        objectSchema["enum"]?.let { enumSchema ->
            val options = enumSchema.asArray().asList()
            if (options.none { it == value }) {
                errors += JsonSchemaValidationError(path, "must be one of the enum values")
            }
        }

        objectSchema["const"]?.let { constSchema ->
            if (constSchema != value) {
                errors += JsonSchemaValidationError(path, "must equal the const value")
            }
        }

        if (value.type == NodeType.NUMBER) {
            validateNumber(objectSchema, value.asNumber(), path, errors)
        }
        if (value.type == NodeType.STRING) {
            validateString(objectSchema, value.asString(), path, errors)
        }
        if (value.type == NodeType.ARRAY) {
            validateArray(objectSchema, value.asArray(), path, errors, depth)
        }
        if (value.type == NodeType.OBJECT) {
            validateObject(objectSchema, value.asObject(), path, errors, depth)
        }

        objectSchema["allOf"]?.asArray()?.asList()?.forEach { subschema ->
            validateNode(subschema, value, path, errors, depth + 1)
        }
        objectSchema["anyOf"]?.asArray()?.asList()?.let { candidates ->
            if (candidates.none { branch -> branchErrors(branch, value, path, depth).isEmpty() }) {
                errors += JsonSchemaValidationError(path, "must match at least one anyOf schema")
            }
        }
        objectSchema["oneOf"]?.asArray()?.asList()?.let { candidates ->
            val matches = candidates.count { branch -> branchErrors(branch, value, path, depth).isEmpty() }
            if (matches != 1) {
                errors += JsonSchemaValidationError(path, "must match exactly one oneOf schema, but matched $matches")
            }
        }
        objectSchema["not"]?.let { subschema ->
            if (branchErrors(subschema, value, path, depth).isEmpty()) {
                errors += JsonSchemaValidationError(path, "must not match the not schema")
            }
        }
    }

    private fun validateNumber(
        schema: ObjectNode,
        number: NumberNode,
        path: String,
        errors: MutableList<JsonSchemaValidationError>,
    ) {
        val value = number.value
        val minimum = schema["minimum"]?.numberOrNull()?.value
        val maximum = schema["maximum"]?.numberOrNull()?.value
        val exclusiveMinimum = schema["exclusiveMinimum"]
        val exclusiveMaximum = schema["exclusiveMaximum"]
        if (minimum != null) {
            if (exclusiveMinimum?.booleanOrNull() == true) {
                if (value <= minimum) errors += JsonSchemaValidationError(path, "must be > $minimum")
            } else if (value < minimum) {
                errors += JsonSchemaValidationError(path, "must be >= $minimum")
            }
        }
        if (maximum != null) {
            if (exclusiveMaximum?.booleanOrNull() == true) {
                if (value >= maximum) errors += JsonSchemaValidationError(path, "must be < $maximum")
            } else if (value > maximum) {
                errors += JsonSchemaValidationError(path, "must be <= $maximum")
            }
        }
        exclusiveMinimum?.numberOrNull()?.let { bound ->
            if (value <= bound.value) errors += JsonSchemaValidationError(path, "must be > ${bound.value}")
        }
        exclusiveMaximum?.numberOrNull()?.let { bound ->
            if (value >= bound.value) errors += JsonSchemaValidationError(path, "must be < ${bound.value}")
        }
        schema["multipleOf"]?.numberOrNull()?.let { divisor ->
            if (divisor.value.signum() == 0) {
                throw JsonSchemaException("multipleOf must not be zero")
            }
            if (value.remainder(divisor.value).compareTo(BigDecimal.ZERO) != 0) {
                errors += JsonSchemaValidationError(path, "must be a multiple of ${divisor.value}")
            }
        }
    }

    private fun validateString(
        schema: ObjectNode,
        value: String,
        path: String,
        errors: MutableList<JsonSchemaValidationError>,
    ) {
        val length = value.codePointCount(0, value.length)
        schema["minLength"]?.numberOrNull()?.value?.toInt()?.let { minimum ->
            if (length < minimum) errors += JsonSchemaValidationError(path, "length must be >= $minimum")
        }
        schema["maxLength"]?.numberOrNull()?.value?.toInt()?.let { maximum ->
            if (length > maximum) errors += JsonSchemaValidationError(path, "length must be <= $maximum")
        }
        schema["pattern"]?.stringOrNull()?.let { pattern ->
            if (!compilePattern(pattern).matcher(value).find()) {
                errors += JsonSchemaValidationError(path, "must match pattern $pattern")
            }
        }
    }

    private fun validateArray(
        schema: ObjectNode,
        array: ArrayNode,
        path: String,
        errors: MutableList<JsonSchemaValidationError>,
        depth: Int,
    ) {
        val size = array.size
        schema["minItems"]?.numberOrNull()?.value?.toInt()?.let { minimum ->
            if (size < minimum) errors += JsonSchemaValidationError(path, "must contain at least $minimum items")
        }
        schema["maxItems"]?.numberOrNull()?.value?.toInt()?.let { maximum ->
            if (size > maximum) errors += JsonSchemaValidationError(path, "must contain at most $maximum items")
        }
        if (schema["uniqueItems"]?.booleanOrNull() == true) {
            for (i in 0 until size) {
                for (j in i + 1 until size) {
                    if (array[i] == array[j]) {
                        errors += JsonSchemaValidationError(path, "items must be unique")
                        return
                    }
                }
            }
        }
        val items = schema["items"]
        if (items != null) {
            if (items.type == NodeType.ARRAY) {
                val itemSchemas = items.asArray().asList()
                for (index in 0 until minOf(size, itemSchemas.size)) {
                    validateNode(itemSchemas[index], array[index], pointer(path, index), errors, depth + 1)
                }
            } else {
                for (index in 0 until size) {
                    validateNode(items, array[index], pointer(path, index), errors, depth + 1)
                }
            }
        }
    }

    private fun validateObject(
        schema: ObjectNode,
        value: ObjectNode,
        path: String,
        errors: MutableList<JsonSchemaValidationError>,
        depth: Int,
    ) {
        val properties = schema["properties"]?.asObject()
        val patternProperties = schema["patternProperties"]?.asObject()
        val required = schema["required"]?.asArray()?.asList()
        required?.forEach { requiredName ->
            val name = requiredName.asString()
            if (!value.containsKey(name)) {
                errors += JsonSchemaValidationError(path, "missing required property '$name'")
            }
        }
        schema["minProperties"]?.numberOrNull()?.value?.toInt()?.let { minimum ->
            if (value.size < minimum) errors += JsonSchemaValidationError(path, "must contain at least $minimum properties")
        }
        schema["maxProperties"]?.numberOrNull()?.value?.toInt()?.let { maximum ->
            if (value.size > maximum) errors += JsonSchemaValidationError(path, "must contain at most $maximum properties")
        }
        value.entries().forEach { entry ->
            val propertySchema = properties?.get(entry.key)
            if (propertySchema != null) {
                validateNode(propertySchema, entry.value, pointer(path, entry.key), errors, depth + 1)
                return@forEach
            }
            val patternSchema = patternProperties?.asMap()?.entries?.firstOrNull { (pattern, _) ->
                compilePattern(pattern).matcher(entry.key).find()
            }?.value
            if (patternSchema != null) {
                validateNode(patternSchema, entry.value, pointer(path, entry.key), errors, depth + 1)
                return@forEach
            }
            val additional = schema["additionalProperties"] ?: return@forEach
            if (additional.type == NodeType.BOOLEAN && !additional.asBoolean()) {
                errors += JsonSchemaValidationError(path, "additional property '${entry.key}' is not allowed")
            } else if (additional.type == NodeType.OBJECT) {
                validateNode(additional, entry.value, pointer(path, entry.key), errors, depth + 1)
            }
        }
    }

    private fun resolveRef(ref: String): Node {
        if (!ref.startsWith("#")) {
            throw JsonSchemaException("Only local \$ref is supported: $ref")
        }
        val fragment = ref.removePrefix("#")
        if (fragment.isEmpty()) {
            return root
        }
        if (!fragment.startsWith("/")) {
            throw JsonSchemaException("Invalid JSON Pointer ref: $ref")
        }
        var current = root
        fragment.removePrefix("/").split('/').forEach { rawToken ->
            val token = rawToken.replace("~1", "/").replace("~0", "~")
            current = when (current.type) {
                NodeType.OBJECT -> current.asObject().get(token) ?: throw JsonSchemaException("Cannot resolve \$ref: $ref")
                NodeType.ARRAY -> current.asArray()[token.toIntOrNull() ?: throw JsonSchemaException("Invalid array index in \$ref: $ref")]
                else -> throw JsonSchemaException("Cannot resolve \$ref: $ref")
            }
        }
        return current
    }

    private fun branchErrors(schema: Node, value: Node, path: String, depth: Int): List<JsonSchemaValidationError> {
        val errors = mutableListOf<JsonSchemaValidationError>()
        validateNode(schema, value, path, errors, depth + 1)
        return errors
    }

    private fun matchesType(typeSchema: Node, value: Node): Boolean {
        val requiredTypes = if (typeSchema.type == NodeType.ARRAY) {
            typeSchema.asArray().asList().map { it.asString() }
        } else {
            listOf(typeSchema.asString())
        }
        return requiredTypes.any { type ->
            when (type) {
                "null" -> value.type == NodeType.NULL
                "boolean" -> value.type == NodeType.BOOLEAN
                "object" -> value.type == NodeType.OBJECT
                "array" -> value.type == NodeType.ARRAY
                "string" -> value.type == NodeType.STRING
                "number" -> value.type == NodeType.NUMBER
                "integer" -> value.type == NodeType.NUMBER && value.asNumber().value.stripTrailingZeros().scale() <= 0
                else -> throw JsonSchemaException("Unknown JSON Schema type: $type")
            }
        }
    }

    private fun typeDescription(typeSchema: Node): String = if (typeSchema.type == NodeType.ARRAY) typeSchema.asArray().asList().joinToString(" or ") { it.asString() } else typeSchema.asString()

    private fun compilePattern(pattern: String): Pattern = try {
        Pattern.compile(pattern)
    } catch (e: PatternSyntaxException) {
        throw JsonSchemaException("Invalid JSON Schema pattern: $pattern", e)
    }

    private fun pointer(path: String, token: String): String = "$path/${token.replace("~", "~0").replace("/", "~1")}"

    private fun pointer(path: String, index: Int): String = pointer(path, index.toString())

    private fun Node.objectOrNull(): ObjectNode? = if (type == NodeType.OBJECT) asObject() else null

    private fun Node.stringOrNull(): String? = if (type == NodeType.STRING) asString() else null

    private fun Node.numberOrNull(): NumberNode? = if (type == NodeType.NUMBER) asNumber() else null

    private fun Node.booleanOrNull(): Boolean? = if (type == NodeType.BOOLEAN) asBoolean() else null
}

private const val MAX_SCHEMA_DEPTH = 256
