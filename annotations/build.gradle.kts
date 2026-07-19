plugins {
    kotlin("jvm")
    alias(libs.plugins.mavenPublishing)
}

mavenPublishing {
    coordinates("io.github.kochkaev.kotlin.uniontypes", "kotlin-union-types-annotations", "0.0")
}