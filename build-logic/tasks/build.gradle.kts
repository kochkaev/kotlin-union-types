plugins {
    `kotlin-dsl`
}

dependencies {
    api(project(":utils"))
    implementation(libs.kotlinGradlePlugin)
}