import io.github.kochkaev.kotlin.uniontypes.build.GenerateBuildConfig

plugins {
    kotlin("jvm")
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.mavenPublishing)
    `java-gradle-plugin`
}

val unionTypesVersion = libs.versions.unionTypes.get()

group = "io.github.kochkaev.kotlin.uniontypes"
version = unionTypesVersion

val generateBuildConfig = tasks.register<GenerateBuildConfig>("generateBuildConfig") {
    description = "Generates BuildConfig.kt for the Gradle plugin"
    annotationsVersion.set(unionTypesVersion)
    compilerVersion.set(unionTypesVersion)
    kotlinVersion.set(libs.versions.kotlin.get())
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