# Hytale Gradle Plugin
![Java](https://img.shields.io/badge/Java-orange?style=for-the-badge&logo=openjdk&logoColor=white)
![Hytale](https://img.shields.io/badge/Hytale-FF7175?style=for-the-badge&logo=anycubic&logoColor=white)
![Version](https://img.shields.io/badge/version-0.0.1-248cd6?labelColor=&style=for-the-badge)
![License: MIT](https://img.shields.io/badge/License-MIT-7267db.svg?style=for-the-badge)

A Gradle plugin to streamline Hytale server development.

## Usage

### 1. Configure Plugin Repository
Since this plugin is hosted on the Smolder repository, you must tell Gradle where to find it.
Add this to your `settings.gradle.kts` file:

```kotlin
pluginManagement {
    repositories {
        maven("https://repo.smolder.fr/public/")
        gradlePluginPortal()
    }
}
```

### 2. Apply the Plugin
In your `build.gradle.kts`:

```kotlin
plugins {
    id("fr.smolder.hytale.dev") version "0.0.1"
}
```

### Configuration
Configure the plugin using the `hytale` block:

```kotlin
hytale {
    // Optional: Override Hytale installation path
    hytalePath.set("C:/Users/You/AppData/Roaming/Hytale")

    // Optional: patch line (defaults to "live")
    patchLine.set("live")

    // Optional: game version (defaults to "latest")
    gameVersion.set("latest")
    
    // Auto-update manifest.json during build? (defaults to true)
    autoUpdateManifest.set(true)
    
    // Memory configuration
    minMemory.set("2G")
    maxMemory.set("4G")

    // You can modify the default arguments or add your own
    serverArgs.add("--hello-world")
}
```

### Tasks
- `./gradlew runServer`: Starts the Hytale server with the configured environment.
- `./gradlew build`: Builds the project and updates the `manifest.json`.

## Contributing
Contributions are welcome! Please feel free to submit issues, feature requests, or pull requests.

## License
This project is licensed under the MIT License.