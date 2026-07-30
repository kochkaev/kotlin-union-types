package io.github.kochkaev.kotlin.uniontypes.build.tasks

import org.gradle.kotlin.dsl.register
import io.github.kochkaev.kotlin.uniontypes.build.utils.*

val extension = extensions.create<TasksExtension>("unionTypesTasks")

val generateCompatibilityMatrix = tasks.register<CompatibilityMatrixTask>("generateCompatibilityMatrix") {
    description = "Run tests on each Kotlin version in project bounds to generate compatibility matrix"
    kotlinVersions = getKotlinVersionsFromMaven(extension.kotlinVersion.get(), extension.latestSupportedKotlinVersion.get())
    rootDir = project.rootDir
    compatibilityOutputFile = extension.compatibilityMatrixFile
}

val checkPortCompatibility = tasks.register<CheckKotlinPortCompatibilityTask>("checkPortCompatibility") {
    description = "Checks compatibility of Kotlin port"
    group = "verification"
    targetKotlinVersion = project.findProperty("targetKotlinVersion")?.toString() ?: ""
    latestSupportedKotlinVersion = extension.latestSupportedKotlinVersion
    outputFile = project.layout.buildDirectory.file("port_result.txt")
    rootDir = project.rootDir
}
val addPortVersionCompatibilityProperties = tasks.register<UpdateCompatibilityPropertiesTask>("addPortVersionCompatibilityProperties") {
    description = "Adds port version to compatibility properties"
    group = "build setup"
    globalPluginVersion = extension.pluginVersion
    outputFile = extension.compatibilityPropertiesFile

    featureRelease = false
    targetKotlinVersion = extension.latestSupportedKotlinVersion
}
val regenerateCompatibilityProperties = tasks.register<UpdateCompatibilityPropertiesTask>("regenerateCompatibilityProperties") {
    description = "Regenerates compatibility properties"
    group = "build setup"
    globalPluginVersion = extension.pluginVersion
    outputFile = extension.compatibilityPropertiesFile

    featureRelease = true
    compatibilityMatrix = generateCompatibilityMatrix
        .flatMap { it.compatibilityOutputFile }
        .map { it.asFile.loadCompatibilityMatrix() }
}

val generateStagingDeployment = tasks.register<GenerateStagingDeploymentTask>("generateStagingDeployment") {
    description = "Generates staging deployment"
    group = "publishing"
    globalPluginVersion = extension.pluginVersion
    publishMeta = true
    stagingDir = extension.stagingDeploymentDir
    compatibilityMatrix = generateCompatibilityMatrix
        .flatMap { it.compatibilityOutputFile }
        .map { it.asFile.loadCompatibilityMatrix() }
    rootDir = project.rootDir
}