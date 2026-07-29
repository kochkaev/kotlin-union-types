package io.github.kochkaev.kotlin.uniontypes.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import io.github.kochkaev.kotlin.uniontypes.build.utils.*

abstract class UpdateCompatibilityPropertiesTask : DefaultTask() {

    @get:Input
    abstract val featureRelease: Property<Boolean>

    @get:Input
    abstract val globalPluginVersion: Property<String>

    @get:Input @get:Optional
    abstract val targetKotlinVersion: Property<String>

    @get:Input @get:Optional
    abstract val compatibilityMatrix: MapProperty<String, Boolean>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val file = outputFile.get().asFile
        val baseVersion = globalPluginVersion.get()

        val versioning = compatibilityMatrix.orNull
            ?.takeIf { it.isNotEmpty() }
            ?.resolveVersioning(baseVersion)

        logger.lifecycle("""
            |${LogFormatting.BOLD}⏳ Updating compatibility.properties... ${LogFormatting.RESET}
            |   ${LogFormatting.BOLD}Feature Release: ${LogFormatting.RESET}${LogFormatting.YELLOW}${featureRelease.get()}${LogFormatting.RESET}
            |   ${LogFormatting.BOLD}Global Plugin Version: ${LogFormatting.RESET}${LogFormatting.YELLOW}${globalPluginVersion.get()}${LogFormatting.RESET}
            |   ${LogFormatting.BOLD}Target Kotlin Version: ${LogFormatting.RESET}${LogFormatting.YELLOW}${targetKotlinVersion.getOrElse("NONE")}${LogFormatting.RESET}
            |   ${LogFormatting.BOLD}Matrix Breakpoints: ${LogFormatting.RESET}${LogFormatting.YELLOW}${versioning?.keys?.joinToString() ?: "NONE"}${LogFormatting.RESET}
        ${LogFormatting.RESET}""".trimMargin("|"))

        if (!featureRelease.get()) {
            val kotlinVer = targetKotlinVersion.get()
            if (kotlinVer.isNotBlank()) {
                file.appendText("\n$kotlinVer=$baseVersion")
                logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.GREEN}📝 Mapped Kotlin $kotlinVer -> $baseVersion${LogFormatting.RESET}")
            }
        } else {
            if (versioning == null) {
                logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.RED}⚠️ No Kotlin versions found in compatibility matrix results. Skipping update.${LogFormatting.RESET}")
                return
            }

            val minVersion = versioning.keys.first()
            val current = file.loadPropertiesMap()
                .filterKeys { KotlinSemVer(it) < minVersion }
                .entries
                .joinToString("\n") { (key, value) -> "$key=$value" }
            val newLines = versioning.entries
                .joinToString("\n") { (key, value) -> "$key=$value" }

            file.writeText(current + "\n" + newLines)
            logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.GREEN}📝 Re-generated compatibility.properties${LogFormatting.RESET}")
        }
    }
}