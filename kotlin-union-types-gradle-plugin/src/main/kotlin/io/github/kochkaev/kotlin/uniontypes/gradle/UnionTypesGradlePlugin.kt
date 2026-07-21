package io.github.kochkaev.kotlin.uniontypes.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

class UnionTypesGradlePlugin : KotlinCompilerPluginSupportPlugin {

    override fun apply(target: Project) {
        val annotationDependency = "io.github.kochkaev.kotlin.uniontypes:kotlin-union-types-annotations:${BuildConfig.ANNOTATIONS_VERSION}"

        target.plugins.withType(KotlinBasePlugin::class.java) {
            val kotlinExtension = target.extensions.getByType(KotlinProjectExtension::class.java)

            kotlinExtension.sourceSets.configureEach { sourceSet ->
                if (sourceSet.name == "commonMain" || sourceSet.name == "main") {
                    sourceSet.dependencies {
                        implementation(annotationDependency)
                    }
                }
            }
        }
    }

    override fun getCompilerPluginId(): String = "io.github.kochkaev.kotlin.uniontypes"

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = "io.github.kochkaev.kotlin.uniontypes",
        artifactId = "kotlin-union-types-compiler",
        version = BuildConfig.COMPILER_VERSION
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>) = true

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.project

        val projectKotlinVersion = project.getKotlinPluginVersion()

        val requiredKotlin = BuildConfig.KOTLIN_VERSION

        if (projectKotlinVersion != requiredKotlin) {
            project.logger.warn(
                "Union & Intersection Types Compiler Plugin requires Kotlin $requiredKotlin, " +
                "but you are using $projectKotlinVersion. Compilation may fail."
            )
        }

        return project.provider { emptyList() }
    }
}