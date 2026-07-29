plugins {
    kotlin("jvm")
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.mavenPublishing)
    `java-gradle-plugin`
    id("io.github.kochkaev.kotlin.uniontypes.build.maven.info")
    id("io.github.kochkaev.kotlin.uniontypes.build.gradlePluginGen.plugin")
}

gradlePluginGen {
    latestSupportedKotlinVersion = libs.versions.latestSupportedKotlin
    compatibilityPropertiesFile = project.rootDir.resolve("compatibility.properties")
    metaVersionsFile = project.rootDir.resolve("meta.versions")
    outputDirectory = layout.buildDirectory.dir("generated/source/buildconfig/main/kotlin")
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