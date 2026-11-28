package com.mingliqiye.document.jpa.hibernate

import com.mingliqiye.document.jpa.JpaConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/** [HibernateConfig] / [HibernateDocumentMapper] 配置测试。 */
class HibernateConfigTest {

    @Test
    fun providesDefaultPolicies() {
        val config = HibernateConfig.builder().build()

        assertEquals(HibernateLazyLoadPolicy.INITIALIZE, config.lazyLoadPolicy)
        assertEquals(HibernateCyclePolicy.FAIL, config.cyclePolicy)
    }

    @Test
    fun buildsCustomPolicies() {
        val config = HibernateConfig.builder()
            .lazyLoadPolicy(HibernateLazyLoadPolicy.FAIL)
            .cyclePolicy(HibernateCyclePolicy.OMIT)
            .build()

        assertEquals(HibernateLazyLoadPolicy.FAIL, config.lazyLoadPolicy)
        assertEquals(HibernateCyclePolicy.OMIT, config.cyclePolicy)
    }

    @Test
    fun exposesConfigurationOnMapper() {
        val mapper = HibernateDocumentMapper()

        assertEquals(HibernateLazyLoadPolicy.INITIALIZE, mapper.config.lazyLoadPolicy)
        assertEquals(HibernateCyclePolicy.FAIL, mapper.config.cyclePolicy)
        assertEquals(1, mapper.jpaConfig.associationDepth)
    }

    @Test
    fun usesJpaConfigAssociationDepth() {
        val mapper = HibernateDocumentMapper(JpaConfig.builder().associationDepth(3).build())

        assertEquals(3, mapper.jpaConfig.associationDepth)
    }
}
