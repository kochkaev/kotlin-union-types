package io.github.kochkaev.kotlin.uniontypes.build.maven

import org.gradle.api.provider.Property

abstract class MavenRepository(val name: String) {
    abstract val url: Property<String>
    abstract val supportZipDeploymentPublish: Property<Boolean>
    abstract val username: Property<String>
    abstract val password: Property<String>
    abstract val bearerAuth: Property<Boolean>

    init {
        supportZipDeploymentPublish.convention(false)
        bearerAuth.convention(false)
    }
}

abstract class MavenCentralRepository: MavenRepository("Maven Central") {
    abstract val automaticRelease: Property<Boolean>

    init {
        automaticRelease.convention(true)
        supportZipDeploymentPublish.convention(true)
        bearerAuth.convention(true)
        url.convention(automaticRelease.map { isAutomatic ->
            val type = if (isAutomatic) "AUTOMATIC" else "USER_MANAGED"
            "https://central.sonatype.com/api/v1/publisher/upload?publishingType=$type"
        })
    }
}