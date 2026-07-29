package io.github.kochkaev.kotlin.uniontypes.build.gradlePluginGen

import org.gradle.kotlin.dsl.register
import io.github.kochkaev.kotlin.uniontypes.build.utils.*

plugins {
    kotlin("jvm")
}

val extension = extensions.create<GradlePluginGenExtension>("gradlePluginGen")

val generateBuildConfig = tasks.register<GenerateBuildConfig>("generateBuildConfig") {
    description = "Generates BuildConfig.kt for the Gradle plugin"

    latestSupportedKotlinVersion = extension.latestSupportedKotlinVersion
    compatibilityMap = extension.compatibilityPropertiesFile.get().asFile.loadPropertiesMap()
    metaList = extension.metaVersionsFile.get().asFile.loadPropertiesList()
    outputDirectory = extension.outputDirectory
}

sourceSets {
    main {
        kotlin.srcDir(generateBuildConfig)
    }
}