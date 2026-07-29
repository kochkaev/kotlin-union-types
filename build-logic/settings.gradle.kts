dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

include(":utils")
include(":maven")
include(":tasks")
include(":gradle-plugin-gen")

rootProject.name = "build-logic"