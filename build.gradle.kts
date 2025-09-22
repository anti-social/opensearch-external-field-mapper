plugins {
    java
    kotlin("jvm") version "1.9.24"
    idea
    id("opensearch.opensearchplugin")
    id("opensearch.java-agent")
    id("com.netflix.nebula.ospackage")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

version = Versions.project

configureOpensearchPlugin(
    name = "external-field-mapper",
    description = "External file field mapper for OpenSearch",
    classname = "company.evo.opensearch.plugin.mapper.ExternalFileMapperPlugin",
    numberOfTestClusterNodes = 2,
    // Compatibility between MINOR update is not working at the moment:
    // https://github.com/opensearch-project/OpenSearch/issues/18787
    opensearchCompatibility = OpensearchCompatibility.REVISION,
)

dependencies {
    implementation("dev.evo.persistent:persistent-hashmap")
}
