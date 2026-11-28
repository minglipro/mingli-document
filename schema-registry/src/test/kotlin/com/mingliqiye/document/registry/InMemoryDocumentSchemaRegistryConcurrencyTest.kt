package com.mingliqiye.document.registry

import com.mingliqiye.document.schema.DocumentSchema
import com.mingliqiye.document.schema.DocumentSchemaBuilder
import com.mingliqiye.document.schema.SchemaType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [InMemoryDocumentSchemaRegistry] 并发注册测试。
 */
class InMemoryDocumentSchemaRegistryConcurrencyTest {

    private val subject = SchemaSubject.of("concurrent")

    @Test
    fun concurrentDistinctRegistrationsProduceUniqueVersionsAndIds() {
        val registry = InMemoryDocumentSchemaRegistry(SchemaCompatibilityPolicy.NONE)
        val threadCount = 8
        val registrationsPerThread = 25
        val total = threadCount * registrationsPerThread
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threadCount)

        try {
            val futures = (0 until threadCount).map { thread ->
                executor.submit<Int> {
                    start.await()
                    for (index in 0 until registrationsPerThread) {
                        registry.register(subject, schema("f_${thread}_$index"))
                    }
                    registrationsPerThread
                }
            }
            start.countDown()
            var completed = 0
            for (future in futures) {
                completed += future.get(60, TimeUnit.SECONDS)
            }

            assertEquals(total, completed)
            val versions = registry.versions(subject)
            assertEquals(total, versions.size)
            assertEquals((1..total).map { SchemaVersion.of(it) }, versions)
            val ids = (1..total).map { registry.byId(SchemaId.of(it.toLong())) }
            assertEquals(total, ids.filterNotNull().map { it.id }.toSet().size)
            assertNotNull(registry.latest(subject))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun concurrentIdenticalRegistrationsProduceSingleVersion() {
        val registry = InMemoryDocumentSchemaRegistry()
        val threadCount = 8
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threadCount)
        val schema = schema("same")

        try {
            val futures = (0 until threadCount).map {
                executor.submit<SchemaId> {
                    start.await()
                    registry.register(subject, schema).id
                }
            }
            start.countDown()
            val ids = futures.map { it.get(60, TimeUnit.SECONDS) }

            assertEquals(1, ids.toSet().size)
            assertEquals(listOf(SchemaVersion.of(1)), registry.versions(subject))
            assertEquals(1, registry.latest(subject)?.version?.value)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun schema(fieldName: String): DocumentSchema = DocumentSchemaBuilder("Concurrent")
        .field(fieldName, SchemaType.STRING)
        .build()
}
