package com.mingliqiye.document.validation

import com.mingliqiye.document.serialization.DocumentBeanValidator
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import jakarta.validation.ValidationException
import jakarta.validation.Validator

/**
 * 基于 Jakarta Bean Validation 的 [DocumentBeanValidator] 实现。
 *
 * 把 Bean Validation 的约束违规转换成带字段路径与约束消息的
 * [DocumentSerializationException]，交由
 * `com.mingliqiye.document.serialization.DocumentMapper` 在根对象
 * 序列化入口/反序列化出口统一抛出：
 *
 * ```kotlin
 * val config = DocumentMapperConfig.builder()
 *     .beanValidator(JakartaDocumentBeanValidator.create())
 *     .build()
 * val mapper = JsonMapper(config)
 * mapper.format(user) // 违反 @NotNull/@Size/@Pattern 等约束时抛 DocumentSerializationException
 * ```
 *
 * 校验使用 [Validator] 的默认分组；需要自定义分组时可直接使用底层
 * `jakarta.validation.Validator`，再用 [toDocumentSerializationException] 转换结果。
 *
 * 嵌套 Bean 的级联校验由 Bean Validation 的 `@Valid` 语义决定；Mapper 只对根对象
 * 调用一次本校验器，不会对每个嵌套对象重复调用。
 *
 * @param validator 底层 Jakarta Bean Validation 校验器
 */
public class JakartaDocumentBeanValidator(
    private val validator: Validator,
) : DocumentBeanValidator {

    /**
     * 校验根对象。
     *
     * @param value 非 `null` 根值
     * @throws DocumentSerializationException 存在约束违规时抛出，消息包含字段路径与约束消息
     */
    override fun validate(value: Any) {
        val violations = try {
            validator.validate(value)
        } catch (e: ConstraintViolationException) {
            throw toDocumentSerializationException(e)
        }
        if (violations.isNotEmpty()) {
            throw toDocumentSerializationException(violations, value)
        }
    }

    public companion object {

        /**
         * 使用默认 Jakarta Bean Validation Provider（如 hibernate-validator）创建校验器。
         *
         * Provider 通过 `ServiceLoader` 查找，运行期需要 classpath 中存在实现
         * （例如 `org.hibernate.validator:hibernate-validator`，消息插值还需要
         * `org.glassfish:jakarta.el` 等 EL 实现）。
         *
         * 每次调用都会创建独立的默认 [jakarta.validation.ValidatorFactory]；工厂由返回的
         * 校验器持有。建议应用启动时创建一次并复用校验器实例，而不是每次序列化时重新创建。
         *
         * @return 使用默认 Provider 的校验器
         * @throws DocumentSerializationException classpath 中找不到 Jakarta Bean Validation
         * Provider 时抛出，cause 保留原始 [ValidationException]
         */
        @JvmStatic
        public fun create(): JakartaDocumentBeanValidator {
            val factory = try {
                Validation.buildDefaultValidatorFactory()
            } catch (e: ValidationException) {
                throw DocumentSerializationException(
                    "Cannot create Jakarta Bean Validation validator: no provider found on the classpath; " +
                        "add a Jakarta Bean Validation provider such as hibernate-validator",
                    e,
                )
            }
            return JakartaDocumentBeanValidator(factory.validator)
        }

        /**
         * 把约束违规集合格式化为单行消息。
         *
         * 每条违规按 `字段路径: 约束消息` 输出（类级别约束没有字段路径时只输出消息），
         * 多条之间用 `; ` 连接并按文本排序，保证消息稳定。
         *
         * @param violations 约束违规集合
         * @param value 被校验对象；提供时消息包含对象类型名
         * @return 明确的消息文本
         */
        @JvmStatic
        public fun message(
            violations: Collection<ConstraintViolation<*>>,
            value: Any? = null,
        ): String {
            val details = violations.map { violation ->
                val path = violation.propertyPath.toString()
                if (path.isEmpty()) violation.message else "$path: ${violation.message}"
            }.sorted().joinToString("; ")
            val typeName = value?.javaClass?.name
            return if (typeName == null) {
                "Bean validation failed: $details"
            } else {
                "Bean validation failed for $typeName: $details"
            }
        }

        /**
         * 把约束违规集合转换为 [DocumentSerializationException]。
         *
         * @param violations 约束违规集合
         * @param value 被校验对象；提供时消息包含对象类型名
         * @return 带明确消息的异常
         */
        @JvmStatic
        public fun toDocumentSerializationException(
            violations: Collection<ConstraintViolation<*>>,
            value: Any? = null,
        ): DocumentSerializationException = DocumentSerializationException(message(violations, value))

        /**
         * 把 [ConstraintViolationException] 转换为 [DocumentSerializationException]。
         *
         * 消息按 [message] 的格式重建，原始异常通过 cause 保留。
         *
         * @param failure Bean Validation 抛出的约束违规异常
         * @return 带明确消息的异常；cause 为 [failure]
         */
        @JvmStatic
        public fun toDocumentSerializationException(
            failure: ConstraintViolationException,
        ): DocumentSerializationException = DocumentSerializationException(
            message(failure.constraintViolations),
            failure,
        )
    }
}
