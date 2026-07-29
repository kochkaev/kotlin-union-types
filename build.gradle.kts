plugins {
    id("io.github.kochkaev.kotlin.uniontypes.build.tasks.plugin")
}

val globalPluginVersion = libs.versions.unionTypes
version = project.findProperty("overrideVersion") as? String ?: globalPluginVersion.get()
group = "io.github.kochkaev.kotlin.uniontypes"

unionTypesTasks {
    pluginVersion = libs.versions.unionTypes
    kotlinVersion = libs.versions.kotlin
    latestSupportedKotlinVersion = libs.versions.latestSupportedKotlin
    compatibilityPropertiesFile = file("compatibility.properties")
    compatibilityMatrixFile = project.layout.buildDirectory.file("tmp/compatibility_matrix.txt")
    stagingDeploymentDir = project.layout.buildDirectory.dir("maven-staging")
}