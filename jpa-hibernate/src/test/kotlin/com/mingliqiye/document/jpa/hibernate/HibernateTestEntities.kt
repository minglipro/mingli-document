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
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapKey
import jakarta.persistence.MapKeyColumn
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
