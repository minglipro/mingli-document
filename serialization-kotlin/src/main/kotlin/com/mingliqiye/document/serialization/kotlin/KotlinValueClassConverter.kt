package com.mingliqiye.document.serialization.kotlin

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.Node
import com.mingliqiye.document.core.NullNode
import com.mingliqiye.document.serialization.DeserializationContext
import com.mingliqiye.document.serialization.DocumentBaseConverter
import com.mingliqiye.document.serialization.SerializationContext
import com.mingliqiye.document.serialization.exception.DocumentSerializationException

/**
 * Kotlin value class 编解码器：文档表示即底层值。
 *
 * 由 [kotlinModule] 按显式传入的 value class 注册到 Mapper。注册后：
 * - 序列化时把 value class 拆箱为底层值再走常规序列化；
 * - 反序列化时先按底层类型反序列化，再装箱成 value class。
 *
 * data class 的直接构造参数（引用底层类型、非空原始类型底层）不需要注册编解码器，
 * 由 [KotlinDataClassInstantiator] 直接按底层 JVM 类型处理；注册后可以额外支持
 * 根对象、泛型集合/Map 元素、数组元素与"可空原始类型底层"的值为类。
 */
internal class KotlinValueClassConverter(
    private val valueClass: KotlinValueClass,
) : DocumentBaseConverter<Any> {

    @Suppress("UNCHECKED_CAST")
    override fun getType(): DocumentTypeReference<Any> = DocumentTypeReference.of(valueClass.javaClass as Class<Any>)

    override fun serialize(value: Any, context: SerializationContext): Node = try {
        context.mapper.toNode(valueClass.unbox(value))
    } catch (e: DocumentSerializationException) {
        throw e
    } catch (e: Exception) {
        throw DocumentSerializationException(
            "Cannot serialize value class ${valueClass.javaClass.name} at ${context.path}",
            e,
        )
    }

    override fun deserialize(node: Node, context: DeserializationContext): Any {
        if (node is NullNode) {
            if (!valueClass.underlyingNullable) {
                throw DocumentSerializationException(
                    "Cannot deserialize null as value class ${valueClass.javaClass.name} " +
                        "with non-null underlying type at ${context.path}",
                )
            }
            return valueClass.box(null)
        }
        val unboxed = try {
            context.mapper.fromNode(node, DocumentTypeReference.of<Any?>(valueClass.underlyingType))
        } catch (e: DocumentSerializationException) {
            throw e
        } catch (e: Exception) {
            throw DocumentSerializationException(
                "Cannot deserialize value class ${valueClass.javaClass.name} from ${node.type} at ${context.path}",
                e,
            )
        }
        return valueClass.box(unboxed)
    }
}
