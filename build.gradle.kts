
plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = "fr.smolder.hytale"
version = "0.0.7"

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
}

gradlePlugin {
    website.set("https://github.com/GhostRider584/hytale-gradle-plugin")
    vcsUrl.set("https://github.com/GhostRider584/hytale-gradle-plugin.git")
    plugins {
        register("hytaleDev") {
            id = "fr.smolder.hytale.dev"
            displayName = "Hytale Development Plugin"
            description = "A Gradle plugin to streamline Hytale plugin development."
            tags.set(listOf("hytale", "hytale-plugin", "hytale-mod", "hytale-server", "gradle-plugin", "development"))
            implementationClass = "fr.smolder.hytale.gradle.HytaleDevPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            name = "Smolder"
            url = uri("https://repo.smolder.fr/public/")
            credentials {
                username = project.findProperty("smolderUsername") as String?
                password = project.findProperty("smolderPassword") as String?
            }
        }
    }
}
