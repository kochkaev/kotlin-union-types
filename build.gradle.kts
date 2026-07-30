plugins {
    id("io.github.kochkaev.kotlin.uniontypes.build.tasks.plugin")
    id("io.github.kochkaev.kotlin.uniontypes.build.maven.publish")
}

val stagingDir = project.layout.buildDirectory.dir("maven-staging")
val globalPluginVersion = libs.versions.unionTypes
version = project.findProperty("overrideVersion") as? String ?: globalPluginVersion.get()
group = "io.github.kochkaev.kotlin.uniontypes"

unionTypesTasks {
    pluginVersion = globalPluginVersion
    kotlinVersion = libs.versions.kotlin
    latestSupportedKotlinVersion = libs.versions.latestSupportedKotlin
    compatibilityPropertiesFile = file("compatibility.properties")
    compatibilityMatrixFile = project.layout.buildDirectory.file("tmp/compatibility_matrix.txt")
    stagingDeploymentDir = stagingDir
}

unionTypesPublish {
    stagingDirectory = stagingDir

    mavenCentral {
        username = "mavenCentralUsername" // Not yet implemented
        password = "mavenCentralPassword"
    }
}