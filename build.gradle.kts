import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.nio.file.Paths

buildscript {
    val defaultOpensearchVersion = "2.18.0"
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

val tag = grgit.describe(mapOf("tags" to true, "match" to listOf("v*"))) ?: "v0.0.0"
val currentPluginVersion = tag.trimStart('v')
version = currentPluginVersion
val opensearchVersions = org.opensearch.gradle.VersionProperties.getVersions() as Map<String, String>

val distDir = Paths.get(buildDir.path, "distributions")

repositories {
    mavenCentral()
}

dependencies {
    implementation("dev.evo.persistent:persistent-hashmap")
    implementation("commons-logging", "commons-logging", opensearchVersions["commonslogging"])
    implementation("org.apache.logging.log4j", "log4j-slf4j2-impl", opensearchVersions["log4j"])

    // FIXME: IDK why it does not apply transitive dependencies from persistent-hashmap
    implementation("org.slf4j", "slf4j-api", "2.0.7")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_11.toString()
}

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

// We do not have integration tests yet, but github action for elasticsearch plugin wants them
tasks.register("integTest") {}

tasks.named("assemble") {
    dependsOn("deb")
}

tasks.register("deb", com.netflix.gradle.plugins.deb.Deb::class) {
    dependsOn("bundlePlugin")

    packageName = "opensearch-${pluginName}-plugin"
    version = currentPluginVersion
    requires("opensearch", opensearchVersions["opensearch"])
        .or("opensearch-oss", opensearchVersions["opensearch"])

    from(zipTree(tasks["bundlePlugin"].outputs.files.singleFile))
    val opensearchHome = project.properties["esHome"] ?: "/usr/share/opensearch"
    into("$opensearchHome/plugins/${pluginName}")

    doLast {
        if (properties.containsKey("assembledInfo")) {
            distDir.resolve("assembled-deb.filename").toFile()
                .writeText(assembleArchiveName())
        }
    }
}
