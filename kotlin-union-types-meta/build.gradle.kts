import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
//    alias(libs.plugins.mavenPublishing)
    id("io.github.kochkaev.kotlin.uniontypes.build.maven.info")
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

//fun getLatestMetaVersion(): String {
//    val file = rootProject.file("meta.versions")
//    if (!file.exists()) return libs.versions.unionTypes.get()
//    return file.useLines { lines ->
//        lines
//            .map { it.trim() }
//            .filter { it.isNotEmpty() && !it.startsWith("#") }
//            .maxOfOrNull { KotlinSemVer(it) }?.toString()
//            ?: libs.versions.unionTypes.get()
//    }
//}
//val metaVersion = getLatestMetaVersion()
//if (metaVersion != version) {
//    tasks.matching {
//        it.name.startsWith("publish") || it.name.startsWith("sign")
//    }.configureEach {
//        enabled = false
//    }
//}

mavenPublishing {
    pom {
        name.set("Kotlin Union & Intersection Types Meta")
        description.set("Annotations and etc. for Union & Intersection Types FIR K2 Plugin")
    }
}