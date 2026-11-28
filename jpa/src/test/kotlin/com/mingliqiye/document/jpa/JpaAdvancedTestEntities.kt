package com.mingliqiye.document.jpa

import jakarta.persistence.Access
import jakarta.persistence.AccessType
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapKey
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.MapKeyEnumerated
import jakarta.persistence.MapKeyTemporal
import jakarta.persistence.OneToMany
import jakarta.persistence.TemporalType
import java.math.BigDecimal
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger

// ----------------------------------------------------------------------
// @Convert / AttributeConverter 测试模型
// ----------------------------------------------------------------------

/** 自定义值类型：由 [MoneyConverter] 映射为数据库字符串。 */
internal class Money(
    val amount: BigDecimal,
    val currency: String,
) {
    override fun equals(other: Any?): Boolean = other is Money &&
        amount.compareTo(other.amount) == 0 &&
        currency == other.currency

    override fun hashCode(): Int = 31 * amount.stripTrailingZeros().hashCode() + currency.hashCode()

    override fun toString(): String = "${amount.toPlainString()} $currency"
}

/** `Money` <-> `String` 转换器（转换器必须能处理 null，与 JPA 提供者行为一致）。 */
internal class MoneyConverter : AttributeConverter<Money, String> {
    override fun convertToDatabaseColumn(attribute: Money?): String? = attribute?.let {
        "${it.amount.toPlainString()} ${it.currency}"
    }

    override fun convertToEntityAttribute(dbData: String?): Money? = dbData?.let {
        val parts = it.split(' ')
        require(parts.size == 2) { "Invalid Money value '$it'" }
        Money(BigDecimal(parts[0]), parts[1])
    }
}

/** 另一个 `Money` <-> `String` 转换器（格式不同），用于显式覆盖 autoApply。 */
internal class CompactMoneyConverter : AttributeConverter<Money, String> {
    override fun convertToDatabaseColumn(attribute: Money?): String? = attribute?.let {
        "${it.currency}:${it.amount.toPlainString()}"
    }

    override fun convertToEntityAttribute(dbData: String?): Money? = dbData?.let {
        val separator = it.indexOf(':')
        require(separator > 0) { "Invalid Money value '$it'" }
        Money(BigDecimal(it.substring(separator + 1)), it.substring(0, separator))
    }
}

/** 等级值类型：由 [RatingConverter] 映射为数据库整数。 */
internal class Rating(val stars: Int) {
    override fun equals(other: Any?): Boolean = other is Rating && stars == other.stars

    override fun hashCode(): Int = stars
}

/** `Rating` <-> `Int` 转换器（数据库列为数字类型）。 */
internal class RatingConverter : AttributeConverter<Rating, Int> {
    override fun convertToDatabaseColumn(attribute: Rating?): Int? = attribute?.stars

    override fun convertToEntityAttribute(dbData: Int?): Rating? = dbData?.let { Rating(it) }
}

/** 显式 `@Convert`、`@ElementCollection` 元素转换器与数字数据库类型。 */
@Entity
internal class ConvertedEntity {
    @Id
    var id: Long? = null

    @Convert(converter = MoneyConverter::class)
    var price: Money? = null

    @Column(name = "fee_amount")
    @Convert(converter = MoneyConverter::class)
    var fee: Money? = null

    @Convert(converter = RatingConverter::class)
    var rating: Rating? = null

    @ElementCollection
    @Convert(converter = MoneyConverter::class)
    var charges: MutableList<Money> = mutableListOf()
}

/** 邮箱值类型，用于测试 autoApply converter。 */
internal class Email(val address: String) {
    override fun equals(other: Any?): Boolean = other is Email && address == other.address

    override fun hashCode(): Int = address.hashCode()
}

/** 邮箱 autoApply 转换器（大写化地址）。 */
internal class EmailConverter : AttributeConverter<Email, String> {
    override fun convertToDatabaseColumn(attribute: Email?): String? = attribute?.address?.uppercase()

    override fun convertToEntityAttribute(dbData: String?): Email? = dbData?.let { Email(it.lowercase()) }
}

/** 另一个邮箱转换器，用于显式覆盖/重复 autoApply 校验。 */
internal class RawEmailConverter : AttributeConverter<Email, String> {
    override fun convertToDatabaseColumn(attribute: Email?): String? = attribute?.let { "raw:${it.address}" }

    override fun convertToEntityAttribute(dbData: String?): Email? = dbData?.let { Email(it.removePrefix("raw:")) }
}

/** 枚举 autoApply 转换器。 */
internal class StatusLabelConverter : AttributeConverter<AccountStatus, String> {
    override fun convertToDatabaseColumn(attribute: AccountStatus?): String? = attribute?.let { "status:${it.name}" }

    override fun convertToEntityAttribute(dbData: String?): AccountStatus? = dbData?.let {
        AccountStatus.valueOf(it.removePrefix("status:"))
    }
}

/** autoApply、显式覆盖、disableConversion、@Enumerated/@Temporal 排除规则测试实体。 */
@Entity
internal class AutoAppliedEntity {
    @Id
    var id: Long? = null

    /** 无 @Convert：注册的 autoApply 转换器生效（大写地址）。 */
    var email: Email? = null

    /** 显式 @Convert 覆盖 autoApply 转换器。 */
    @Convert(converter = RawEmailConverter::class)
    var alias: Email? = null

    /** @Convert(disableConversion = true)：不转换，写出时该值类型不受支持。 */
    @Convert(disableConversion = true)
    var raw: Email? = null

    /** 显式 @Enumerated：autoApply 枚举转换器不生效，按 STRING 写出。 */
    @Enumerated(EnumType.STRING)
    var status: AccountStatus? = null

    /** 无 @Enumerated：autoApply 枚举转换器生效。 */
    var convertedStatus: AccountStatus? = null
}

/** 转换失败转换器：值包含 "boom" 时抛异常。 */
internal class BoomConverter : AttributeConverter<String, String> {
    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute != null && attribute.contains("boom")) {
            throw IllegalStateException("boom converter failure")
        }
        return attribute
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData != null && dbData.contains("boom")) {
            throw IllegalStateException("boom converter failure")
        }
        return dbData
    }
}

/** 转换失败测试实体。 */
@Entity
internal class BoomConvertedEntity {
    @Id
    var id: Long? = null

    @Convert(converter = BoomConverter::class)
    var value: String? = null
}

/** 记录 null 调用次数的转换器。 */
internal object NullTrackingConverterState {
    val databaseColumnCalls = AtomicInteger()
    val entityAttributeCalls = AtomicInteger()

    fun reset() {
        databaseColumnCalls.set(0)
        entityAttributeCalls.set(0)
    }
}

/** 统计 null 调用的转换器。 */
internal class TrackingConverter : AttributeConverter<String, String> {
    override fun convertToDatabaseColumn(attribute: String?): String? {
        NullTrackingConverterState.databaseColumnCalls.incrementAndGet()
        return attribute
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        NullTrackingConverterState.entityAttributeCalls.incrementAndGet()
        return dbData
    }
}

/** null 不经过转换器的测试实体。 */
@Entity
internal class NullConvertedEntity {
    @Id
    var id: Long? = null

    @Convert(converter = TrackingConverter::class)
    var value: String? = null
}

/** 返回 null 的转换器：非 null 属性写出为 NullNode。 */
internal class DroppingConverter : AttributeConverter<String, String?> {
    override fun convertToDatabaseColumn(attribute: String?): String? = attribute?.takeIf { it != "drop" }

    override fun convertToEntityAttribute(dbData: String?): String? = dbData
}

/** 转换器输出 null 的测试实体。 */
@Entity
internal class DroppingConvertedEntity {
    @Id
    var id: Long? = null

    @Convert(converter = DroppingConverter::class)
    var value: String? = null
}

/** 不是 AttributeConverter 的类：注册 autoApply 时应报配置异常。 */
internal class NotAConverter

/** 没有无参构造器的转换器：实例化时应报配置异常。 */
internal class NoDefaultConstructorConverter(private val prefix: String) : AttributeConverter<String, String> {
    override fun convertToDatabaseColumn(attribute: String): String = prefix + attribute

    override fun convertToEntityAttribute(dbData: String): String = dbData.removePrefix(prefix)
}

/** 属性访问策略下的转换器实体（注解在 getter 上）。 */
@Entity
@Access(AccessType.PROPERTY)
internal class PropertyConvertedEntity {
    private var idValue: Long? = null
    private var priceValue: Money? = null

    @get:Id
    var id: Long?
        get() = idValue
        set(value) {
            idValue = value
        }

    @get:Convert(converter = MoneyConverter::class)
    var price: Money?
        get() = priceValue
        set(value) {
            priceValue = value
        }
}

// ----------------------------------------------------------------------
// Map 关联 / Map @ElementCollection 测试模型
// ----------------------------------------------------------------------

/** `@OneToMany` Map 关联：键为基本类型列（`@MapKeyColumn`）。 */
@Entity
internal class MapAssociationEntity {
    @Id
    var id: Long? = null

    var name: String? = null

    @OneToMany
    @MapKeyColumn(name = "member_name")
    var membersByName: MutableMap<String, EmployeeEntity> = mutableMapOf()

    @OneToMany
    @MapKeyColumn(name = "member_id")
    var membersById: MutableMap<Long, EmployeeEntity> = mutableMapOf()
}

/** `@ManyToMany` Map 关联：`@MapKey(name)` 使用目标实体属性作为键。 */
@Entity
internal class MapKeyNameEntity {
    @Id
    var id: Long? = null

    @ManyToMany
    @MapKey(name = "name")
    var mentorsByName: MutableMap<String, EmployeeEntity> = mutableMapOf()

    /** 泛型键为 Object + `@MapKey`（默认主键）：按目标实体 @Id 类型延迟解析。 */
    @ManyToMany
    @MapKey
    var byPrimaryKey: MutableMap<Any, EmployeeEntity> = mutableMapOf()
}

/** 自引用 Map 关联：测试深度与循环引用。 */
@Entity
internal class MapNodeEntity {
    @Id
    var id: Long? = null

    var name: String? = null

    @OneToMany
    @MapKeyColumn(name = "child_key")
    var children: MutableMap<String, MapNodeEntity> = mutableMapOf()
}

/** Map `@ElementCollection`：标量值、嵌入对象值、枚举键、日期键。 */
@Entity
internal class MapElementCollectionEntity {
    @Id
    var id: Long? = null

    @ElementCollection
    @MapKeyColumn(name = "attr_key")
    var attributes: MutableMap<String, String> = mutableMapOf()

    @ElementCollection
    var scores: MutableMap<Long, Int> = mutableMapOf()

    @ElementCollection
    var addresses: MutableMap<String, Address> = mutableMapOf()

    @ElementCollection
    @MapKeyEnumerated(EnumType.STRING)
    var statusNotes: MutableMap<AccountStatus, String> = mutableMapOf()

    @ElementCollection
    var statusOrdinals: MutableMap<AccountStatus, Int> = mutableMapOf()

    @ElementCollection
    @MapKeyTemporal(TemporalType.DATE)
    var dayNotes: MutableMap<Date, String> = mutableMapOf()
}

// ----------------------------------------------------------------------
// 继承 / 多态测试模型
// ----------------------------------------------------------------------

/** `SINGLE_TABLE` 继承根：抽象类 + 显式 discriminator 列。 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "animal_kind")
internal abstract class AnimalEntity {
    @Id
    var id: Long? = null

    var name: String? = null

    @ManyToOne
    var parent: AnimalEntity? = null
}

/** 动物子类：猫。 */
@Entity
@DiscriminatorValue("cat")
internal class CatEntity : AnimalEntity() {
    var lives: Int = 9
}

/** 动物子类：狗。 */
@Entity
@DiscriminatorValue("dog")
internal class DogEntity : AnimalEntity() {
    @Column(name = "bark_volume")
    var barkVolume: Int = 0
}

/** 持有声明类型为父类的关联集合：测试关联中的多态。 */
@Entity
internal class ZooEntity {
    @Id
    var id: Long? = null

    var name: String? = null

    @OneToMany
    var animals: MutableList<AnimalEntity> = mutableListOf()
}

/** `JOINED` 继承且未声明 `@DiscriminatorColumn`：使用默认列名 `DTYPE`。 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
internal open class VehicleEntity {
    @Id
    var id: Long? = null

    var brand: String? = null
}

/** 车辆子类，默认 discriminator 值为实体名。 */
@Entity
internal class CarEntity : VehicleEntity() {
    var doors: Int = 4
}

/** INTEGER discriminator 继承根。 */
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@DiscriminatorColumn(name = "ticket_kind", discriminatorType = DiscriminatorType.INTEGER)
internal abstract class TicketEntity {
    @Id
    var id: Long? = null
}

/** INTEGER discriminator 子类。 */
@Entity
@DiscriminatorValue("1")
internal class OnlineTicketEntity : TicketEntity() {
    var email: String? = null
}

/** STRING 默认 discriminator 值（实体名）继承根。 */
@Entity
@Inheritance
@DiscriminatorColumn(name = "event_type")
internal open class BaseEventEntity {
    @Id
    var id: Long? = null

    var label: String? = null
}

/** 没有 `@DiscriminatorValue` 的事件子类：默认值为实体名。 */
@Entity
internal class ClickEventEntity : BaseEventEntity() {
    var x: Int = 0
}

/** 多态自引用：测试继承层次的循环检测。 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "node_type")
internal abstract class PolyNodeEntity {
    @Id
    var id: Long? = null

    @ManyToOne
    var parent: PolyNodeEntity? = null
}

/** 多态叶节点。 */
@Entity
@DiscriminatorValue("leaf")
internal class LeafNodeEntity : PolyNodeEntity()

// ----------------------------------------------------------------------
// 非法配置测试模型
// ----------------------------------------------------------------------

/** 转换器属性类型不匹配：映射时应抛配置异常。 */
@Entity
internal class MismatchedConverterEntity {
    @Id
    var id: Long? = null

    @Convert(converter = MoneyConverter::class)
    var text: String? = null
}

/** 关系属性上使用 @Convert：映射时应抛配置异常。 */
@Entity
internal class BadConvertedAssociationEntity {
    @Id
    var id: Long? = null

    @ManyToOne
    @Convert(converter = MoneyConverter::class)
    var employee: EmployeeEntity? = null
}

/** Map 键类型不是基本类型（嵌入对象）：映射时应抛配置异常。 */
@Entity
internal class BadMapKeyEntity {
    @Id
    var id: Long? = null

    @ManyToMany
    var links: MutableMap<Address, EmployeeEntity> = mutableMapOf()
}

/** 非 Map 属性上加 Map 键注解：映射时应抛配置异常。 */
@Entity
internal class BadMapKeyAnnotationEntity {
    @Id
    var id: Long? = null

    @OneToMany
    @MapKey
    var employees: MutableList<EmployeeEntity> = mutableListOf()
}

/** null 双向映射不对称的转换器：用于验证转换器可以处理 null。 */
internal class NullAliasConverter : AttributeConverter<String, String?> {
    override fun convertToDatabaseColumn(attribute: String?): String? = if (attribute == "nil-entity") null else attribute

    override fun convertToEntityAttribute(dbData: String?): String? = if (dbData == null) "nil-entity" else dbData
}

/** null 由转换器处理为实体值的实体。 */
@Entity
internal class NullAliasedEntity {
    @Id
    var id: Long? = null

    @Convert(converter = NullAliasConverter::class)
    var value: String? = null
}
