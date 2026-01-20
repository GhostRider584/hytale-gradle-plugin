package fr.smolder.hytale.gradle

import fr.smolder.hytale.gradle.manifest.ManifestDsl
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.newInstance
import org.gradle.api.GradleException
import org.gradle.plugins.ide.idea.model.IdeaModel
import java.io.File
import org.gradle.process.CommandLineArgumentProvider
import javax.inject.Inject
import org.gradle.api.model.ObjectFactory

abstract class HytaleExtension @Inject constructor(
    private val objects: ObjectFactory,
    private val project: Project
) {
    /** Path to the Hytale installation directory */
    abstract val hytalePath: Property<String>
    
    /** Patch line to use (e.g., "release", "pre-release") */
    abstract val patchLine: Property<String>
    
    /** Game version to use */
    abstract val gameVersion: Property<String>
    
    /** Path to the server JAR */
    abstract val serverJar: RegularFileProperty
    
    /** Whether the project includes an asset pack */
    abstract val includesAssetPack: Property<Boolean>
    
    /** Whether to load user mods from the default mods directory */
    abstract val loadUserMods: Property<Boolean>
    
    /** Whether to auto-update the manifest.json with version info */
    abstract val autoUpdateManifest: Property<Boolean>
    
    /** Arguments to pass to the server */
    abstract val serverArgs: ListProperty<String>
    
    /** Minimum memory for the server JVM */
    abstract val minMemory: Property<String>
    
    /** Maximum memory for the server JVM */
    abstract val maxMemory: Property<String>
    
    /** Vineflower decompiler version */
    abstract val vineflowerVersion: Property<String>
    
    /** Filter patterns for decompilation */
    abstract val decompileFilter: ListProperty<String>
    
    /** Heap size for the decompiler */
    abstract val decompilerHeapSize: Property<String>
    
    /** Whether to use AOT cache for the server */
    abstract val useAotCache: Property<Boolean>
    
    /** Whether to include decompiled sources in the IDE */
    abstract val includeDecompiledSources: Property<Boolean>

    // Manifest DSL support
    private var manifestDsl: ManifestDsl? = null
    
    /** Access the manifest DSL configuration */
    val manifest: ManifestDsl
        get() {
            if (manifestDsl == null) {
                manifestDsl = objects.newInstance<ManifestDsl>(project)
            }
            return manifestDsl!!
        }
    
    /** Configure the manifest using a DSL block */
    fun manifest(action: Action<ManifestDsl>) {
        action.execute(manifest)
    }
    
    /** Check if manifest has been configured */
    val hasManifest: Boolean
        get() = manifestDsl != null
}

class HytaleDevPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<HytaleExtension>("hytale")

        val userHome = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        
        val defaultHytaleHome = when {
            os.contains("win") -> "$userHome/AppData/Roaming/Hytale"
            os.contains("mac") -> "$userHome/Library/Application Support/Hytale"
            else -> {
                val xdgData = System.getenv("XDG_DATA_HOME")
                if (xdgData.isNullOrEmpty()) "$userHome/.local/share/Hytale" else "$xdgData/Hytale"
            }
        }
        
        extension.hytalePath.convention(defaultHytaleHome)
        extension.patchLine.convention("release")
        extension.gameVersion.convention("latest")
        extension.includesAssetPack.convention(true)
        extension.loadUserMods.convention(false)
        extension.autoUpdateManifest.convention(true)
        extension.minMemory.convention("1G")
        extension.maxMemory.convention("4G")
        extension.vineflowerVersion.convention("1.11.2")
        extension.decompileFilter.convention(listOf("com/hypixel/**"))
        extension.decompilerHeapSize.convention("6G")
        extension.useAotCache.convention(true)
        extension.includeDecompiledSources.convention(true)

        val resolvedServerJar = project.layout.file(project.provider {
            val home = extension.hytalePath.get()
            val patch = extension.patchLine.get()
            val version = extension.gameVersion.get()
            File("$home/install/$patch/package/game/$version/Server/HytaleServer.jar")
        })
        extension.serverJar.convention(resolvedServerJar)
        extension.serverArgs.convention(emptyList())

        val decompiler = project.configurations.create("decompiler")
        project.afterEvaluate {
            project.dependencies.add("decompiler", "org.vineflower:vineflower:${extension.vineflowerVersion.get()}")
            project.dependencies.add("implementation", project.files(extension.serverJar))
        }

        configureManifestUpdate(project, extension)
        configureDecompileTask(project, extension, decompiler)
        configureAuthTask(project)
        configureRunTask(project, extension)

        project.afterEvaluate {
            project.pluginManager.withPlugin("idea") {
                project.extensions.configure<IdeaModel>("idea") {
                    if (extension.includeDecompiledSources.get()) {
                        val decompiledSourcesDir = project.layout.buildDirectory.dir("decompile/sources").get().asFile
                        module.sourceDirs = module.sourceDirs + decompiledSourcesDir
                        module.generatedSourceDirs = module.generatedSourceDirs + decompiledSourcesDir
                    }
                }
            }
        }
    }

    private fun configureManifestUpdate(project: Project, extension: HytaleExtension) {
        val generatedResourcesDir = project.layout.buildDirectory.dir("generated/resources/hytale")
        val generatedManifest = generatedResourcesDir.map { it.file("manifest.json") }
        val legacyManifest = project.file("src/main/resources/manifest.json")

        val generateTask = project.tasks.register("generateManifest") {
            group = "hytale"
            description = "Generates manifest.json from the manifest DSL configuration"

            onlyIf { extension.hasManifest }

            // track all inputs so Gradle knows when to regenerate
            inputs.property("group", project.provider { extension.manifest.group })
            inputs.property("name", project.provider { extension.manifest.name })
            inputs.property("version", project.provider { extension.manifest.version })
            inputs.property("description", project.provider { extension.manifest.description })
            inputs.property("serverVersion", project.provider { extension.manifest.serverVersion })
            inputs.property("website", project.provider { extension.manifest.website }).optional(true)
            inputs.property("main", project.provider { extension.manifest.main }).optional(true)
            inputs.property("includesAssetPack", project.provider { extension.manifest.includesAssetPack })
            inputs.property("disabledByDefault", project.provider { extension.manifest.disabledByDefault })
            
            outputs.file(generatedManifest)

            doLast {
                val manifest = extension.manifest

                if (!manifest.includesAssetPackProperty.isPresent) {
                    manifest.includesAssetPack = extension.includesAssetPack.get()
                }
                
                val errors = manifest.validate()
                if (errors.isNotEmpty()) {
                    throw GradleException("Manifest validation failed:\n  - ${errors.joinToString("\n  - ")}")
                }

                val outputFile = generatedManifest.get().asFile
                outputFile.parentFile?.mkdirs()
                outputFile.writeText(manifest.toJson())
                println("Generated manifest.json")
            }
        }

        // legacy manifest.json
        val updateTask = project.tasks.register("updatePluginManifest") {
            group = "hytale"
            description = "Updates version and includesAssetPack in an existing manifest.json"
            
            inputs.property("version", project.version)
            inputs.property("includes_pack", extension.includesAssetPack)
            outputs.file(legacyManifest)

            onlyIf {
                extension.autoUpdateManifest.get() && !extension.hasManifest && legacyManifest.exists()
            }

            doLast {
                if (!legacyManifest.exists()) {
                    throw GradleException("Could not find manifest.json at ${legacyManifest.path}!")
                }

                @Suppress("UNCHECKED_CAST")
                val manifestJson = JsonSlurper().parseText(legacyManifest.readText()) as MutableMap<String, Any>
                manifestJson["Version"] = project.version.toString()
                manifestJson["IncludesAssetPack"] = extension.includesAssetPack.get()

                legacyManifest.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifestJson)))
                println("Updated manifest.json with version ${project.version}")
            }
        }

        // add generated resources to the source set
        project.afterEvaluate {
            if (extension.hasManifest) {
                val javaExtension = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
                val mainSourceSet = javaExtension.sourceSets.getByName("main")
                mainSourceSet.resources.srcDir(generatedResourcesDir)
            }
        }

        project.tasks.named("processResources") {
            dependsOn(generateTask)
            dependsOn(updateTask)
        }
    }

    private fun configureRunTask(project: Project, extension: HytaleExtension) {
        val serverRunDir = project.file("${project.projectDir}/run")
        if (!serverRunDir.exists()) {
            serverRunDir.mkdirs()
        }

        project.tasks.register<JavaExec>("runServer") {
            group = "hytale"
            description = "Runs the Hytale Server"

            dependsOn(project.tasks.named("build"))

            mainClass.set("com.hypixel.hytale.Main")
            
            val javaExtension = project.extensions.getByType(org.gradle.api.plugins.JavaPluginExtension::class.java)
            val mainSourceSet = javaExtension.sourceSets.getByName("main")
            
            classpath(extension.serverJar)
            classpath(mainSourceSet.runtimeClasspath)

            doFirst {
               if(!extension.serverJar.get().asFile.exists()) {
                   throw GradleException("Hytale Server JAR not found at: ${extension.serverJar.get().asFile.absolutePath}")
               }

               val globalAuth = File(project.gradle.gradleUserHomeDir, "hytale/auth.enc")
               val localAuth = File(serverRunDir, "auth.enc")
               if (globalAuth.exists() && !localAuth.exists()) {
                   globalAuth.copyTo(localAuth)
                   println("Auto-provisioned auth.enc from global cache")
               }

               minHeapSize = extension.minMemory.get()
               maxHeapSize = extension.maxMemory.get()
               
               if (extension.useAotCache.get()) {
                   val serverJar = extension.serverJar.get().asFile
                   val aotFile = File(serverJar.parentFile, "HytaleServer.aot")
                   if (aotFile.exists()) {
                       jvmArgs("-XX:AOTCache=${aotFile.absolutePath}")
                   }
               }
            }

            argumentProviders.add(CommandLineArgumentProvider {
                val argsList = mutableListOf<String>()

                argsList.add("--allow-op")
                argsList.add("--disable-sentry")

                val home = extension.hytalePath.get()
                val patch = extension.patchLine.get()
                val version = extension.gameVersion.get()
                val assetsPath = "$home/install/$patch/package/game/$version/Assets.zip"
                argsList.add("--assets=$assetsPath")

                if (extension.includesAssetPack.get()) {
                     val srcMain = project.file("src/main")
                     argsList.add("--mods=${srcMain.absolutePath}")
                }

                if (extension.loadUserMods.get()) {
                    argsList.add("--mods=$home/UserData/Mods")
                }

                argsList.addAll(extension.serverArgs.get())
                
                argsList
            })
            
            workingDir = serverRunDir
            standardInput = System.`in`
        }
    }

    private fun configureDecompileTask(
        project: Project,
        extension: HytaleExtension,
        decompiler: org.gradle.api.artifacts.Configuration
    ) {
        val tempClassesDir = project.layout.buildDirectory.dir("decompile/classes")
        val decompiledOutputDir = project.layout.buildDirectory.dir("decompile/sources")

        project.tasks.register<JavaExec>("decompileServer") {
            group = "hytale"
            description = "Decompiles the Hytale Server JAR using Vineflower"

            classpath = decompiler
            mainClass.set("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler")

            maxHeapSize = extension.decompilerHeapSize.get()
            jvmArgs("-Xms2G")

            doFirst {
                val serverJar = extension.serverJar.get().asFile
                if (!serverJar.exists()) {
                    throw GradleException("Hytale Server JAR not found at: ${serverJar.absolutePath}")
                }

                val classesDir = tempClassesDir.get().asFile
                val outputDir = decompiledOutputDir.get().asFile
                
                project.delete(classesDir)
                project.delete(outputDir)
                project.mkdir(classesDir)
                project.mkdir(outputDir)

                println("Extracting classes from ${serverJar.name}...")
                project.copy {
                    from(project.zipTree(serverJar))
                    into(classesDir)
                    extension.decompileFilter.get().forEach { pattern ->
                        include(pattern)
                    }
                }

                println("Starting decompilation...")

                args = listOf(
                    "-dgs=1", "-rsy=1", "-rbr=1", "-lit=1", "-jvn=1", "-log=ERROR",
                    classesDir.absolutePath,
                    outputDir.absolutePath
                )
            }

            doLast {
                val serverJar = extension.serverJar.get().asFile
                val sourcesJar = File(serverJar.parentFile, serverJar.nameWithoutExtension + "-sources.jar")
                val outputDir = decompiledOutputDir.get().asFile

                println("Creating sources jar: ${sourcesJar.name}...")
                project.ant.invokeMethod("zip", mapOf(
                    "destfile" to sourcesJar.absolutePath,
                    "basedir" to outputDir.absolutePath
                ))

                println("--------------------------------------------------")
                println("Done! Sources jar created at: ${sourcesJar.absolutePath}")
                println("--------------------------------------------------")
            }
        }
    }
    
    private fun configureAuthTask(project: Project) {
        project.tasks.register("saveAuth") {
            group = "hytale"
            description = "Saves the local authentication file to the global Gradle cache"

            doLast {
                val localAuth = project.file("run/auth.enc")
                if (localAuth.exists()) {
                    val globalAuth = File(project.gradle.gradleUserHomeDir, "hytale/auth.enc")
                    globalAuth.parentFile.mkdirs()
                    localAuth.copyTo(globalAuth, overwrite = true)
                    println("Saved auth.enc to ${globalAuth.absolutePath}")
                } else {
                    println("No local auth.enc found in run directory")
                }
            }
        }
    }
}
