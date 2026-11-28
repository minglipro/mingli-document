package com.mingliqiye.document.jpa

import jakarta.persistence.Access
import jakarta.persistence.AccessType
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.Temporal
import jakarta.persistence.TemporalType
import jakarta.persistence.Transient
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID

/** 测试用枚举。 */
internal enum class AccountStatus {
    ACTIVE,
    SUSPENDED,
}

/**
 * 普通实体：字段访问 + `@Id`/`@Column`/枚举/日期时间/transient/UUID 等常用类型。
 */
@Entity
@Table(name = "accounts")
internal class AccountEntity {
    @Id
    var id: Long? = null

    @Column(name = "user_name", nullable = false)
    var userName: String? = null

    var displayName: String? = null

    var age: Int = 0

    var balance: BigDecimal? = null

    var active: Boolean? = null

    @Enumerated(EnumType.STRING)
    var status: AccountStatus? = null

    @Enumerated(EnumType.ORDINAL)
    var statusOrdinal: AccountStatus? = null

    @Temporal(TemporalType.DATE)
    var registeredOn: Date? = null

    @Temporal(TemporalType.TIME)
    var registeredTime: Date? = null

    @Temporal(TemporalType.TIMESTAMP)
    var registeredAt: Date? = null

    var localDate: LocalDate? = null

    var localDateTime: LocalDateTime? = null

    var instant: Instant? = null

    var uuid: UUID? = null

    @Transient
    var cachedToken: String? = null

    @Transient
    val computedToken: String = "computed"

    @Transient
    var cachedTags: MutableList<String> = mutableListOf("a", "b")
}

/**
 * 注解继承：`@MappedSuperclass` 中的列参与子实体映射。
 */
@MappedSuperclass
internal abstract class BaseAuditEntity {
    @Id
    var id: Long? = null

    @Column(name = "created_at")
    var createdAt: Date? = null

    @Transient
    var auditNote: String? = null
}

/** 继承 `@MappedSuperclass` 的实体。 */
@Entity
@Table(name = "audit_logs")
internal class AuditEntity : BaseAuditEntity() {
    @Column(name = "message")
    var message: String? = null
}

/** `@Entity(name)` 自定义实体名。 */
@Entity(name = "custom_account")
internal class NamedEntity {
    @Id
    var id: Long? = null
}

/** 嵌入对象。 */
@Embeddable
internal class Address {
    @Column(name = "city")
    var city: String? = null

    @Column(name = "zip_code")
    var zipCode: String? = null
}

/** 使用 `@Embedded` 的实体。 */
@Entity
internal class CustomerEntity {
    @Id
    var id: Long? = null

    @Embedded
    var address: Address? = null
}

/** 复合主键嵌入类。 */
@Embeddable
internal class OrderKey {
    @Column(name = "order_no")
    var orderNo: String? = null

    var region: String? = null
}

/** 使用 `@EmbeddedId` 的实体。 */
@Entity
internal class OrderEntity {
    @EmbeddedId
    var key: OrderKey? = null

    var total: BigDecimal? = null
}

/** 部门与员工双向关联（`@OneToMany` / `@ManyToOne` / `@OneToOne`）。 */
@Entity
internal class DepartmentEntity {
    @Id
    var id: Long? = null

    var name: String? = null

    @OneToMany(mappedBy = "department")
    var employees: MutableList<EmployeeEntity> = mutableListOf()
}

/** 员工实体。 */
@Entity
internal class EmployeeEntity {
    @Id
    var id: Long? = null

    var name: String? = null

    @ManyToOne
    @JoinColumn(name = "department_id")
    var department: DepartmentEntity? = null

    @OneToOne
    var mentor: EmployeeEntity? = null
}

/** 自引用实体：测试循环引用。 */
@Entity
internal class TreeNodeEntity {
    @Id
    var id: Long? = null

    var name: String? = null

    @ManyToOne
    var parent: TreeNodeEntity? = null
}

/** 关联可选性：`optional = false` 读到 null 应当报错。 */
@Entity
internal class RequiredMentorEntity {
    @Id
    var id: Long? = null

    @OneToOne(optional = false)
    var mentor: EmployeeEntity? = null
}

/** `@ElementCollection` 标量元素集合。 */
@Entity
internal class TaggedEntity {
    @Id
    var id: Long? = null

    @ElementCollection
    var tags: MutableList<String> = mutableListOf()

    @ElementCollection
    @Enumerated(EnumType.STRING)
    var statuses: MutableList<AccountStatus> = mutableListOf()
}

/** 非关联的普通集合/Map 属性按普通值处理。 */
@Entity
internal class LooseEntity {
    @Id
    var id: Long? = null

    var labels: MutableList<String> = mutableListOf()

    var numbers: MutableList<Long> = mutableListOf()

    var attributes: MutableMap<String, String> = mutableMapOf()

    var counters: MutableMap<String, Int> = mutableMapOf()
}

/** 属性访问策略实体（`@Access(PROPERTY)`，注解放在 getter 上）。 */
@Entity
@Access(AccessType.PROPERTY)
internal class PropertyAccessEntity {
    private var idValue: Long? = null
    private var nameValue: String? = null

    @get:Id
    @get:Column(name = "ID")
    var id: Long?
        get() = idValue
        set(value) {
            idValue = value
        }

    @get:Column(name = "NAME", nullable = false)
    var name: String?
        get() = nameValue
        set(value) {
            nameValue = value
        }

    @get:Transient
    var ignored: String? = null
}

/** 没有类级 `@Access`，通过 getter 上的 `@Id` 自动识别属性访问。 */
@Entity
internal class AutoPropertyEntity {
    private var idValue: Long? = null

    @get:Id
    var id: Long?
        get() = idValue
        set(value) {
            idValue = value
        }
}

/** 无 `@Entity` 注解的普通类。 */
internal class PlainPojo {
    var id: Long? = null
}

/** 只有带参构造器的类：反序列化时应报“缺少无参构造器”。 */
internal class ConstructorOnlyEntity(@Id val id: Long)

/** 不支持的值类型。 */
internal class OpaqueValue

/** 持有不支持值类型的实体。 */
@Entity
internal class OpaqueEntity {
    @Id
    var id: Long? = null

    var opaque: OpaqueValue? = null
}

/** 不支持的 Map 关联：解析元数据时应当抛配置异常。 */
@Entity
internal class BrokenMapAssociationEntity {
    @Id
    var id: Long? = null

    @jakarta.persistence.ManyToMany
    var links: MutableMap<String, EmployeeEntity> = mutableMapOf()
}

/** 两个属性映射到同一列名：解析元数据时应当抛配置异常。 */
@Entity
internal class DuplicateColumnEntity {
    @Id
    var id: Long? = null

    @Column(name = "same")
    var first: String? = null

    @Column(name = "same")
    var second: String? = null
}
