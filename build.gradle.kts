import io.github.kochkaev.kotlin.uniontypes.build.*

val mavenPublishingPlugin = libs.plugins.mavenPublishing
val globalPluginVersion = libs.versions.unionTypes
version = project.findProperty("overrideVersion") as? String ?: globalPluginVersion.get()
group = "io.github.kochkaev.kotlin.uniontypes"

val compatibilityPropertiesFile = file("compatibility.properties")
val compatibilityMatrixFile = project.layout.buildDirectory.file("tmp/compatibility_matrix.txt")
val stagingDeploymentDir = project.layout.buildDirectory.dir("maven-staging")

val generateCompatibilityMatrix = tasks.register <CompatibilityMatrixTask>("generateCompatibilityMatrix") {
    description = "Run tests on each Kotlin version in project bounds to generate compatibility matrix"
    kotlinVersions = getKotlinVersionsFromMaven(libs.versions.kotlin.get(), libs.versions.latestSupportedKotlin.get())
    rootDir = project.rootDir
    compatibilityOutputFile = compatibilityMatrixFile
}

val checkPortCompatibility = tasks.register<CheckKotlinPortCompatibilityTask>("checkPortCompatibility") {
    description = "Checks compatibility of Kotlin port"
    group = "verification"
    targetKotlinVersion = project.findProperty("targetKotlinVersion")?.toString() ?: ""
    latestSupportedKotlinVersion = libs.versions.latestSupportedKotlin
    outputFile = project.layout.buildDirectory.file("port_result.txt")
    rootDir = project.rootDir
}
val addPortVersionCompatibilityProperties = tasks.register<UpdateCompatibilityPropertiesTask>("addPortVersionCompatibilityProperties") {
    description = "Adds port version to compatibility properties"
    group = "build setup"
    globalPluginVersion = libs.versions.unionTypes
    outputFile = compatibilityPropertiesFile

    featureRelease = false
    targetKotlinVersion = libs.versions.latestSupportedKotlin
}
val regenerateCompatibilityProperties = tasks.register<UpdateCompatibilityPropertiesTask>("regenerateCompatibilityProperties") {
    description = "Regenerates compatibility properties"
    group = "build setup"
    globalPluginVersion = libs.versions.unionTypes
    outputFile = compatibilityPropertiesFile

    featureRelease = true
    compatibilityMatrix = generateCompatibilityMatrix
        .flatMap { it.compatibilityOutputFile }
        .map { it.asFile.loadCompatibilityMatrix() }
}

val generateStagingDeployment = tasks.register<GenerateStagingDeploymentTask>("generateStagingDeployment") {
    description = "Generates staging deployment"
    group = "publishing"
    globalPluginVersion = libs.versions.unionTypes
    publishMeta = true
    stagingDir = stagingDeploymentDir
    compatibilityMatrix = generateCompatibilityMatrix
        .flatMap { it.compatibilityOutputFile }
        .map { it.asFile.loadCompatibilityMatrix() }
    rootDir = project.rootDir
}