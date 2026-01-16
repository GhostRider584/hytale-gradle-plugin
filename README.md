# Hytale Gradle Plugin
![Java](https://img.shields.io/badge/Java-orange?style=for-the-badge&logo=openjdk&logoColor=white)
![Hytale](https://img.shields.io/badge/Hytale-FF7175?style=for-the-badge&logo=anycubic&logoColor=white)
![Version](https://img.shields.io/badge/version-0.0.4-248cd6?labelColor=&style=for-the-badge)
![License: MIT](https://img.shields.io/badge/License-MIT-7267db.svg?style=for-the-badge)

A Gradle plugin to streamline Hytale plugin development.

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
    id("fr.smolder.hytale.dev") version "0.0.4"
}
```

### Configuration
Configure the plugin using the `hytale` block:

```kotlin
hytale {
    // Optional: Override Hytale installation path (defaults to OS-specific standard location)
    // hytalePath.set("...")

    // Optional: patch line (defaults to "release")
    patchLine.set("release")

    // Optional: game version (defaults to "latest")
    gameVersion.set("latest")
    
    // Auto-update manifest.json during build? (defaults to true)
    autoUpdateManifest.set(true)
    
    // Memory configuration
    minMemory.set("2G")
    maxMemory.set("4G")

    // Use AOT cache for faster startup (defaults to true)
    useAotCache.set(true)

    // You can add extra server arguments
    serverArgs.add("--your-custom-arg")
    
    // Decompilation settings
    vineflowerVersion.set("1.11.2")
    decompileFilter.set(listOf("com/hypixel/**"))
    decompilerHeapSize.set("6G")
    
    // Automatically attach decompiled sources to IDE (defaults to true)
    includeDecompiledSources.set(true)
}
```

### Tasks
- `./gradlew runServer`: Starts the Hytale server with your plugin loaded.
- `./gradlew build`: Builds the project and updates the `manifest.json`.
- `./gradlew decompileServer`: Decompiles the Hytale Server JAR and creates a `-sources.jar`.

## Contributing
Contributions are welcome! Please feel free to submit issues, feature requests, or pull requests.

## License
This project is licensed under the MIT License.