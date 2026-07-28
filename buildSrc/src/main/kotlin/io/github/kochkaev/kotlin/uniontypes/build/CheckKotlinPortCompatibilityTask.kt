package io.github.kochkaev.kotlin.uniontypes.build

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class CheckKotlinPortCompatibilityTask: DefaultTask() {

    @get:Input
    abstract val targetKotlinVersion: Property<String> // Version to check

    @get:Input
    abstract val latestSupportedKotlinVersion: Property<String> // Latest tested version (from libs.versions.toml)

    @get:Input
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val rootDir: DirectoryProperty

    @TaskAction
    fun checkCompatibility() {
        val target = targetKotlinVersion.get()
        val latest = latestSupportedKotlinVersion.get()
        val resultFile = outputFile.asFile.get()
        resultFile.parentFile.mkdirs()

        val rootDir = rootDir.get().asFile
        val compatibility = rootDir.kotlinCompatibleTest(target, latest, logger)

        when (compatibility) {
            Compatibility.ABI_COMPATIBLE -> {
                resultFile.writeText("SUCCESS_ABI_COMPATIBLE")
                return
            }
            Compatibility.ABI_BROKEN -> {
                logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.GREEN}✅ Requires new compiler plugin publication.${LogFormatting.RESET}")
                resultFile.writeText("SUCCESS_ABI_BROKEN")
            }
            Compatibility.INCOMPATIBLE -> {
                resultFile.writeText("FAILURE_INCOMPATIBLE")
                logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.RED}❌ Source code is incompatible with Kotlin $target. Manual intervention required.${LogFormatting.RESET}")
                error("Source code is incompatible with Kotlin $target. Manual intervention required.")
            }
        }
    }
}