package com.mingliqiye.document.jpa.hibernate

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

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
