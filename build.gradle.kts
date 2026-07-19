// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // Apply the Kotlin plugin to all subprojects
    alias(libs.plugins.kotlin.jvm) apply false
    // Apply the KSP plugin to all subprojects that need it
    alias(libs.plugins.ksp) apply false
}
