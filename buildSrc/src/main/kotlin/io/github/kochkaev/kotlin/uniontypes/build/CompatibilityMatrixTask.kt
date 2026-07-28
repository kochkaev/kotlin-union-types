package io.github.kochkaev.kotlin.uniontypes.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class CompatibilityMatrixTask: DefaultTask() {

    @get:Input
    abstract val minKotlinVersion: Property<String>

    @get:Input
    abstract val maxKotlinVersion: Property<String>

    @get:Input
    abstract val rootDir: DirectoryProperty

    @get:OutputFile
    abstract val compatibilityOutputFile: RegularFileProperty

    @TaskAction
    fun runMatrix() {
        val rawVersions = getKotlinVersionsFromMaven(minKotlinVersion.get(), maxKotlinVersion.get())
        val versions = rawVersions.map { KotlinSemVer(it) }.sorted()

        if (versions.isEmpty()) {
            logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.RED}⚠️ No Kotlin versions found in range${LogFormatting.RESET}")
            error("No Kotlin versions found in range")
        }

        logger.lifecycle("""
            |${LogFormatting.BOLD}⏳ Starting compatibility matrix processing... ${LogFormatting.RESET}
            |   ${LogFormatting.BOLD}Kotlin versions to test: ${LogFormatting.RESET}${LogFormatting.YELLOW}${versions.joinToString()}${LogFormatting.RESET}
            |   ${LogFormatting.BOLD}Min Kotlin version: ${LogFormatting.RESET}${LogFormatting.YELLOW}${minKotlinVersion.get()}${LogFormatting.RESET}
            |   ${LogFormatting.BOLD}Max Kotlin version: ${LogFormatting.RESET}${LogFormatting.YELLOW}${maxKotlinVersion.get()}${LogFormatting.RESET}
        ${LogFormatting.RESET}""".trimMargin("|"))

        var currentMainVersion = versions.first()
        val compatibilityResults = mutableMapOf<String, Boolean>()

        versions.forEachIndexed { i, targetVersion ->
            logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.YELLOW}🔄 Testing Kotlin ${targetVersion}...${LogFormatting.RESET}")

            val rootDir = rootDir.get().asFile
            val compatibility = rootDir.kotlinCompatibleTest(targetVersion.toString(), currentMainVersion.toString(), logger)

            when (compatibility) {
                Compatibility.ABI_COMPATIBLE -> {
                    compatibilityResults[targetVersion.toString()] = i == 0
                }
                Compatibility.ABI_BROKEN -> {
                    logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.GREEN}✅ Shifting compilation baseline to $targetVersion${LogFormatting.RESET}")
                    currentMainVersion = targetVersion
                    compatibilityResults[targetVersion.toString()] = true
                }
                Compatibility.INCOMPATIBLE -> {
                    logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.RED}❌ Source code failed to compile on Kotlin ${targetVersion}!${LogFormatting.RESET}")
                    error("Source code failed to compile on Kotlin ${targetVersion}!")
                }
            }
        }
        compatibilityOutputFile.asFile.get()
            .writeText(compatibilityResults.entries.joinToString("\n") { "${it.key}=${it.value}" })
        logger.lifecycle("${LogFormatting.BOLD}✅ Compatibility matrix processing finished.${LogFormatting.RESET}")
        logger.debug("Compatibility matrix processing results: {}", compatibilityResults)
    }
}