package com.mingliqiye.document.jpa.hibernate

import jakarta.persistence.AttributeConverter
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinColumns
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapKey
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.MapKeyJoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal

/** 测试用枚举。 */
internal enum class HibernateTestStatus {
    ACTIVE,
    LEAVE,
}

/** 测试用嵌入对象。 */
@Embeddable
internal class HibernateTestAddress {
    @Column(name = "city")
    var city: String? = null

    @Column(name = "zip_code")
    var zipCode: String? = null
}

/**
 * 部门：懒加载 `@OneToMany`。
 *
 * 实体类与属性都声明为 `open`，Hibernate 才能生成 CGLIB/ByteBuddy 代理子类并覆盖
 * 访问器；否则懒加载代理退化为立即加载。
 */
@Entity
@Table(name = "hb_departments")
internal open class HibernateTestDepartment {
    @Id
    open var id: Long? = null

    @Column(name = "dept_name")
    open var name: String? = null

    @OneToMany(mappedBy = "department", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    open var employees: MutableList<HibernateTestEmployee> = mutableListOf()
}

/** 员工：懒加载 `@ManyToOne` 代理、嵌入对象、枚举、懒加载 `@ElementCollection`。 */
@Entity
@Table(name = "hb_employees")
internal open class HibernateTestEmployee {
    @Id
    open var id: Long? = null

    @Column(name = "full_name", nullable = false)
    open var name: String? = null

    @Enumerated(EnumType.STRING)
    open var status: HibernateTestStatus? = null

    @Embedded
    open var address: HibernateTestAddress? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    open var department: HibernateTestDepartment? = null

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "hb_employee_tags", joinColumns = [JoinColumn(name = "employee_id")])
    @Column(name = "tag")
    open var tags: MutableList<String> = mutableListOf()

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "hb_employee_statuses", joinColumns = [JoinColumn(name = "employee_id")])
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    open var statuses: MutableList<HibernateTestStatus> = mutableListOf()
}

/** 无关联的普通实体：用于 detach 图与引用解析测试。 */
@Entity
@Table(name = "hb_notes")
internal open class HibernateTestNote {
    @Id
    open var id: Long? = null

    @Column(name = "note_text", nullable = false)
    open var text: String? = null

    @Enumerated(EnumType.STRING)
    open var status: HibernateTestStatus? = null

    @Embedded
    open var address: HibernateTestAddress? = null
}

/** 自引用实体：用于循环引用测试。 */
@Entity
@Table(name = "hb_tree_nodes")
internal open class HibernateTestTreeNode {
    @Id
    open var id: Long? = null

    @Column(name = "node_name")
    open var name: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    open var parent: HibernateTestTreeNode? = null
}

/** 必填关联实体：用于 fromNode 的 null 校验。 */
@Entity
@Table(name = "hb_required_links")
internal open class HibernateTestRequiredLink {
    @Id
    open var id: Long? = null

    @ManyToOne(optional = false)
    @JoinColumn(name = "target_id")
    open var target: HibernateTestNote? = null
}

// ----------------------------------------------------------------------
// 高级特性测试模型：@Convert / Map 关联 / 多态
// ----------------------------------------------------------------------

/** 自定义值类型：由 [HibernateTestMoneyConverter] 映射为数据库字符串。 */
internal class HibernateTestMoney(
    val amount: BigDecimal,
    val currency: String,
) {
    override fun equals(other: Any?): Boolean = other is HibernateTestMoney &&
        amount.compareTo(other.amount) == 0 &&
        currency == other.currency

    override fun hashCode(): Int = 31 * amount.stripTrailingZeros().hashCode() + currency.hashCode()
}

/** `HibernateTestMoney` <-> `String` 转换器。 */
@Converter
internal class HibernateTestMoneyConverter : AttributeConverter<HibernateTestMoney, String> {
    override fun convertToDatabaseColumn(attribute: HibernateTestMoney?): String? = attribute?.let {
        "${it.amount.toPlainString()} ${it.currency}"
    }

    override fun convertToEntityAttribute(dbData: String?): HibernateTestMoney? = dbData?.let {
        val parts = it.split(' ')
        require(parts.size == 2) { "Invalid money value '$it'" }
        HibernateTestMoney(BigDecimal(parts[0]), parts[1])
    }
}

/** 邮箱值类型：由 autoApply 转换器映射为字符串。 */
internal class HibernateTestEmail(val address: String) {
    override fun equals(other: Any?): Boolean = other is HibernateTestEmail && address == other.address

    override fun hashCode(): Int = address.hashCode()
}

/** autoApply 邮箱转换器（数据库存大写，读回小写）。 */
@Converter(autoApply = true)
internal class HibernateTestEmailConverter : AttributeConverter<HibernateTestEmail, String> {
    override fun convertToDatabaseColumn(attribute: HibernateTestEmail?): String? = attribute?.address?.uppercase()

    override fun convertToEntityAttribute(dbData: String?): HibernateTestEmail? = dbData?.let {
        HibernateTestEmail(it.lowercase())
    }
}

/** 显式 `@Convert` 实体。 */
@Entity
@Table(name = "hb_payments")
internal open class HibernateTestPayment {
    @Id
    open var id: Long? = null

    @Convert(converter = HibernateTestMoneyConverter::class)
    open var amount: HibernateTestMoney? = null
}

/** autoApply 转换器实体。 */
@Entity
@Table(name = "hb_contacts")
internal open class HibernateTestContact {
    @Id
    open var id: Long? = null

    open var email: HibernateTestEmail? = null
}

/** 团队：Map 关联（`@MapKey(name)` 与 `@MapKeyColumn`）。 */
@Entity
@Table(name = "hb_teams")
internal open class HibernateTestTeam {
    @Id
    open var id: Long? = null

    @Column(name = "team_name")
    open var name: String? = null

    @OneToMany(mappedBy = "team", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @MapKey(name = "name")
    open var membersByName: MutableMap<String, HibernateTestMember> = mutableMapOf()

    @ManyToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinTable(
        name = "hb_team_members_by_key",
        joinColumns = [JoinColumn(name = "team_id")],
        inverseJoinColumns = [JoinColumn(name = "member_id")],
    )
    @MapKeyColumn(name = "member_key")
    open var membersByKey: MutableMap<Long, HibernateTestMember> = mutableMapOf()
}

/** 团队成员。 */
@Entity
@Table(name = "hb_members")
internal open class HibernateTestMember {
    @Id
    open var id: Long? = null

    @Column(name = "member_name")
    open var name: String? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    open var team: HibernateTestTeam? = null
}

/** SINGLE_TABLE 继承根。 */
@Entity
@Table(name = "hb_animals")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "animal_kind")
internal abstract class HibernateTestAnimal {
    @Id
    open var id: Long? = null

    @Column(name = "animal_name")
    open var name: String? = null
}

/** 继承子类：猫。 */
@Entity
@DiscriminatorValue("cat")
internal open class HibernateTestCat : HibernateTestAnimal() {
    open var lives: Int = 9
}

/** 继承子类：狗。 */
@Entity
@DiscriminatorValue("dog")
internal open class HibernateTestDog : HibernateTestAnimal() {
    @Column(name = "bark_volume")
    open var barkVolume: Int = 0
}

// ----------------------------------------------------------------------
// 剩余映射细节测试模型：关系 @Convert / 实体 Map 键 / 复合主键引用
// ----------------------------------------------------------------------

/** 关系属性上的显式 @Convert：实体关联 -> 数据库标识符（Long）。 */
@Converter
internal class HibernateTestEmployeeRefConverter : AttributeConverter<HibernateTestEmployee, Long> {
    override fun convertToDatabaseColumn(attribute: HibernateTestEmployee?): Long? = attribute?.id

    override fun convertToEntityAttribute(dbData: Long?): HibernateTestEmployee? = dbData?.let {
        HibernateTestEmployee().apply { id = it }
    }
}

/** 关系属性 @Convert 实体。 */
@Entity
@Table(name = "hb_converted_links")
internal open class HibernateTestConvertedLink {
    @Id
    open var id: Long? = null

    @ManyToOne
    @JoinColumn(name = "employee_id")
    @Convert(converter = HibernateTestEmployeeRefConverter::class)
    open var employee: HibernateTestEmployee? = null
}

/** 项目实体：作为实体 Map 键 Map 的值。 */
@Entity
@Table(name = "hb_projects")
internal open class HibernateTestProject {
    @Id
    open var id: Long? = null

    @Column(name = "project_name")
    open var name: String? = null
}

/** Map 键为实体引用（`@MapKeyJoinColumn`）。 */
@Entity
@Table(name = "hb_workloads")
internal open class HibernateTestWorkload {
    @Id
    open var id: Long? = null

    @ManyToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    @JoinTable(
        name = "hb_workload_projects",
        joinColumns = [JoinColumn(name = "workload_id")],
        inverseJoinColumns = [JoinColumn(name = "project_id")],
    )
    @MapKeyJoinColumn(name = "member_id")
    open var projectByMember: MutableMap<HibernateTestMember, HibernateTestProject> = mutableMapOf()
}

/** `@EmbeddedId` 复合主键。 */
@Embeddable
internal open class HibernateTestOrderKey {
    @Column(name = "order_no")
    open var orderNo: String? = null

    @Column(name = "region_code")
    open var region: String? = null
}

/** `@EmbeddedId` 复合主键实体。 */
@Entity
@Table(name = "hb_composite_orders")
internal open class HibernateTestCompositeOrder {
    @EmbeddedId
    open var key: HibernateTestOrderKey? = null

    @Column(name = "order_amount")
    open var amount: String? = null
}

/** 指向 `@EmbeddedId` 实体的关联。 */
@Entity
@Table(name = "hb_composite_order_links")
internal open class HibernateTestCompositeOrderLink {
    @Id
    open var id: Long? = null

    @ManyToOne
    @JoinColumns(
        value = [
            JoinColumn(name = "order_no", referencedColumnName = "order_no"),
            JoinColumn(name = "region_code", referencedColumnName = "region_code"),
        ],
    )
    open var order: HibernateTestCompositeOrder? = null
}

/** `@IdClass` 复合主键类（实现 equals/hashCode 以支持 Hibernate 一级缓存查找）。 */
internal class HibernateTestBookKey {
    var shelf: Int? = null
    var slot: Int? = null

    override fun equals(other: Any?): Boolean = other is HibernateTestBookKey && shelf == other.shelf && slot == other.slot

    override fun hashCode(): Int = 31 * (shelf ?: 0) + (slot ?: 0)
}

/** `@IdClass` 复合主键实体。 */
@Entity
@Table(name = "hb_books")
@IdClass(HibernateTestBookKey::class)
internal open class HibernateTestIdClassBook {
    @Id
    open var shelf: Int? = null

    @Id
    open var slot: Int? = null

    @Column(name = "book_title")
    open var title: String? = null
}

/** 指向 `@IdClass` 实体的关联。 */
@Entity
@Table(name = "hb_book_links")
internal open class HibernateTestBookLink {
    @Id
    open var id: Long? = null

    @ManyToOne
    @JoinColumns(
        value = [
            JoinColumn(name = "shelf", referencedColumnName = "shelf"),
            JoinColumn(name = "slot", referencedColumnName = "slot"),
        ],
    )
    open var book: HibernateTestIdClassBook? = null
}
