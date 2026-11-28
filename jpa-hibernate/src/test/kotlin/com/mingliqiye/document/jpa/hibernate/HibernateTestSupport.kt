package com.mingliqiye.document.jpa.hibernate

import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import java.util.concurrent.atomic.AtomicLong

/**
 * 测试用 H2 内存数据库 + Hibernate bootstrap。
 *
 * 整个测试 JVM 共享一个 `SessionFactory`；测试之间通过 [nextId] 分配互不冲突的标识符，
 * 不依赖测试执行顺序。
 */
internal object HibernateTestDb {

    private val idSequence = AtomicLong(10_000L)

    private val entityClasses = listOf(
        HibernateTestDepartment::class.java,
        HibernateTestEmployee::class.java,
        HibernateTestNote::class.java,
        HibernateTestTreeNode::class.java,
        HibernateTestRequiredLink::class.java,
        HibernateTestPayment::class.java,
        HibernateTestContact::class.java,
        HibernateTestTeam::class.java,
        HibernateTestMember::class.java,
        HibernateTestAnimal::class.java,
        HibernateTestCat::class.java,
        HibernateTestDog::class.java,
        HibernateTestConvertedLink::class.java,
        HibernateTestProject::class.java,
        HibernateTestWorkload::class.java,
        HibernateTestCompositeOrder::class.java,
        HibernateTestCompositeOrderLink::class.java,
        HibernateTestIdClassBook::class.java,
        HibernateTestBookLink::class.java,
    )

    val sessionFactory: SessionFactory by lazy {
        val registry = StandardServiceRegistryBuilder()
            .applySetting("jakarta.persistence.jdbc.driver", "org.h2.Driver")
            .applySetting("jakarta.persistence.jdbc.url", "jdbc:h2:mem:jpa_hibernate_test;DB_CLOSE_DELAY=-1")
            .applySetting("jakarta.persistence.jdbc.user", "sa")
            .applySetting("jakarta.persistence.jdbc.password", "")
            .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
            .applySetting("hibernate.hbm2ddl.auto", "create-drop")
            .applySetting("hibernate.show_sql", "false")
            .applySetting("hibernate.format_sql", "false")
            .build()
        val sources = MetadataSources(registry)
        entityClasses.forEach { sources.addAnnotatedClass(it) }
        // 本模块的 Hibernate 运行期测试需要让 SessionFactory 也认识属性转换器：
        // 显式 @Convert 转换器按类注册，autoApply 转换器按实例注册为自动应用。
        val metadataBuilder = sources.metadataBuilder
        metadataBuilder.applyAttributeConverter(HibernateTestMoneyConverter())
        metadataBuilder.applyAttributeConverter(HibernateTestEmailConverter(), true)
        metadataBuilder.applyAttributeConverter(HibernateTestEmployeeRefConverter())
        metadataBuilder.build().buildSessionFactory()
    }

    /** 分配测试内唯一标识符。 */
    fun nextId(): Long = idSequence.incrementAndGet()

    /** 在事务中执行；异常时回滚。 */
    fun <T> inSession(block: (Session) -> T): T = sessionFactory.openSession().use { session ->
        val transaction = session.beginTransaction()
        try {
            val result = block(session)
            transaction.commit()
            result
        } catch (exception: Throwable) {
            if (transaction.isActive) {
                transaction.rollback()
            }
            throw exception
        }
    }

    /** 在无事务的只读 Session 中执行并关闭。 */
    fun <T> inReadSession(block: (Session) -> T): T = sessionFactory.openSession().use(block)

    /** 持久化一个部门 + 两名员工的固定数据。 */
    fun seedOrganization(): OrganizationFixture = inSession { session ->
        val department = HibernateTestDepartment().apply {
            id = nextId()
            name = "engineering"
        }
        val alice = HibernateTestEmployee().apply {
            id = nextId()
            name = "alice"
            status = HibernateTestStatus.ACTIVE
            address = HibernateTestAddress().apply {
                city = "shanghai"
                zipCode = "200000"
            }
            this.department = department
            tags = mutableListOf("core", "backend")
            statuses = mutableListOf(HibernateTestStatus.ACTIVE, HibernateTestStatus.LEAVE)
        }
        val bob = HibernateTestEmployee().apply {
            id = nextId()
            name = "bob"
            status = HibernateTestStatus.LEAVE
            this.department = department
        }
        department.employees = mutableListOf(alice, bob)
        session.persist(department)
        OrganizationFixture(
            departmentId = department.id!!,
            departmentName = department.name!!,
            aliceId = alice.id!!,
            bobId = bob.id!!,
            employeeNames = mapOf(alice.id!! to "alice", bob.id!! to "bob"),
        )
    }
}

/** [HibernateTestDb.seedOrganization] 的返回值。 */
internal class OrganizationFixture(
    val departmentId: Long,
    val departmentName: String,
    val aliceId: Long,
    val bobId: Long,
    val employeeNames: Map<Long, String>,
)
