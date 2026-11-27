plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "mingli-document"

include(":annotation")
include(":core")
include(":exception")
include(":format-json")
include(":format-yaml")
include(":format-xml")
include(":format-properties")
include(":serialization")
include(":serialization-kotlin")
include(":benchmarks")
