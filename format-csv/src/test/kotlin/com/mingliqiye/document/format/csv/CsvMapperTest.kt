package com.mingliqiye.document.format.csv

import com.mingliqiye.document.core.DocumentTypeReference
import com.mingliqiye.document.serialization.DocumentMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [CsvMapper] 测试。
 */
class CsvMapperTest {

    @Test
    fun mapperUsesCsvFormat() {
        val mapper: DocumentMapper = CsvMapper()

        assertEquals("csv", mapper.formatName)
        val node = mapper.toNode(mapOf("name" to "小于", "age" to 18))
        assertTrue(mapper.format(node).startsWith("name,age"))

        val rows = mapper.parse("name,age\n小于,18\n", DocumentTypeReference.listType(Person::class.java))
        assertEquals(1, rows.size)
        assertEquals("小于", rows[0].name)
        assertEquals(18, rows[0].age)

        val row = mapper.fromNode(CsvReader("name,age\n小于,18\n").read().asArray()[0], Person::class.java)
        assertEquals("小于", row.name)
        assertEquals(18, row.age)
    }

    @Test
    fun singleBeanWritesSingleRow() {
        val mapper = CsvMapper()
        val person = Person().apply {
            name = "小于"
            age = 18
        }

        val lines = mapper.format(person).trim().lines()
        assertEquals(2, lines.size)
        assertEquals(setOf("age", "name"), lines[0].split(",").toSet())
        assertEquals(setOf("18", "小于"), lines[1].split(",").toSet())

        val restored = mapper.parse(mapper.format(person), DocumentTypeReference.listType(Person::class.java))
        assertEquals(1, restored.size)
        assertEquals("小于", restored[0].name)
        assertEquals(18, restored[0].age)
    }

    @Test
    fun listOfBeansRoundTrip() {
        val mapper = CsvMapper()
        val people = listOf(
            Person().apply {
                name = "小于"
                age = 18
            },
            Person().apply {
                name = "张三"
                age = 20
            },
        )

        val csv = mapper.format(people)
        val type = DocumentTypeReference.listType(Person::class.java)
        val restored = mapper.parse(csv, type)

        assertEquals(2, restored.size)
        assertEquals("小于", restored[0].name)
        assertEquals(18, restored[0].age)
        assertEquals("张三", restored[1].name)
        assertEquals(20, restored[1].age)
    }

    @Test
    fun emptyListRoundTrips() {
        val mapper = CsvMapper()
        val type = DocumentTypeReference.listType(Person::class.java)

        assertEquals("", mapper.format(emptyList<Person>()))
        assertTrue(mapper.parse("", type).isEmpty())
    }

    @Test
    fun csvConfigControlsDelimiterAndHeader() {
        val mapper = CsvMapper(
            CsvConfig.builder()
                .delimiter(';')
                .build(),
        )
        val people = listOf(
            Person().apply {
                name = "小于"
                age = 18
            },
        )

        val csv = mapper.format(people)
        assertTrue(csv.contains(";"))
        assertEquals(setOf("name", "age"), csv.trim().lines()[0].split(";").toSet())
        val restored = mapper.parse(csv, DocumentTypeReference.listType(Person::class.java))
        assertEquals("小于", restored[0].name)
        assertEquals(18, restored[0].age)

        val noHeaderMapper = CsvMapper(CsvConfig.builder().header(false).build())
        val noHeader = noHeaderMapper.format(people)
        val noHeaderLines = noHeader.trim().lines()
        assertEquals(1, noHeaderLines.size)
        assertFalse(noHeaderLines[0].contains("name") || noHeaderLines[0].contains("age"))
    }

    @Test
    fun builderAcceptsCsvConfig() {
        val mapper = CsvMapper.builder()
            .config(CsvConfig.builder().delimiter('|').build())
            .build()

        assertTrue(mapper.format(mapOf("a" to 1, "b" to 2)).startsWith("a|b"))
    }

    private class Person {
        var name: String = ""
        var age: Int = 0
    }
}
