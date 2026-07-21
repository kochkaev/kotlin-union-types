import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductInfo.LayoutItemKind

plugins {
    alias(libs.plugins.intellij)
    alias(libs.plugins.changelog)
    kotlin("jvm")
}

group = "io.github.kochkaev.kotlin.uniontypes"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":kotlin-union-types-compiler"))
    implementation(project(":kotlin-union-types-annotations"))

    intellijPlatform {
        intellijIdea(libs.versions.idea)
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
//    pluginName.set("kotlin-union-types")
//    platformVersion.set(libs.versions.idea)
//    platformType.set(IntelliJPlatformType.IC)
//
}

tasks {
    runIde {
//        autoReloadPlugins.set(true)
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
