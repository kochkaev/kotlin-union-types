package io.github.kochkaev.kotlin.uniontypes.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateBuildConfig : DefaultTask() {

    @get:Input
    abstract val annotationsVersion: Property<String>

    @get:Input
    abstract val compilerVersion: Property<String>

    @get:Input
    abstract val kotlinVersion: Property<String>

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
                const val ANNOTATIONS_VERSION = "${annotationsVersion.get()}"
                const val COMPILER_VERSION = "${compilerVersion.get()}"
                const val KOTLIN_VERSION = "${kotlinVersion.get()}"
            }
            """.trimIndent()
        )
    }
}