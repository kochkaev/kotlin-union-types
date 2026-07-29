plugins {
    id("com.vanniktech.maven.publish")
}

group = rootProject.group
version = rootProject.version

mavenPublishing {
    signAllPublications()

    pom {
        url = "https://github.com/kochkaev/kotlin-union-types"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://raw.githubusercontent.com/kochkaev/kotlin-union-types/refs/heads/master/LICENSE"
            }
        }
        developers {
            developer {
                id = "kochkaev"
                name = "Dmitrii Kochkaev"
            }
        }
        scm {
            connection = "scm:git:git://github.com/kochkaev/kotlin-union-types.git"
            developerConnection = "scm:git:ssh://github.com/kochkaev/kotlin-union-types.git"
            url = "https://github.com/kochkaev/kotlin-union-types"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "Staging"
            url = uri(rootProject.layout.buildDirectory.dir("maven-staging"))
        }
    }
}
