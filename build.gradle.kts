import io.github.kochkaev.kotlin.uniontypes.build.*

plugins {
    alias(libs.plugins.mavenPublishing) apply false
    id("io.github.kochkaev.kotlin.uniontypes.maven-publishing")
}

val compatibilityPropertiesFile = file("compatibility.properties")
val stagingDeploymentDir = project.layout.buildDirectory.dir("maven-staging")

val generateCompatibilityMatrix = tasks.register<CompatibilityMatrixTask>("generateCompatibilityMatrix") {
    description = "Run tests on each Kotlin version in project bounds to generate compatibility matrix"
    minKotlinVersion = libs.versions.kotlin
    maxKotlinVersion = libs.versions.latestSupportedKotlin
    rootDir = project.rootDir
    compatibilityOutputFile = project.layout.buildDirectory.file("tmp/compatibility_matrix.txt")
}

val checkPortCompatibility = tasks.register<CheckKotlinPortCompatibilityTask>("checkPortCompatibility") {
    description = "Checks compatibility of Kotlin port"
    group = "verification"
    targetKotlinVersion = project.findProperty("targetKotlinVersion")?.toString() ?: ""
    latestSupportedKotlinVersion = libs.versions.latestSupportedKotlin
    outputFile = project.layout.buildDirectory.file("port_result.txt")
    rootDir = project.rootDir
}
val addPortVersionCompatibilityProperties = tasks.register<UpdateCompatibilityPropertiesTask>("addPortVersionCompatibilityProperties") {
    description = "Adds port version to compatibility properties"
    group = "build setup"
    globalPluginVersion = libs.versions.unionTypes
    outputFile = compatibilityPropertiesFile

    featureRelease = false
    targetKotlinVersion = libs.versions.latestSupportedKotlin
}
val regenerateCompatibilityProperties = tasks.register<UpdateCompatibilityPropertiesTask>("regenerateCompatibilityProperties") {
    description = "Regenerates compatibility properties"
    group = "build setup"
    globalPluginVersion = libs.versions.unionTypes
    outputFile = compatibilityPropertiesFile

    featureRelease = true
    dependsOn(generateCompatibilityMatrix)
    matrixResults = generateCompatibilityMatrix
        .flatMap { it.compatibilityOutputFile }
        .map { regularFile ->
            regularFile.asFile
                .loadPropertiesMap()
                .mapValues { it.value.toBoolean() }
        }
}

val generateStagingDeployment = tasks.register<GenerateStagingDeploymentTask>("generateStagingDeployment") {
    description = "Generates staging deployment"
    group = "publishing"
    globalPluginVersion = libs.versions.unionTypes
    publishMeta = true
    stagingDir = stagingDeploymentDir
    compatibilityProperties = compatibilityPropertiesFile
    rootDir = project.rootDir
}