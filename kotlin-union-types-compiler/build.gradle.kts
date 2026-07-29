
plugins {
    kotlin("jvm")
    alias(libs.plugins.mavenPublishing)
    id("io.github.kochkaev.kotlin.uniontypes.build.maven.info")
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

val mainConfigurations = setOf(
    "compileClasspath",
    "runtimeClasspath",
    "apiElements",
    "runtimeElements"
)
val customMainKotlinVersion = project.findProperty("mainKotlinVersion") as? String
if (!customMainKotlinVersion.isNullOrBlank()) {
    val targetDependency = libs.kotlinCompilerEmbeddable.get()
    configurations.matching { it.name in mainConfigurations } .configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("${targetDependency.group}:${targetDependency.name}"))
                .using(module("${targetDependency.group}:${targetDependency.name}:$customMainKotlinVersion"))
                .because("Forcing Kotlin version for main runtime classpath")
        }
    }
}
val testConfigurations = setOf(
    "testCompileClasspath",
    "testRuntimeClasspath"
)
val customTestKotlinVersion = project.findProperty("testKotlinVersion") as? String
if (!customTestKotlinVersion.isNullOrBlank()) {
    val targetDependency = libs.kotlinCompilerEmbeddable.get()
    configurations.matching { it.name in testConfigurations } .configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("${targetDependency.group}:${targetDependency.name}"))
                .using(module("${targetDependency.group}:${targetDependency.name}:$customTestKotlinVersion"))
                .because("Forcing Kotlin version for test runtime classpath")
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

//fun getLatestCompilerVersion(): String {
//    val file = rootProject.file("compatibility.properties")
//    if (!file.exists()) return libs.versions.unionTypes.get()
//    return file.loadPropertiesMap()
//        .maxOfOrNull { KotlinSemVer(it.value) }
//        ?.toString()
//        ?: libs.versions.unionTypes.get()
//    }
//
//val compilerVersion = getLatestCompilerVersion()
//if (compilerVersion != version) {
//    tasks.matching {
//        it.name.startsWith("publish") || it.name.startsWith("sign")
//    }.configureEach {
//        enabled = false
//    }
//}

mavenPublishing {
    pom {
        name = "Kotlin Union & Intersection Types Compiler Plugin"
        description = "A Kotlin Union & Intersection Types FIR K2 Compiler Plugin"
    }
}