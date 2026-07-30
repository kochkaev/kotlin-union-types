package io.github.kochkaev.kotlin.uniontypes.build.maven

import org.gradle.kotlin.dsl.register
import org.gradle.api.tasks.bundling.Zip

val extension = extensions.create<PublishExtension>("unionTypesPublish")

val zipTask = tasks.register<Zip>("zipMavenStaging") {
    description = "Zips the Maven staging directory"
    from(extension.stagingDirectory)
    archiveFileName = "maven-deployment-bundle.zip"
    destinationDirectory = layout.buildDirectory.dir("maven-bundle")
}

val publishAllTask = tasks.register("unionTypesPublish") {
    group = "publishing"
    description = "Publishes staging repository to all declared repositories."
}

project.afterEvaluate {
    val stagingDir = extension.stagingDirectory.get().asFile
    val publisher = StagingPublisher(
        logger = logger,
        stagingDir = stagingDir,
        bundleZipFile = zipTask.get().archiveFile.get().asFile
    )

    extension.publishRepositories.forEach { repo ->
        val taskName = "publishTo${repo.name.replaceFirstChar { it.uppercase() }}"

        val repoTask = tasks.register(taskName) {
            group = "publishing"
            description = "Publishes staging repository to ${repo.name}."

            if (repo.supportZipDeploymentPublish.getOrElse(false)) {
                dependsOn(zipTask)
            }

            doLast {
                publisher.publishToMaven(repo)
            }
        }

        publishAllTask.configure { dependsOn(repoTask) }
    }
}