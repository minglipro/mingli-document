plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "mingli-document"

include(":annotation")
include(":codegen")
include(":codegen-test")
include(":core")
include(":exception")
include(":format-json")
include(":format-yaml")
include(":format-xml")
include(":format-properties")
include(":format-ion")
include(":format-smile")
include(":format-cbor")
include(":format-csv")
include(":format-toml")
include(":schema")
include(":format-avro")
include(":format-protobuf")
include(":serialization")
include(":bom")
include(":fuzz")
include(":serialization-kotlin")
include(":benchmarks")
