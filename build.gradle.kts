import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    kotlin("jvm") version "2.0.0"
    idea
    id("opensearch.opensearchplugin")
    id("opensearch.java-agent")
    id("com.netflix.nebula.ospackage")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(22)
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_22
    targetCompatibility = JavaVersion.VERSION_22
}

version = Versions.project
val opensearchVersions = org.opensearch.gradle.VersionProperties.getVersions() as Map<String, String>

configureOpensearchPlugin(
    name = project.name,
    description = "External file field mapper for OpenSearch",
    classname = "company.evo.opensearch.plugin.mapper.ExternalFileMapperPlugin",
    numberOfTestClusterNodes = 2,
    // Compatibility between MINOR update is not working at the moment:
    // https://github.com/opensearch-project/OpenSearch/issues/18787
    opensearchCompatibility = OpensearchCompatibility.REVISION,
)

dependencies {
    implementation("dev.evo.persistent:persistent-hashmap")

    runtimeOnly("org.slf4j", "slf4j-api", opensearchVersions["slf4j"])
    runtimeOnly("org.apache.logging.log4j", "log4j-slf4j2-impl", opensearchVersions["log4j"])
}
