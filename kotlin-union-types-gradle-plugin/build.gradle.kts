import io.github.kochkaev.kotlin.uniontypes.build.GenerateBuildConfig
import io.github.kochkaev.kotlin.uniontypes.build.loadPropertiesMap
import io.github.kochkaev.kotlin.uniontypes.build.loadPropertiesList

plugins {
    kotlin("jvm")
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.mavenPublishing)
    `java-gradle-plugin`
    id("uniontypes.maven-info")
}

val generateBuildConfig = tasks.register<GenerateBuildConfig>("generateBuildConfig") {
    description = "Generates BuildConfig.kt for the Gradle plugin"

    latestSupportedKotlinVersion.set(libs.versions.latestSupportedKotlin.get())
    compatibilityMap.set(project.rootDir.resolve("compatibility.properties").loadPropertiesMap())
    metaList.set(project.rootDir.resolve("meta.versions").loadPropertiesList())
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
    pom {
        name.set("Kotlin Union & Intersection Types Gradle Plugin")
        description.set("A Gradle Plugin that configures Kotlin Union & Intersection Types FIR K2 Plugin")
    }
}