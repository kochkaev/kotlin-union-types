package io.github.kochkaev.kotlin.uniontypes.build.maven

import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class PublishExtension @Inject constructor(
    val objects: ObjectFactory
) {
    val stagingDirectory: DirectoryProperty = objects.directoryProperty()
    val publishRepositories = mutableListOf<MavenRepository>()

    fun mavenCentral(action: Action<MavenCentralRepository>) {
        val repo = objects.newInstance(MavenCentralRepository::class.java)
        action.execute(repo)
        publishRepositories.add(repo)
    }

    fun maven(name: String, action: Action<MavenRepository>) {
        val repo = objects.newInstance(MavenRepository::class.java, name)
        action.execute(repo)
        publishRepositories.add(repo)
    }
}