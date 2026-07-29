package io.github.kochkaev.kotlin.uniontypes.build.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

interface TasksExtension {
    val pluginVersion: Property<String>
    val kotlinVersion: Property<String>
    val latestSupportedKotlinVersion: Property<String>
    val compatibilityPropertiesFile: RegularFileProperty
    val compatibilityMatrixFile: RegularFileProperty
    val stagingDeploymentDir: DirectoryProperty
}