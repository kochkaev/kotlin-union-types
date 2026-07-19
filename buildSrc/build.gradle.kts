plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    // Add a dependency on the Kotlin Gradle plugin, so that convention plugins can apply it.
    implementation(libs.kotlinGradlePlugin)
}
