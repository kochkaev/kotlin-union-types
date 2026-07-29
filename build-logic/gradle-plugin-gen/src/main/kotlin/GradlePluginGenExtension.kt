package io.github.kochkaev.kotlin.uniontypes.build.gradlePluginGen

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

interface GradlePluginGenExtension {
    val latestSupportedKotlinVersion: Property<String>
    val compatibilityPropertiesFile: RegularFileProperty
    val metaVersionsFile: RegularFileProperty
    val outputDirectory: DirectoryProperty
}