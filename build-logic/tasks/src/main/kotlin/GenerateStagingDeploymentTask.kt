package io.github.kochkaev.kotlin.uniontypes.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import io.github.kochkaev.kotlin.uniontypes.build.utils.*

abstract class GenerateStagingDeploymentTask : DefaultTask() {

    @get:Input
    abstract val globalPluginVersion: Property<String>

    @get:Input
    abstract val publishMeta: Property<Boolean>

    @get:OutputDirectory
    abstract val stagingDir: DirectoryProperty

    @get:Internal
    abstract val rootDir: DirectoryProperty

    @get:Input
    abstract val compatibilityMatrix: MapProperty<String, Boolean>

    @TaskAction
    fun generate() {
        val rootDir = rootDir.get().asFile
        val stagingDir = stagingDir.get().asFile
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        val currentRelease = globalPluginVersion.get()
        val versioning = compatibilityMatrix.get().resolveVersioning(currentRelease)

        logger.lifecycle("${LogFormatting.BOLD}🚀 Generating Local Deployment for release $currentRelease in ${stagingDir.absolutePath}${LogFormatting.RESET}")

        versioning.forEach { (kotlinVersion, compilerVersion) ->
            logger.lifecycle("${LogFormatting.BOLD}🔨 Assembling Compiler Plugin $compilerVersion (Kotlin $kotlinVersion)${LogFormatting.RESET}")
            rootDir.runGradle(
                task = ":kotlin-union-types-compiler:publishAllPublicationsToStagingRepository",
                arguments = listOfNotNull(
                    "--rerun-tasks",
                    "-PmainKotlinVersion=$kotlinVersion",
                    "-PoverrideVersion=$compilerVersion",
                ),
                logPrefix = "Assembling Compiler Plugin $compilerVersion",
                logger = logger,
            )
        }

        logger.lifecycle("${LogFormatting.BOLD}🐘 Assembling Gradle Plugin $currentRelease...${LogFormatting.RESET}")
        rootDir.runGradle(
            task = ":kotlin-union-types-gradle-plugin:publishAllPublicationsToStagingRepository",
            arguments = listOfNotNull(
                "--rerun-tasks",
                "-PoverrideVersion=$currentRelease",
            ),
            logPrefix = "Assembling Gradle Plugin $currentRelease",
            logger = logger,
        )

        if (publishMeta.get()) {
            logger.lifecycle("${LogFormatting.BOLD}🏷️ Assembling Meta Module...${LogFormatting.RESET}")
            rootDir.runGradle(
                task = ":kotlin-union-types-meta:publishAllPublicationsToStagingRepository",
                arguments = listOfNotNull(
                    "--rerun-tasks",
                    "-PoverrideVersion=$currentRelease",
                ),
                logPrefix = "Assembling Meta $currentRelease",
                logger = logger,
            )
        }

        logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.GREEN}✅ All artifacts successfully staged in ${stagingDir.absolutePath}${LogFormatting.RESET}")
    }
}