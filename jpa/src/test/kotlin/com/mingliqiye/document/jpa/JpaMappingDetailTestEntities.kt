package com.mingliqiye.document.jpa

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapKeyJoinColumn
import jakarta.persistence.MapKeyJoinColumns
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import java.math.BigDecimal

// ----------------------------------------------------------------------
// @Convert(attributeName = ...) 测试模型
// ----------------------------------------------------------------------

/** 实体字符串 -> 数据库字符串：数据库值加 `db:` 前缀。 */
internal class DbPrefixConverter : AttributeConverter<String, String> {
    override fun convertToDatabaseColumn(attribute: String?): String? = attribute?.let { "db:$it" }

    override fun convertToEntityAttribute(dbData: String?): String? = dbData?.removePrefix("db:")
}

/** 实体字符串 -> 数据库字符串：数据库值大写。 */
internal class UpperConverter : AttributeConverter<String, String> {
    override fun convertToDatabaseColumn(attribute: String?): String? = attribute?.uppercase()

    override fun convertToEntityAttribute(dbData: String?): String? = dbData?.lowercase()
}

/** `@Embedded` 属性上的点分路径转换。 */
@Entity
internal class ConvertedProfileEntity {
    @Id
    var id: Long? = null

    @Embedded
    @Convert(attributeName = "zipCode", converter = DbPrefixConverter::class)
    var address: Address? = null
}

/** 嵌套嵌入对象：地理坐标。 */
@Embeddable
internal class GeoPoint {
    var lat: String? = null

    var lng: String? = null
}

/** 嵌套嵌入对象：地址 + 坐标。 */
@Embeddable
internal class GeoAddress {
    @Embedded
    var geo: GeoPoint? = null

    var city: String? = null
}

/** `@Embedded` 属性上的多级点分路径转换（geo.lat）。 */
@Entity
internal class NestedConvertedProfileEntity {
    @Id
    var id: Long? = null

    @Embedded
    @Convert(attributeName = "geo.lat", converter = DbPrefixConverter::class)
    var address: GeoAddress? = null
}

/** 类级 @Convert(attributeName = "...")：路径相对实体根。 */
@Entity
@Convert(attributeName = "address.zipCode", converter = DbPrefixConverter::class)
internal class ClassLevelConvertedEntity {
    @Id
    var id: Long? = null

    @Embedded
    var address: Address? = null
}

/** `@EmbeddedId` 复合主键组件。 */
@Embeddable
internal class ConvertedOrderKey {
    @Convert(converter = DbPrefixConverter::class)
    var orderNo: String? = null

    var region: String? = null
}

/** `@EmbeddedId` 属性上的 attributeName 转换（region 覆盖为大写）。 */
@Entity
internal class ConvertedOrderEntity {
    @EmbeddedId
    @Convert(attributeName = "region", converter = UpperConverter::class)
    var key: ConvertedOrderKey? = null
}

/** 嵌入类自身声明了转换器的金额。 */
@Embeddable
internal class AmountHolder {
    @Convert(converter = MoneyConverter::class)
    var amount: Money? = null
}

/** 属性级 @Convert(attributeName) 应覆盖嵌入类自身声明的转换器。 */
@Entity
internal class PriorityConvertedEntity {
    @Id
    var id: Long? = null

    @Embedded
    @Convert(attributeName = "amount", converter = CompactMoneyConverter::class)
    var holder: AmountHolder? = null
}

/** 嵌入类自身声明了转换器。 */
@Embeddable
internal class PrefixedHolder {
    @Convert(converter = DbPrefixConverter::class)
    var value: String? = null
}

/** `disableConversion` 覆盖应关闭嵌入类属性自身的转换器。 */
@Entity
internal class DisabledOverrideEntity {
    @Id
    var id: Long? = null

    @Embedded
    @Convert(attributeName = "value", disableConversion = true)
    var holder: PrefixedHolder? = null
}

/** 失效路径：属性名不存在。 */
@Entity
internal class InvalidConvertPathEntity {
    @Id
    var id: Long? = null

    @Embedded
    @Convert(attributeName = "missingField", converter = DbPrefixConverter::class)
    var address: Address? = null
}

/** 失效路径：中间段不是 @Embedded。 */
@Entity
@Convert(attributeName = "name.foo", converter = DbPrefixConverter::class)
internal class InvalidConvertPathThroughScalarEntity {
    @Id
    var id: Long? = null

    var name: String? = null
}

/** 转换器类型与目标属性类型不匹配。 */
@Entity
internal class MismatchedConvertPathEntity {
    @Id
    var id: Long? = null

    @Embedded
    @Convert(attributeName = "zipCode", converter = MoneyConverter::class)
    var address: Address? = null
}

/** attributeName 不能用于普通基本属性。 */
@Entity
internal class AttributeNameOnScalarEntity {
    @Id
    var id: Long? = null

    @Convert(attributeName = "x", converter = DbPrefixConverter::class)
    var name: String? = null
}

// ----------------------------------------------------------------------
// 关系属性上的 @Convert 测试模型
// ----------------------------------------------------------------------

/** 实体关联值 -> 数据库标识符（Long）。 */
internal class EmployeeRefConverter : AttributeConverter<EmployeeEntity, Long> {
    override fun convertToDatabaseColumn(attribute: EmployeeEntity?): Long? = attribute?.id

    override fun convertToEntityAttribute(dbData: Long?): EmployeeEntity? = dbData?.let {
        EmployeeEntity().apply { id = it }
    }
}

/** 员工集合 -> 数据库逗号分隔标识符文本。 */
internal class EmployeeListConverter : AttributeConverter<List<EmployeeEntity>, String> {
    override fun convertToDatabaseColumn(attribute: List<EmployeeEntity>?): String? = attribute?.joinToString(",") { it.id.toString() }

    override fun convertToEntityAttribute(dbData: String?): List<EmployeeEntity>? = dbData?.let {
        if (it.isEmpty()) {
            emptyList()
        } else {
            it.split(',').map { id -> EmployeeEntity().apply { this.id = id.toLong() } }
        }
    }
}

/** 关系属性上的显式 @Convert：整个关联值按基本属性写/读。 */
@Entity
internal class ConvertedEmployeeLinkEntity {
    @Id
    var id: Long? = null

    @ManyToOne
    @Convert(converter = EmployeeRefConverter::class)
    var employee: EmployeeEntity? = null

    @OneToOne
    @Convert(converter = EmployeeRefConverter::class)
    var mentor: EmployeeEntity? = null

    @OneToMany
    @Convert(converter = EmployeeListConverter::class)
    var team: MutableList<EmployeeEntity> = mutableListOf()
}

// ----------------------------------------------------------------------
// @MapKeyJoinColumn 实体 Map 键测试模型
// ----------------------------------------------------------------------

/** Map 键为实体引用（单主键与 @EmbeddedId 复合主键）。 */
@Entity
internal class EntityKeyMapEntity {
    @Id
    var id: Long? = null

    @ManyToMany
    @MapKeyJoinColumn(name = "employee_id")
    var employees: MutableMap<EmployeeEntity, DepartmentEntity> = mutableMapOf()

    @ManyToMany
    @MapKeyJoinColumns(
        value = [
            MapKeyJoinColumn(name = "order_no"),
            MapKeyJoinColumn(name = "region"),
        ],
    )
    var orders: MutableMap<OrderEntity, DepartmentEntity> = mutableMapOf()
}

/** @MapKeyJoinColumn 用在基本类型键上：配置异常。 */
@Entity
internal class MapKeyJoinColumnOnBasicKeyEntity {
    @Id
    var id: Long? = null

    @ManyToMany
    @MapKeyJoinColumn(name = "department_id")
    var departments: MutableMap<Long, DepartmentEntity> = mutableMapOf()
}

/** 金额嵌入对象的辅助换算（用于测试注释示例可读性）。 */
internal fun moneyOf(amount: String, currency: String): Money = Money(BigDecimal(amount), currency)

/** 嵌入对象：仅用于 autoApply 优先级测试。 */
@Embeddable
internal class AutoAmountHolder {
    var amount: Money? = null
}

/** autoApply 与 attributeName 覆盖的优先级测试。 */
@Entity
internal class AutoApplyAmountEntity {
    @Id
    var id: Long? = null

    @Embedded
    @Convert(attributeName = "amount", converter = CompactMoneyConverter::class)
    var holder: AutoAmountHolder? = null
}

/** 关系属性转换器类型不匹配。 */
@Entity
internal class BadRelationshipConvertEntity {
    @Id
    var id: Long? = null

    @ManyToOne
    @Convert(converter = DbPrefixConverter::class)
    var employee: EmployeeEntity? = null
}

/** 关系属性上的 @Convert(attributeName) 不支持。 */
@Entity
internal class AttributeNameOnRelationshipEntity {
    @Id
    var id: Long? = null

    @ManyToOne
    @Convert(attributeName = "name", converter = DbPrefixConverter::class)
    var employee: EmployeeEntity? = null
}

/** 直接持有嵌入类的实体（测试嵌入类自身声明的转换器）。 */
@Entity
internal class EmbeddedHolderEntity {
    @Id
    var id: Long? = null

    @Embedded
    var holder: AmountHolder? = null
}

/** `@IdClass` 复合主键类（实体作为 Map 键）。 */
internal class BookKey {
    var shelf: Int? = null
    var slot: Int? = null
}

/** `@IdClass` 复合主键实体。 */
@Entity
@IdClass(BookKey::class)
internal class KeyedBookEntity {
    @Id
    var shelf: Int? = null

    @Id
    var slot: Int? = null

    var title: String? = null
}

/** Map 键为 `@IdClass` 复合主键实体。 */
@Entity
internal class IdClassKeyMapEntity {
    @Id
    var id: Long? = null

    @ManyToMany
    @MapKeyJoinColumns(
        value = [
            MapKeyJoinColumn(name = "shelf"),
            MapKeyJoinColumn(name = "slot"),
        ],
    )
    var books: MutableMap<KeyedBookEntity, DepartmentEntity> = mutableMapOf()
}
