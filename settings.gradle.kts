plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "opensearch-mapper-external-file"

includeBuild("persistent-hashmap")
