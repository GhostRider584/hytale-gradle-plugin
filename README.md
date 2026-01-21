# Hytale Gradle Plugin
![Java](https://img.shields.io/badge/Java-orange?style=for-the-badge&logo=openjdk&logoColor=white)
![Hytale](https://img.shields.io/badge/Hytale-FF7175?style=for-the-badge&logo=anycubic&logoColor=white)
![Version](https://img.shields.io/badge/version-0.0.10-248cd6?labelColor=&style=for-the-badge)
![License: MIT](https://img.shields.io/badge/License-MIT-7267db.svg?style=for-the-badge)

A Gradle plugin to streamline Hytale plugin development.

[Usage](#usage) • [Configuration](#configuration) • [Tasks](#tasks) • [Manifest DSL](#manifest-dsl) • [Authentication](#authentication) • [Legacy Mode](#legacy-mode)

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
    java // or java-library, etc.
    id("fr.smolder.hytale.dev") version "0.0.10"
}
```

> **Note**: This plugin requires the Java plugin (or any plugin that applies it, such as `java-library` for example).

### 3. Project Repositories
Ensure your project has `mavenCentral()` in its repositories (required for the Vineflower decompiler):

```kotlin
repositories {
    mavenCentral()
}
```

### Configuration
Configure the plugin using the `hytale` block:

```kotlin
hytale {
    // Optional: Override Hytale installation path (defaults to OS-specific standard location)
    // hytalePath.set("...")

    // Optional: Patch line (defaults to Patchline.RELEASE)
    patchLine.set(Patchline.RELEASE)       // or Patchline.PRE_RELEASE
    // patchLine.set(Patchline("custom"))  // custom patch line

    // Optional: game version (defaults to "latest")
    gameVersion.set("latest")
    
    // Auto-update manifest.json during build? (defaults to true)
    autoUpdateManifest.set(true)
    
    // Memory configuration
    minMemory.set("2G")
    maxMemory.set("4G")

    // Use AOT cache for faster startup (defaults to true)
    useAotCache.set(true)

    // Accept early plugins (defaults to false)
    acceptEarlyPlugins.set(true)

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
- `./gradlew runServer`: Starts the Hytale server with your plugin loaded. Auto-provisions `auth.enc` from the global cache if missing locally.
- `./gradlew build`: Builds the project and updates/generates the `manifest.json`.
- `./gradlew generateManifest`: Generates `manifest.json` from the DSL configuration.
- `./gradlew decompileServer`: Decompiles the Hytale Server JAR and creates a `-sources.jar`.
  > ⚠️ You may see `Unable to simplify switch on enum` errors, these are harmless Vineflower warnings and should be ignored.
- `./gradlew saveAuth`: Saves your current `run/auth.enc` to the global cache (`~/.gradle/hytale/auth.enc`).

## Manifest DSL

Define your pack manifest directly in Gradle with a clean, type-safe DSL:

```kotlin
hytale {
    manifest {
        group = "MyOrganization"
        name = "MyAwesomePack"
        version = project.version.toString() // Auto-syncs with project version
        description = "An incredible pack for Hytale!"
        
        // Add authors
        author {
            name = "YourName"
            email = "you@example.com"
            url = "https://your-website.com"
        }
        
        // Or simply by name
        author("AnotherContributor")
        
        website = "https://my-pack.com"
        serverVersion = "*"
        
        // Dependencies
        dependency("RequiredPack", "1.0.0")
        optionalDependency("NiceToHavePack", "*")
        
        // Plugin-specific
        main = "com.example.MyPlugin"
        includesAssetPack = true
        disabledByDefault = false
    }
}
```

### Manifest Fields

| Field                  | Type     | Description                                | Required |
|------------------------|----------|--------------------------------------------|----------|
| `group`                | String   | Your organization or group name            | Yes      |
| `name`                 | String   | The name of your Pack                      | Yes      |
| `version`              | String   | Version number (semantic versioning)       | Yes      |
| `description`          | String   | A brief description of what your Pack does | Yes      |
| `authors`              | Author[] | Array of author information                | Yes      |
| `website`              | String   | Your website or project page               | No       |
| `serverVersion`        | String   | Compatible server version (`*` for all)    | Yes      |
| `dependencies`         | Map      | Packs required for this to work            | No       |
| `optionalDependencies` | Map      | Packs that enhance but aren't required     | No       |
| `disabledByDefault`    | Boolean  | Whether Pack loads automatically           | No       |
| `main`                 | String   | Main plugin class (plugin-specific)        | No       |
| `includesAssetPack`    | Boolean  | Whether this pack includes assets          | No       |

## Authentication

The plugin makes it easy to manage Hytale authentication across multiple projects:
- **Global Cache**: The plugin looks for an `auth.enc` file in `~/.gradle/hytale/auth.enc`.
- **Auto-Provisioning**: Running `runServer` automatically copies the global `auth.enc` to your project's `run/` directory and ensures `config.json` has the `AuthCredentialStore` section configured.
- **Save Credentials**: Use `./gradlew saveAuth` to save your current project's credentials to the global cache for use in other projects.

### Legacy Mode

If you prefer to manage `manifest.json` manually, the plugin will auto-update the `Version` and `IncludesAssetPack` fields during build when:
- No `manifest {}` DSL block is defined
- `autoUpdateManifest` is `true` (default)
- `manifest.json` already exists in `src/main/resources/`

## Contributing
Contributions are welcome! Please feel free to submit issues, feature requests, or pull requests.

## License
This project is licensed under the MIT License.