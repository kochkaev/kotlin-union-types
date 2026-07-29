package io.github.kochkaev.kotlin.uniontypes.build.gradlePluginGen

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import kotlin.collections.component1
import kotlin.collections.component2

abstract class GenerateBuildConfig : DefaultTask() {

    @get:Input
    abstract val latestSupportedKotlinVersion: Property<String>

    @get:Input
    abstract val compatibilityMap: MapProperty<String, String>

    @get:Input
    abstract val metaList: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputDir = outputDirectory.get().asFile
        val outputFile = outputDir.resolve("io/github/kochkaev/kotlin/uniontypes/gradle/BuildConfig.kt")

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package io.github.kochkaev.kotlin.uniontypes.gradle

            internal object BuildConfig {
                const val LATEST_SUPPORTED_KOTLIN_VERSION = "${latestSupportedKotlinVersion.get()}"
                val COMPATIBILITY_MAP: Map<String, String> = mapOf(
                    ${compatibilityMap.get().toKotlinMapString()}
                )
                val META_LIST: List<String> = listOf(
                    ${metaList.get().toKotlinListString()}
                )
            }
            """.trimIndent()
        )
    }

    fun Map<String, String>.toKotlinMapString(): String {
        if (this.isEmpty()) return ""
        return entries.joinToString(",\n        ") { (k, v) -> "\"$k\" to \"$v\"" }
    }
    fun List<String>.toKotlinListString(): String {
        if (this.isEmpty()) return ""
        return joinToString(",\n        ") { "\"$it\"" }
    }
}