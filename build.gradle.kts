
plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    `maven-publish`
}

group = "fr.smolder.hytale"
version = "0.0.4"

gradlePlugin {
    plugins {
        create("hytaleDev").apply {
            id = "fr.smolder.hytale.dev"
            implementationClass = "fr.smolder.hytale.gradle.HytaleDevPlugin"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
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
