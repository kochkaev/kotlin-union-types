package io.github.kochkaev.kotlin.uniontypes.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class CompatibilityMatrixTask: DefaultTask() {

    @get:Input
    abstract val kotlinVersions: ListProperty<String>

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @get:OutputFile
    abstract val compatibilityOutputFile: RegularFileProperty

    @TaskAction
    fun runMatrix() {
        val versions = kotlinVersions.get().map { KotlinSemVer(it) }.sorted()

        if (versions.isEmpty()) {
            logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.RED}⚠️ No Kotlin versions found in range${LogFormatting.RESET}")
            error("No Kotlin versions found in range")
        }

        logger.lifecycle("""
            |${LogFormatting.BOLD}⏳ Starting compatibility matrix processing... ${LogFormatting.RESET}
            |   ${LogFormatting.BOLD}Kotlin versions to test: ${LogFormatting.RESET}${LogFormatting.YELLOW}${versions.joinToString()}${LogFormatting.RESET}
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