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
    testImplementation(project(":kotlin-union-types-annotations"))
}
//val compileKotlin: KotlinCompile by tasks
//compileKotlin.compilerOptions {
//    freeCompilerArgs.set(listOf("-Xcontext-parameters"))
//}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

version = libs.versions.unionTypes.get()
group = "io.github.kochkaev.kotlin.uniontypes"

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