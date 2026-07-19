import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

    // Depend on the annotations module
    implementation(project(":annotations"))

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("dev.zacsweers.kctfork:core:0.13.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(project(":annotations"))
}
//val compileKotlin: KotlinCompile by tasks
//compileKotlin.compilerOptions {
//    freeCompilerArgs.set(listOf("-Xcontext-parameters"))
//}

tasks.test {
    useJUnitPlatform()
}

mavenPublishing {
    val group = "io.github.kochkaev.kotlin.uniontypes"
    val version = "2.4-1.0.0"
    coordinates(group, "kotlin-union-types-compiler", version)
}