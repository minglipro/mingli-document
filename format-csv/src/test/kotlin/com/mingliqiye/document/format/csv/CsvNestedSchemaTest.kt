package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.core.ObjectNode
import com.mingliqiye.document.format.csv.exception.CsvException
import com.mingliqiye.document.format.csv.exception.CsvParseException
import com.mingliqiye.document.schema.SchemaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 多级 Schema（嵌套 CsvSchema / 数组元素 Schema）读写测试。
 */
class CsvNestedSchemaTest {

    private val geo = CsvSchema.builder("geo")
        .double("lat")
        .double("lon")
        .build()

    private val address = CsvSchema.builder("address")
        .string("city")
        .int("zip", required = false)
        .objectField("geo", geo, required = false)
        .build()

    private val person = CsvSchema.builder("person")
        .string("name")
        .objectField("address", address, required = false)
        .build()

    private fun personNode(): ObjectNode = obj(
        "name" to str("小于"),
        "address" to obj(
            "city" to str("杭州"),
            "zip" to num(310000),
            "geo" to obj("lat" to dnum(30.1), "lon" to dnum(120.2)),
        ),
    )

    private fun personBean(): Person = Person().apply {
        name = "小于"
        address = Address().apply {
            city = "杭州"
            zip = 310000
            geo = Geo().apply {
                lat = 30.1
                lon = 120.2
            }
        }
    }

    @Test
    fun nestedSchemaExpandsColumnNames() {
        assertEquals(
            listOf("name", "address.city", "address.zip", "address.geo.lat", "address.geo.lon"),
            person.columnNames,
        )
        assertEquals(listOf("lat", "lon"), geo.columnNames)
        assertEquals(SchemaType.OBJECT, person.field("address")?.type)
        assertEquals(address, person.nestedObject("address"))
        assertNull(person.nestedObject("name"))
        assertEquals(geo, address.nestedObject("geo"))
    }

    @Test
    fun nestedSchemaWritesExpandedColumns() {
        val lines = arr(personNode()).toCsvString(person).trim().lines()

        assertEquals("name,address.city,address.zip,address.geo.lat,address.geo.lon", lines[0])
        assertEquals("小于,杭州,310000,30.1,120.2", lines[1])
    }

    @Test
    fun nestedSchemaReadsExpandedColumnsInAnyOrder() {
        val csv = "name,address.geo.lon,address.city,address.zip,address.geo.lat\n" +
            "小于,120.2,杭州,310000,30.1\n"

        val restored = CsvSchemaReader.parse(csv, person).asArray()[0]

        assertNodesEqual(personNode(), restored)
    }

    @Test
    fun nestedSchemaBeanRoundTrip() {
        val mapper = CsvMapper(person)

        val csv = mapper.format(listOf(personBean()))
        assertEquals("name,address.city,address.zip,address.geo.lat,address.geo.lon", csv.trim().lines()[0])

        val restored = mapper.parse(csv, DocumentTypeReference.listType(Person::class.java))
        assertEquals(1, restored.size)
        assertEquals("小于", restored[0].name)
        val restoredAddress = restored[0].address
        assertEquals("杭州", restoredAddress?.city)
        assertEquals(310000, restoredAddress?.zip)
        assertEquals(30.1, restoredAddress?.geo?.lat)
        assertEquals(120.2, restoredAddress?.geo?.lon)
    }

    @Test
    fun optionalNestedObjectAbsentIsOmittedOnRead() {
        val row = CsvSchemaReader.parse("name\n小于\n", person).asArray()[0].asObject()

        assertEquals(setOf("name"), row.keys)
    }

    @Test
    fun optionalNestedObjectAbsentRoundTripsThroughEmptyCells() {
        val row = obj("name" to str("小于"))

        val csv = arr(row).toCsvString(person)
        val restored = CsvSchemaReader.parse(csv, person).asArray()[0].asObject()

        assertEquals(setOf("name"), restored.keys)
        assertEquals("小于", restored["name"]?.asString())
    }

    @Test
    fun nestedSchemaUsesPositionalOrderWithoutHeader() {
        val noHeader = CsvSchema.builder("person")
            .string("name")
            .objectField("address", address, required = false)
            .header(false)
            .build()
        val csv = arr(personNode()).toCsvString(noHeader)

        assertFalse(csv.contains("name"))
        val restored = CsvSchemaReader.parse(csv, noHeader).asArray()[0].asObject()
        assertEquals("小于", restored["name"]?.asString())
        assertEquals("杭州", restored["address"]?.asObject()?.get("city")?.asString())
        assertEquals(310000, restored["address"]?.asObject()?.get("zip")?.asNumber()?.toInt())
    }

    @Test
    fun shortPositionalRowKeepsOptionalNestedFieldsAbsent() {
        val noHeader = CsvSchema.builder("person")
            .string("name")
            .objectField("address", address, required = false)
            .header(false)
            .build()

        val restored = CsvSchemaReader.parse("小于,杭州\n", noHeader).asArray()[0].asObject()

        assertEquals("小于", restored["name"]?.asString())
        val restoredAddress = restored["address"]?.asObject()
        assertEquals("杭州", restoredAddress?.get("city")?.asString())
        assertNull(restoredAddress?.get("zip"))
        assertNull(restoredAddress?.get("geo"))
    }

    @Test
    fun requiredNestedColumnsMustBePresent() {
        val strict = CsvSchema.builder("person")
            .string("name")
            .objectField("address", address)
            .build()

        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse("name\n小于\n", strict)
        }
        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse(
                "name,address.city,address.zip,address.geo.lat,address.geo.lon\n小于,,,,\n",
                strict,
            )
        }
        val requiredGeo = CsvSchema.builder("person")
            .string("name")
            .objectField(
                "address",
                CsvSchema.builder("address")
                    .string("city")
                    .objectField("geo", geo)
                    .build(),
            )
            .build()
        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse("name,address.city,address.geo.lat,address.geo.lon\n小于,杭州,,\n", requiredGeo)
        }
    }

    @Test
    fun missingNestedLeafIsRejectedOnWrite() {
        val requiredAddress = CsvSchema.builder("person")
            .string("name")
            .objectField("address", address)
            .build()
        assertFailsWith<CsvException> {
            arr(obj("name" to str("小于"))).toCsvString(requiredAddress)
        }
        assertFailsWith<CsvException> {
            arr(
                obj(
                    "name" to str("小于"),
                    "address" to obj("city" to str("杭州")),
                ),
            ).toCsvString(personWithRequiredZip())
        }
    }

    @Test
    fun invalidNestedLeafCellIsRejected() {
        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse("name,address.city,address.zip\n小于,杭州,abc\n", person)
        }
        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse("name,address.city\n小于,杭州\n", personWithRequiredZip())
        }
    }

    @Test
    fun misplacedNestedTypeIsRejected() {
        assertFailsWith<CsvException> {
            arr(
                obj(
                    "name" to str("小于"),
                    "address" to str("not-an-object"),
                ),
            ).toCsvString(person)
        }
    }

    @Test
    fun duplicateExpandedColumnsAreRejectedAtBuild() {
        assertFailsWith<IllegalArgumentException> {
            CsvSchema.builder("dup")
                .field("address.city", SchemaType.STRING)
                .objectField("address", CsvSchema.builder("nested").string("city").build())
                .build()
        }
    }

    @Test
    fun genericObjectAndArrayCellsUseJsonMode() {
        val generic = CsvSchema.builder("generic")
            .string("name")
            .field("meta", SchemaType.OBJECT)
            .field("tags", SchemaType.ARRAY)
            .build()
        val row = obj(
            "name" to str("x"),
            "meta" to obj("level" to num(1)),
            "tags" to arr(num(1), num(2)),
        )

        val csv = arr(row).toCsvString(generic, nestedMode = CsvNestedMode.JSON)
        val restored = CsvSchemaReader.parse(csv, generic, nestedMode = CsvNestedMode.JSON).asArray()[0]

        assertNodesEqual(row, restored)
    }

    @Test
    fun genericObjectAndArrayCellsAreRejectedByDefault() {
        val generic = CsvSchema.builder("generic")
            .string("name")
            .field("meta", SchemaType.OBJECT)
            .field("tags", SchemaType.ARRAY)
            .build()

        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse("name,meta,tags\nx,{},[]\n", generic)
        }
        assertFailsWith<CsvException> {
            arr(obj("name" to str("x"), "meta" to obj(), "tags" to arr())).toCsvString(generic)
        }
    }

    @Test
    fun declaredNestedSchemaKeepsColumnFormWithJsonModeConfig() {
        val mapper = CsvMapper(CsvConfig.builder().schema(person).nestedMode(CsvNestedMode.JSON).build())

        val csv = mapper.format(listOf(personBean()))
        assertEquals("name,address.city,address.zip,address.geo.lat,address.geo.lon", csv.trim().lines()[0])

        val restored = mapper.parse(csv, DocumentTypeReference.listType(Person::class.java))
        assertEquals("杭州", restored[0].address?.city)
    }

    @Test
    fun mapperBuilderKeepsNestedModeWithSchema() {
        val generic = CsvSchema.builder("generic")
            .string("name")
            .field("meta", SchemaType.OBJECT)
            .build()
        val mapper = CsvMapper.builder()
            .schema(generic)
            .config(CsvConfig.builder().nestedMode(CsvNestedMode.JSON).build())
            .build()

        val csv = mapper.format(mapOf("name" to "x", "meta" to mapOf("level" to 1)))
        assertTrue(csv.contains("level"))

        val row = CsvSchemaReader.parse(csv, generic, nestedMode = CsvNestedMode.JSON).asArray()[0].asObject()
        assertEquals("x", row["name"]?.asString())
        assertEquals(1, row["meta"]?.asObject()?.get("level")?.asNumber()?.toInt())
    }

    @Test
    fun arrayElementSchemaReadsTypedObjects() {
        val team = teamSchema()

        val csv = """
            name,members
            小队,"[{""name"":""小于"",""age"":18}]"
        """.trimIndent() + "\n"

        val row = CsvSchemaReader.parse(csv, team).asArray()[0].asObject()
        val members = row["members"]?.asArray()
        assertEquals(1, members?.size)
        assertEquals("小于", members?.get(0)?.asObject()?.get("name")?.asString())
        assertEquals(18, members?.get(0)?.asObject()?.get("age")?.asNumber()?.toInt())
    }

    @Test
    fun arrayElementSchemaWritesJsonAndRoundTrips() {
        val team = teamSchema()
        val mapper = CsvMapper(team)
        val bean = Team().apply {
            name = "小队"
            members = mutableListOf(
                Member().apply {
                    name = "小于"
                    age = 18
                },
                Member().apply {
                    name = "张三"
                    age = 20
                },
            )
        }

        val csv = mapper.format(listOf(bean))
        assertTrue(csv.contains("["))

        val restored = mapper.parse(csv, DocumentTypeReference.listType(Team::class.java))
        assertEquals(1, restored.size)
        assertEquals("小队", restored[0].name)
        assertEquals(2, restored[0].members.size)
        assertEquals("张三", restored[0].members[1].name)
        assertEquals(20, restored[0].members[1].age)
    }

    @Test
    fun invalidArrayElementSchemaValuesAreRejected() {
        val team = teamSchema()

        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse(
                """
                    name,members
                    小队,"[{""name"":""小于"",""age"":""abc""}]"
                """.trimIndent() + "\n",
                team,
            )
        }
        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse("name,members\n小队,\"[1,2]\"\n", team)
        }
        assertFailsWith<CsvException> {
            arr(
                obj(
                    "name" to str("小队"),
                    "members" to arr(num(1)),
                ),
            ).toCsvString(team)
        }
    }

    @Test
    fun arrayElementSchemaRequiresJsonArrayCell() {
        val team = teamSchema()

        assertFailsWith<CsvParseException> {
            CsvSchemaReader.parse("name,members\n小队,\"[{\"\"name\"\":\"\"小于\"\"}]\"\n", team)
        }
    }

    private fun personWithRequiredZip(): CsvSchema {
        val nestedZip = CsvSchema.builder("address")
            .string("city")
            .int("zip")
            .build()
        return CsvSchema.builder("person")
            .string("name")
            .objectField("address", nestedZip)
            .build()
    }

    private fun teamSchema(): CsvSchema {
        val member = CsvSchema.builder("member")
            .string("name")
            .int("age")
            .build()
        return CsvSchema.builder("team")
            .string("name")
            .arrayField("members", member)
            .build()
    }

    private class Person {
        var name: String = ""
        var address: Address? = null
    }

    private class Address {
        var city: String = ""
        var zip: Int = 0
        var geo: Geo? = null
    }

    private class Geo {
        var lat: Double = 0.0
        var lon: Double = 0.0
    }

    private class Team {
        var name: String = ""
        var members: MutableList<Member> = mutableListOf()
    }

    private class Member {
        var name: String = ""
        var age: Int = 0
    }
}
