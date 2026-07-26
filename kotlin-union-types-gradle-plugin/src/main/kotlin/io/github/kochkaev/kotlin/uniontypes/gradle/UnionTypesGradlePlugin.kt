package io.github.kochkaev.kotlin.uniontypes.gradle

import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

@Suppress("unused")
class UnionTypesGradlePlugin : KotlinCompilerPluginSupportPlugin {

    private var targetProject: Project? = null

    override fun apply(target: Project) {
        targetProject = target

        target.plugins.withType(KotlinBasePlugin::class.java) {
            val kotlinVersion = target.getKotlinPluginVersion()

            val compilerPluginVersion = PluginVersionResolver.resolveCompilerPluginVersion(kotlinVersion, target.logger)
            val annotationVersion = PluginVersionResolver.resolveMetaVersion(compilerPluginVersion)

            val annotationDependency = "io.github.kochkaev.kotlin.uniontypes:kotlin-union-types-meta:$annotationVersion"

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

    override fun getPluginArtifact(): SubpluginArtifact {
        val project = targetProject ?: error("Plugin not applied to any project")
        val kotlinVersion = project.getKotlinPluginVersion()

        val resolvedPluginVersion = PluginVersionResolver.resolveCompilerPluginVersion(kotlinVersion, null)

        return SubpluginArtifact(
            groupId = "io.github.kochkaev.kotlin.uniontypes",
            artifactId = "kotlin-union-types-compiler",
            version = resolvedPluginVersion
        )
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>) = true

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        return kotlinCompilation.project.provider { emptyList() }
    }
}

internal data class KotlinSemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val suffix: String?
) : Comparable<KotlinSemVer> {

    companion object {
        operator fun invoke(version: String): KotlinSemVer {
            val parts = version.substringBefore("-").split(".")
            val suffix = if (version.contains("-")) version.substringAfter("-") else null

            return KotlinSemVer(
                major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                patch = parts.getOrNull(2)?.toIntOrNull() ?: 0,
                suffix = suffix
            )
        }
    }

    override operator fun compareTo(other: KotlinSemVer): Int {
        if (this.major != other.major) return this.major.compareTo(other.major)
        if (this.minor != other.minor) return this.minor.compareTo(other.minor)
        if (this.patch != other.patch) return this.patch.compareTo(other.patch)

        if (this.suffix == null && other.suffix == null) return 0
        if (this.suffix == null) return 1
        if (other.suffix == null) return -1

        return this.suffix.compareTo(other.suffix, ignoreCase = true)
    }

    override fun toString(): String {
        val base = "${major}.${minor}.${patch}"
        return if (suffix != null) "$base-$suffix" else base
    }
}

internal object PluginVersionResolver {

    private val sortedCompatibilityList: List<Pair<KotlinSemVer, String>> by lazy {
        BuildConfig.COMPATIBILITY_MAP
            .map { KotlinSemVer(it.key) to it.value }
            .sortedBy { it.first }
    }

    private val sortedMetaList: List<Pair<KotlinSemVer, String>> by lazy {
        BuildConfig.META_LIST
            .map { KotlinSemVer(it) to it }
            .sortedBy { it.first }
    }

    fun resolveCompilerPluginVersion(userKotlinVersionStr: String, logger: Logger? = null): String {
        if (sortedCompatibilityList.isEmpty()) {
            error("COMPATIBILITY_MAP is empty. Ensure compatibility.properties is populated.")
        }

        val userVersion = KotlinSemVer(userKotlinVersionStr)
        val matchedEntry = sortedCompatibilityList.findLast { it.first <= userVersion }

        if (matchedEntry == null) {
            val minSupported = sortedCompatibilityList.first().first
            logger?.error(
                "[kotlin-union-types] Kotlin version $userKotlinVersionStr is not supported. " +
                "Minimum supported version is $minSupported. Compilation may fail."
            )
            return sortedCompatibilityList.first().second
        }

        if (userVersion > KotlinSemVer(BuildConfig.LATEST_SUPPORTED_KOTLIN_VERSION)) {
            logger?.warn(
                "[kotlin-union-types] Kotlin version $userKotlinVersionStr is newer than the latest explicitly " +
                "supported version (${BuildConfig.LATEST_SUPPORTED_KOTLIN_VERSION}). Proceeding with plugin version ${matchedEntry.second}, " +
                "but compatibility is not guaranteed."
            )
        }

        return matchedEntry.second
    }

    fun resolveMetaVersion(compilerPluginVersionStr: String): String {
        if (sortedMetaList.isEmpty()) {
            error("META_LIST is empty. Ensure annotations.properties is populated.")
        }

        val pluginVersion = KotlinSemVer(compilerPluginVersionStr)
        val matchedEntry = sortedMetaList.findLast { it.first <= pluginVersion }

        if (matchedEntry == null) {
            return sortedMetaList.first().second
        }

        return matchedEntry.second
    }
}