package com.mingliqiye.document.codegen.test

import com.mingliqiye.document.format.json.JsonMapper
import com.mingliqiye.document.generated.CustomDocumentModule
import com.mingliqiye.document.generated.GeneratedDocumentModule
import com.mingliqiye.document.generated.MarkerDocumentConverter
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfiguredDocumentModuleTest {

    @Test
    fun configuredModuleNameAndPackageAreUsed() {
        assertEquals(Marker::class.java, MarkerDocumentConverter().getType().getRawType())

        val module = CustomDocumentModule()
        val context = RecordingModuleSetupContext()
        module.setupModule(context)

        assertEquals("CustomDocumentModule", module.moduleName)
        assertEquals(
            listOf(Marker::class.java),
            context.converters.map { it.getType().getRawType() },
        )
    }

    @Test
    fun bothGeneratedModulesCanBeRegisteredTogether() {
        val mapper = JsonMapper.builder()
            .addModules(GeneratedDocumentModule(), CustomDocumentModule())
            .build()

        val json = mapper.format(Marker.BETA)
        assertEquals("\"BETA\"", json)
        assertEquals(Marker.BETA, mapper.parse(json, Marker::class.java))
    }
}
