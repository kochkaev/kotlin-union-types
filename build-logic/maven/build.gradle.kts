plugins {
    `kotlin-dsl`
}

dependencies {
    api(project(":utils"))
    implementation(libs.build.mavenPublishing)
}