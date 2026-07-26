import io.github.kochkaev.kotlin.uniontypes.build.KotlinSemVer
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.mavenPublishing)
}

kotlin {
    // JVM
    jvm()

    // TODO: Test plugin on other platforms

    // JavaScript / Web
    js {
        browser()
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class) wasmJs {
        browser()
    }
    @OptIn(ExperimentalWasmDsl::class) wasmWasi {
        nodejs()
    }

    // Apple (iOS, macOS)
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    @Suppress("DEPRECATION") macosX64()
    macosArm64()

    // Linux
    linuxX64()
    linuxArm64()

    // Windows
    mingwX64()
}

fun getLatestMetaVersion(): String {
    val file = rootProject.file("meta.versions")
    if (!file.exists()) return libs.versions.unionTypes.get()
    return file.useLines { lines ->
        lines
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .maxOfOrNull { KotlinSemVer(it) }?.toString()
            ?: libs.versions.unionTypes.get()
    }
}

val globalVersion = libs.versions.unionTypes.get()
val metaVersion = getLatestMetaVersion()

version = metaVersion
group = "io.github.kochkaev.kotlin.uniontypes"

if (metaVersion != globalVersion) {
    tasks.matching {
        it.name.startsWith("publish") || it.name.startsWith("sign")
    }.configureEach {
        enabled = false
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        name.set("Kotlin Union & Intersection Types Meta")
        description.set("Annotations and etc. for Union & Intersection Types FIR K2 Plugin")
        url.set("https://github.com/kochkaev/kotlin-union-types")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://raw.githubusercontent.com/kochkaev/kotlin-union-types/refs/heads/master/LICENSE")
            }
        }
        developers {
            developer {
                id.set("kochkaev")
                name.set("Dmitrii Kochkaev")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/kochkaev/kotlin-union-types.git")
                developerConnection.set("scm:git:ssh://github.com/kochkaev/kotlin-union-types.git")
            url.set("https://github.com/kochkaev/kotlin-union-types")
        }
    }
}