package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import com.mingliqiye.document.schema.SchemaType
import com.mingliqiye.document.serialization.MissingFieldPolicy
import com.mingliqiye.document.serialization.exception.DocumentSerializationException
import java.math.BigDecimal
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * [CsvMapper] 按 [CsvSchema] 读写测试。
 */
class CsvSchemaMapperTest {

    private val personSchema = CsvSchema.builder("person")
        .string("name")
        .int("age")
        .build()

    private val personListType = DocumentTypeReference.listType(Person::class.java)

    private fun person(name: String, age: Int): Person = Person().apply {
        this.name = name
        this.age = age
    }

    @Test
    fun schemaControlsColumnOrderOnFormat() {
        val schema = CsvSchema.builder("person")
            .int("age")
            .string("name")
            .build()
        val mapper = CsvMapper(schema)

        assertEquals("age,name\n18,小于\n", mapper.format(listOf(person("小于", 18))))
        assertEquals("age,name\n18,小于\n", mapper.format(person("小于", 18)))
    }

    @Test
    fun parseMapsColumnsByNameRegardlessOfCsvOrder() {
        val schema = CsvSchema.builder("person")
            .int("age")
            .string("name")
            .build()
        val mapper = CsvMapper(CsvConfig.builder().schema(schema).build())

        val people = mapper.parse("name,age\n小于,18\n张三,20\n", personListType)

        assertEquals(2, people.size)
        assertEquals("小于", people[0].name)
        assertEquals(18, people[0].age)
        assertEquals("张三", people[1].name)
        assertEquals(20, people[1].age)
        assertEquals("age,name\n18,小于\n20,张三\n", mapper.format(people))
    }

    @Test
    fun noHeaderReadsAndWritesBySchemaOrder() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .int("age")
            .header(false)
            .build()
        val mapper = CsvMapper(schema)

        assertEquals("小于,18\n", mapper.format(listOf(person("小于", 18))))

        val restored = mapper.parse("张三,20\n", personListType)
        assertEquals("张三", restored[0].name)
        assertEquals(20, restored[0].age)
    }

    @Test
    fun typesAreParsedAccordingToSchema() {
        val schema = CsvSchema.builder("typed")
            .string("name")
            .int("count")
            .long("total")
            .double("ratio")
            .boolean("enabled")
            .bytes("payload")
            .decimal("amount")
            .build()
        val mapper = CsvMapper(schema)
        val payload = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))
        val csv = "name,count,total,ratio,enabled,payload,amount\n" +
            "value,7,9000000000,1.5,TRUE,$payload,12.50\n"

        val row = CsvSchemaReader.parse(csv, schema).asArray()[0].asObject()
        assertEquals(7, row["count"]?.asNumber()?.toInt())
        assertEquals("9000000000", row["total"]?.asNumber()?.raw)
        assertEquals(1.5, row["ratio"]?.asNumber()?.toDouble())
        assertEquals(true, row["enabled"]?.asBoolean())
        assertEquals("12.50", row["amount"]?.asNumber()?.raw)

        val bean = mapper.parse(csv, DocumentTypeReference.listType(TypedBean::class.java))[0]
        assertEquals("value", bean.name)
        assertEquals(7, bean.count)
        assertEquals(9000000000L, bean.total)
        assertEquals(1.5, bean.ratio)
        assertEquals(true, bean.enabled)
        assertContentEquals(byteArrayOf(1, 2, 3), bean.payload)
        assertEquals("12.50", bean.amount.toPlainString())

        val formatted = mapper.format(bean)
        assertEquals(
            listOf(
                "name,count,total,ratio,enabled,payload,amount",
                "value,7,9000000000,1.5,true,$payload,12.50",
            ),
            formatted.trim().lines(),
        )
    }

    @Test
    fun invalidTypedValuesAreRejected() {
        val schema = CsvSchema.builder("typed")
            .string("name")
            .int("count")
            .boolean("enabled")
            .build()
        val mapper = CsvMapper(schema)

        assertFailsWith<CsvParseException> {
            mapper.parse("name,count,enabled\nvalue,abc,true\n", DocumentTypeReference.listType(TypedBean::class.java))
        }
        assertFailsWith<CsvParseException> {
            mapper.parse("name,count,enabled\nvalue,1,maybe\n", DocumentTypeReference.listType(TypedBean::class.java))
        }
    }

    @Test
    fun unknownColumnsAreIgnoredByDefault() {
        val mapper = CsvMapper(personSchema)

        val people = mapper.parse("name,age,active\n小于,18,true\n", personListType)
        assertEquals("小于", people[0].name)
        assertEquals(18, people[0].age)
        assertEquals(false, people[0].active)

        assertEquals("name,age\n小于,18\n", mapper.format(listOf(person("小于", 18))))
    }

    @Test
    fun failOnUnknownColumnsRejectsUnknownColumns() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .int("age")
            .failOnUnknownColumns(true)
            .build()
        val mapper = CsvMapper(schema)

        assertFailsWith<CsvParseException> {
            mapper.parse("name,age,active\n小于,18,true\n", personListType)
        }
        assertFailsWith<CsvException> {
            mapper.format(listOf(person("小于", 18)))
        }
    }

    @Test
    fun requiredColumnsMustBePresent() {
        assertFailsWith<CsvParseException> {
            CsvMapper(personSchema).parse("name\n小于\n", personListType)
        }
        assertFailsWith<CsvParseException> {
            CsvMapper(personSchema).parse("name,age\n小于,\n", personListType)
        }

        val noHeader = CsvSchema.builder("person")
            .string("name")
            .int("age")
            .header(false)
            .build()
        assertFailsWith<CsvParseException> {
            CsvMapper(noHeader).parse("小于\n", personListType)
        }

        val writeSchema = CsvSchema.builder("person")
            .string("name")
            .string("nickname")
            .build()
        assertFailsWith<CsvException> {
            CsvMapper(writeSchema).format(person("小于", 18))
        }
    }

    @Test
    fun optionalColumnsFallBackToBeanDefaults() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .int("age", required = false)
            .build()
        val mapper = CsvMapper(schema)

        val person = mapper.parse("name\n小于\n", personListType)[0]
        assertEquals("小于", person.name)
        assertEquals(0, person.age)

        val strictMapper = CsvMapper(
            CsvConfig.builder()
                .schema(schema)
                .missingFieldPolicy(MissingFieldPolicy.FAIL)
                .build(),
        )
        assertFailsWith<DocumentSerializationException> {
            strictMapper.parse("name\n小于\n", personListType)
        }
    }

    @Test
    fun nullableColumnsReadEmptyCellsAsNull() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .int("age", nullable = true)
            .build()
        val mapper = CsvMapper(schema)
        val type = DocumentTypeReference.listType(NullablePerson::class.java)

        val person = mapper.parse("name,age\n小于,\n", type)[0]
        assertEquals("小于", person.name)
        assertNull(person.age)
    }

    @Test
    fun unknownBeanFieldsFollowConfigPolicy() {
        val schema = CsvSchema.builder("person")
            .string("name")
            .string("nickname")
            .build()
        val lenient = CsvMapper(CsvConfig.builder().schema(schema).build())
        assertEquals("小于", lenient.parse("name,nickname\n小于,别名\n", personListType)[0].name)

        val strict = CsvMapper(
            CsvConfig.builder()
                .schema(schema)
                .failOnUnknownFields(true)
                .build(),
        )
        assertFailsWith<DocumentSerializationException> {
            strict.parse("name,nickname\n小于,别名\n", personListType)
        }
    }

    @Test
    fun schemaHeaderTakesPrecedenceOverConfigHeader() {
        val headerSchema = CsvSchema.builder("person")
            .string("name")
            .int("age")
            .build()
        val headerConfig = CsvConfig.builder().header(false).schema(headerSchema).build()
        assertEquals(
            "name,age\n小于,18\n",
            CsvMapper(headerConfig).format(listOf(person("小于", 18))),
        )

        val noHeaderSchema = CsvSchema.builder("person")
            .string("name")
            .int("age")
            .header(false)
            .build()
        val noHeaderConfig = CsvConfig.builder().header(true).schema(noHeaderSchema).build()
        assertEquals(
            "小于,18\n",
            CsvMapper(noHeaderConfig).format(listOf(person("小于", 18))),
        )
        assertEquals(
            "张三,20\n",
            CsvMapper(noHeaderConfig).format(listOf(person("张三", 20))),
        )
    }

    @Test
    fun builderAcceptsSchema() {
        val schema = CsvSchema.builder("person")
            .int("age")
            .string("name")
            .build()

        val mapper = CsvMapper.builder()
            .schema(schema)
            .build()
        assertEquals("age,name\n18,小于\n", mapper.format(person("小于", 18)))

        val delimiterMapper = CsvMapper.builder()
            .schema(schema)
            .config(CsvConfig.builder().delimiter(';').build())
            .build()
        assertEquals("age;name\n18;小于\n", delimiterMapper.format(person("小于", 18)))
    }

    @Test
    fun emptyListWritesHeaderOnly() {
        assertEquals("name,age\n", CsvMapper(personSchema).format(emptyList<Person>()))

        val noHeaderSchema = CsvSchema.builder("person")
            .string("name")
            .int("age")
            .header(false)
            .build()
        assertEquals("", CsvMapper(noHeaderSchema).format(emptyList<Person>()))
    }

    @Test
    fun unsupportedSchemaTypesAreRejected() {
        val schema = CsvSchema.builder("nested")
            .field("meta", SchemaType.OBJECT)
            .build()

        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse("meta\nvalue\n", schema)
        }
        assertFailsWith<CsvException> {
            CsvMapper(schema).format(mapOf("meta" to mapOf("city" to "杭州")))
        }
    }

    private class Person {
        var name: String = ""
        var age: Int = 0
        var active: Boolean = false
    }

    private class TypedBean {
        var name: String = ""
        var count: Int = 0
        var total: Long = 0
        var ratio: Double = 0.0
        var enabled: Boolean = false
        var payload: ByteArray = ByteArray(0)
        var amount: BigDecimal = BigDecimal.ZERO
    }

    private class NullablePerson {
        var name: String = ""
        var age: Int? = null
    }
}
