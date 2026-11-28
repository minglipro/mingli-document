import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    `java-platform`
    `maven-publish`
    signing
}

version = providers.gradleProperty("projectVersion").get()

dependencies {
    constraints {
        api(project(":annotation"))
        api(project(":exception"))
        api(project(":core"))
        api(project(":schema"))
        api(project(":schema-registry"))
        api(project(":validation"))
        api(project(":jdbc"))
        api(project(":jpa"))
        api(project(":serialization"))
        api(project(":serialization-kotlin"))
        api(project(":codegen"))
        api(project(":openapi"))

        api("com.mingliqiye.document.format:json:$version")
        api("com.mingliqiye.document.format:yaml:$version")
        api("com.mingliqiye.document.format:xml:$version")
        api("com.mingliqiye.document.format:properties:$version")
        api("com.mingliqiye.document.format:toml:$version")
        api("com.mingliqiye.document.format:csv:$version")
        api("com.mingliqiye.document.format:ion:$version")
        api("com.mingliqiye.document.format:cbor:$version")
        api("com.mingliqiye.document.format:smile:$version")
        api("com.mingliqiye.document.format:avro:$version")
        api("com.mingliqiye.document.format:protobuf:$version")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            from(components["javaPlatform"])
            artifactId = "mingli-document-bom"
            pom {
                name.set("mingli-document-bom")
                description.set("Maven BOM for mingli-document")
                url.set("https://github.com/minglipro/mingli-document")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["mavenBom"])
}

// 与根项目一致：只推 release/snapshot 对应仓库，跳过 Gradle Plugin 仓库。
tasks.withType<PublishToMavenRepository>().configureEach {
    onlyIf {
        val repositoryName = repository.name
        val snapshot = project.version.toString().endsWith("-SNAPSHOT")
        when {
            repositoryName.contains("GradlePlugin", ignoreCase = true) -> false
            snapshot -> repositoryName.contains("Snapshot", ignoreCase = true)
            else -> repositoryName.contains("Release", ignoreCase = true)
        }
    }
}
