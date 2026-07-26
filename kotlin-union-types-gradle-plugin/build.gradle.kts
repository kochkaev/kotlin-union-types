import io.github.kochkaev.kotlin.uniontypes.build.GenerateBuildConfig
import java.util.Properties

plugins {
    kotlin("jvm")
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.mavenPublishing)
    `java-gradle-plugin`
}

val unionTypesVersion = libs.versions.unionTypes.get()

group = "io.github.kochkaev.kotlin.uniontypes"
version = unionTypesVersion

fun loadPropertiesList(fileName: String): List<String> {
    val file = rootProject.file(fileName)
    if (!file.exists()) return emptyList()
    return file.useLines { lines ->
        lines.map { line -> line.trim() }
             .filter { it.isNotEmpty() && !it.startsWith("#") }
             .toList()
    }
}
fun loadPropertiesMap(fileName: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    val file = rootProject.file(fileName)
    if (file.exists()) {
        val props = Properties()
        file.inputStream().use { props.load(it) }
        props.forEach { (k, v) -> map[k.toString()] = v.toString() }
    }
    return map
}

val generateBuildConfig = tasks.register<GenerateBuildConfig>("generateBuildConfig") {
    description = "Generates BuildConfig.kt for the Gradle plugin"

    latestSupportedKotlinVersion.set(libs.versions.latestSupportedKotlin.get())

    compatibilityMap.set(loadPropertiesMap("compatibility.properties"))
    metaList.set(loadPropertiesList("meta.versions"))

    outputDirectory.set(layout.buildDirectory.dir("generated/source/buildconfig/main/kotlin"))
}

sourceSets {
    main {
        kotlin.srcDir(generateBuildConfig)
    }
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(libs.kotlinGradlePlugin)
    compileOnly(libs.kotlinGradlePluginApi)
}

gradlePlugin {
    website.set("https://github.com/kochkaev/kotlin-union-types")
    vcsUrl.set("https://github.com/kochkaev/kotlin-union-types.git")

    plugins {
        create("unionTypesPlugin") {
            id = "io.github.kochkaev.kotlin.uniontypes"
            implementationClass = "io.github.kochkaev.kotlin.uniontypes.gradle.UnionTypesGradlePlugin"
            displayName = "Kotlin Union & Intersection Types Compiler Plugin"
            description = "A Kotlin compiler plugin (K2/FIR) that introduces support for Union and Intersection Types via annotations."
            tags.set(listOf("kotlin", "compiler-plugin", "union-types", "k2"))
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        name.set("Kotlin Union & Intersection Types Gradle Plugin")
        description.set("A Gradle Plugin that configures Kotlin Union & Intersection Types FIR K2 Plugin")
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