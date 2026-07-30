plugins {
    `kotlin-dsl`
}

dependencies {
    api(project(":utils"))
    implementation(libs.build.mavenPublishing)
    implementation(libs.kotlinGradlePlugin)
    implementation(libs.build.okHttp)
}