import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Paths

buildscript {
    val defaultOpensearchVersion = "2.17.0"
    val opensearchVersion = if (hasProperty("opensearchVersion")) {
        property("opensearchVersion")
    } else {
        defaultOpensearchVersion
    }

    dependencies {
        classpath("org.opensearch.gradle:build-tools:$opensearchVersion")
    }
}

plugins {
    idea
    java
    kotlin("jvm") version "1.9.24"
    id("org.ajoberstar.grgit") version "4.1.1"
    id("com.netflix.nebula.ospackage") version "11.9.0"
}

apply {
    plugin("opensearch.opensearchplugin")
}

subprojects {
    apply {
        plugin("java")
    }
}

val pluginName = "mapper-external-file"

configure<org.opensearch.gradle.plugin.PluginPropertiesExtension> {
    name = pluginName
    description = "External file field mapper for OpenSearch"
    classname = "company.evo.opensearch.plugin.mapper.ExternalFileMapperPlugin"
    licenseFile = rootProject.file("LICENSE.txt")
    noticeFile = rootProject.file("NOTICE.txt")
}

val grgit: org.ajoberstar.grgit.Grgit? by extra
val tag = grgit?.describe(mapOf("tags" to true, "match" to listOf("v*"))) ?: "v0.0.0"
version = tag.trimStart('v')
val opensearchVersions = org.opensearch.gradle.VersionProperties.getVersions() as Map<String, String>

val distDir = Paths.get(buildDir.path, "distributions")

repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.evo.persistent:persistent-hashmap")
    implementation("commons-logging", "commons-logging", opensearchVersions["commonslogging"])
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
}
// tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>.all {
//     kotlinOptions {
//         jvmTarget = JavaVersion.VERSION_1_8.toString()
//     }
// }

tasks.register("listRepos") {
    doLast {
        println("Repositories:")
        project.repositories
            .forEach{ repo ->
                val repoUrl = when (repo) {
                    is MavenArtifactRepository -> repo.url.toString()
                    is IvyArtifactRepository -> repo.url.toString()
                    else -> "???"
                }
                println("Name: ${repo.name}; url: $repoUrl")
            }
    }
}

tasks.named("assemble") {
    dependsOn("deb")
}

tasks.register("deb", com.netflix.gradle.plugins.deb.Deb::class) {
    dependsOn("bundlePlugin")

    packageName = "opensearch-${pluginName}-plugin"
    requires("opensearch", opensearchVersions["opensearch"])
        .or("opensearch-oss", opensearchVersions["opensearch"])

    from(zipTree(tasks["bundlePlugin"].outputs.files.singleFile))

    val esHome = project.properties["esHome"] ?: "/usr/share/opensearchsearch"
    into("$esHome/plugins/${pluginName}")

    doLast {
        if (properties.containsKey("assembledInfo")) {
            distDir.resolve("assembled-deb.filename").toFile()
                .writeText(assembleArchiveName())
        }
    }
}
