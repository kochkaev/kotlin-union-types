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
    macosX64() // Deprecated
    macosArm64()

    // Linux
    linuxX64()
    linuxArm64()

    // Windows
    mingwX64()
}

version = libs.versions.unionTypes.get()
group = "io.github.kochkaev.kotlin.uniontypes"

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        name.set("Kotlin Union & Intersection Types Annotations")
        description.set("Annotations for Union & Intersection Types FIR K2 Plugin")
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