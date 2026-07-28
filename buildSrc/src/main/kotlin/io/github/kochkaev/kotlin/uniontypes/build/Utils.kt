package io.github.kochkaev.kotlin.uniontypes.build

import org.gradle.api.logging.Logger
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationType
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.util.Properties
import kotlin.collections.component1
import kotlin.collections.component2

object LogFormatting {
    const val RESET = "\u001B[0m"
    const val BOLD = "\u001B[1m"
    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
}

fun getKotlinVersionsFromMaven(minVersionStr: String, maxVersionStr: String): List<String> {
    val min = KotlinSemVer(minVersionStr)
    val max = KotlinSemVer(maxVersionStr)

    val url = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/maven-metadata.xml"
    val xml = URI.create(url).toURL().readText()

    // Регулярное выражение для поиска всех тегов <version>...</version>
    val regex = "<version>(.+?)</version>".toRegex()

    return regex.findAll(xml)
        .map { it.groupValues[1] }
        .mapNotNull {
            try { KotlinSemVer(it) } catch (_: Exception) { null }
        }
        // Фильтруем: берем только те, что >= min и <= max
        .filter { it in min..max }
        .map { it.toString() }
        .distinct()
        .sortedBy { KotlinSemVer(it) }
        .toList()
}

internal fun File.runGradle(
    task: String,
    arguments: List<String>,
    logPrefix: String = "",
    logger: Logger,
): Boolean {
    return try {
        GradleConnector.newConnector()
            .forProjectDirectory(this)
            .connect()
            .use { connection ->
                val launcher = connection.newBuild()
                launcher.forTasks(task)

                launcher.withArguments(arguments)

                launcher.setJvmArguments("-Dorg.gradle.daemon=true", "-Xmx1g")
                launcher.setStandardOutput(OutputStream.nullOutputStream())
                launcher.setStandardError(OutputStream.nullOutputStream())
                launcher.setColorOutput(true)

                var hasError = false
                logger.lifecycle("\n${LogFormatting.BOLD}[$logPrefix]: ${LogFormatting.RESET}RUNNING...")
                launcher.addProgressListener({ event ->
                    val description = event.displayName
                    val result = if (event is FinishEvent) event.result else null
                    hasError = hasError || result is FailureResult
                    if (result is FailureResult)
                        result.failures.map { it.message ?: it.description }.forEach { logger.debug(it) }
                    logger.lifecycle("${LogFormatting.BOLD}[$logPrefix]: ${LogFormatting.RESET}$description")
                }, OperationType.TASK, OperationType.TEST_OUTPUT)

                launcher.run()
                logger.lifecycle("")
            }
        true
    } catch (_: Exception) {
        logger.lifecycle("")
        false
    }
}

enum class Compatibility {
    ABI_COMPATIBLE,
    ABI_BROKEN,
    INCOMPATIBLE
}

internal fun File.kotlinCompatibleTest(
    targetVersion: String,
    latestSupportedVersion: String,
    logger: Logger,
): Compatibility {
    logger.lifecycle("${LogFormatting.BOLD}🔄 Step 1: Checking ABI compatibility (compile=$latestSupportedVersion, test=$targetVersion)${LogFormatting.RESET}")
    val isAbiCompatible = runGradle(
        task = ":kotlin-union-types-compiler:test",
        arguments = listOf(
            "-PtestKotlinVersion=$targetVersion",
            "-PmainKotlinVersion=$latestSupportedVersion",
            "--rerun-tasks",
        ),
        logPrefix = "Testing on Kotlin $latestSupportedVersion -> $targetVersion",
        logger = logger,
    )

    if (isAbiCompatible) {
        logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.GREEN}✅ Full compatibility. ABI is intact.${LogFormatting.RESET}")
        return Compatibility.ABI_COMPATIBLE
    }

    logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.YELLOW}⚠️ ABI broken. Step 2: Checking Source compatibility (compile=$targetVersion, test=$targetVersion)${LogFormatting.RESET}")
    val isSourceCompatible = runGradle(
        task = ":kotlin-union-types-compiler:test",
        arguments = listOf(
            "-PtestKotlinVersion=$targetVersion",
            "-PmainKotlinVersion=$targetVersion",
            "--rerun-tasks",
        ),
        logPrefix = "Testing on Kotlin $targetVersion -> $targetVersion",
        logger = logger,
    )

    if (isSourceCompatible) {
        logger.lifecycle("${LogFormatting.BOLD}${LogFormatting.GREEN}✅ Source compatible, but ABI broken.${LogFormatting.RESET}")
        return Compatibility.ABI_BROKEN
    } else {
        logger.error("${LogFormatting.BOLD}${LogFormatting.RED}❌ Source incompatible. Compilation failed.${LogFormatting.RESET}")
        return Compatibility.INCOMPATIBLE
    }
}

fun File.loadPropertiesMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    if (exists()) {
        val props = Properties()
        inputStream().use { props.load(it) }
        props.forEach { (k, v) -> map[k.toString()] = v.toString() }
    }
    return map
}
fun File.loadPropertiesList(): List<String> {
    val list = mutableListOf<String>()
    if (exists()) {
        useLines { lines ->
            lines.map { line -> line.trim() }
                 .filter { it.isNotEmpty() && !it.startsWith("#") }
                 .forEach { list.add(it) }
        }
    }
    return list
}