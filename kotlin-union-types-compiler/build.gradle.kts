import io.github.kochkaev.kotlin.uniontypes.build.KotlinSemVer

plugins {
    kotlin("jvm")
    alias(libs.plugins.mavenPublishing)
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // The plugin needs to depend on the Kotlin compiler API.
    // As of Kotlin 2.0, all necessary components (including FIR) are in this artifact.
    compileOnly(libs.kotlinCompilerEmbeddable)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(libs.kctFork)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platformLauncher)
    testImplementation(project(":kotlin-union-types-meta"))
}
//val compileKotlin: KotlinCompile by tasks
//compileKotlin.compilerOptions {
//    freeCompilerArgs.set(listOf("-Xcontext-parameters"))
//}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

fun getLatestCompilerVersion(): String {
    val file = rootProject.file("compatibility.properties")
    if (!file.exists()) return libs.versions.unionTypes.get()
    return file.useLines { lines ->
        lines.map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.substringAfter("=").trim() }
            .maxOfOrNull { KotlinSemVer(it) }?.toString()
            ?: libs.versions.unionTypes.get()
    }
}

val globalVersion = libs.versions.unionTypes.get()
val compilerVersion = getLatestCompilerVersion()

version = libs.versions.unionTypes.get()
group = "io.github.kochkaev.kotlin.uniontypes"

if (compilerVersion != globalVersion) {
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
        name.set("Kotlin Union & Intersection Types Compiler Plugin")
        description.set("A Kotlin Union & Intersection Types FIR K2 Compiler Plugin")
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