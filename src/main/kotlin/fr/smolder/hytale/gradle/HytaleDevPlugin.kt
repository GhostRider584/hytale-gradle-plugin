package fr.smolder.hytale.gradle

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register
import org.gradle.api.GradleException
import java.io.File
import org.gradle.process.CommandLineArgumentProvider

interface HytaleExtension {
    val hytalePath: Property<String>
    val patchLine: Property<String>
    val gameVersion: Property<String>
    val serverJar: RegularFileProperty
    val includesAssetPack: Property<Boolean>
    val loadUserMods: Property<Boolean>
    val autoUpdateManifest: Property<Boolean>
    val serverArgs: ListProperty<String>
}

class HytaleDevPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<HytaleExtension>("hytale")

        val userHome = System.getProperty("user.home")
        val defaultHytaleHome = "$userHome/AppData/Roaming/Hytale"
        
        extension.hytalePath.convention(defaultHytaleHome)
        extension.patchLine.convention("release")
        extension.gameVersion.convention("latest")
        extension.includesAssetPack.convention(true)
        extension.loadUserMods.convention(false)
        extension.autoUpdateManifest.convention(true)

        val resolvedServerJar = project.layout.file(project.provider {
            val home = extension.hytalePath.get()
            val patch = extension.patchLine.get()
            val version = extension.gameVersion.get()
            File("$home/install/$patch/package/game/$version/Server/HytaleServer.jar")
        })
        extension.serverJar.convention(resolvedServerJar)

        extension.serverArgs.convention(project.provider {
            val argsList = mutableListOf<String>()
            argsList.add("--allow-op")

            val home = extension.hytalePath.get()
            val patch = extension.patchLine.get()
            val version = extension.gameVersion.get()
            val assetsPath = "$home/install/$patch/package/game/$version/Assets.zip"
            argsList.add("--assets=$assetsPath")

            if (extension.includesAssetPack.get()) {
                 argsList.add("--mods=${project.projectDir.absolutePath}")
            }

            if (extension.loadUserMods.get()) {
                argsList.add("--mods=$home/UserData/Mods")
            }
            argsList
        })

        project.afterEvaluate {
            project.dependencies.add("implementation", project.files(extension.serverJar))
        }

        configureManifestUpdate(project, extension)
        configureRunTask(project, extension)
    }

    private fun configureManifestUpdate(project: Project, extension: HytaleExtension) {
        val updateTask = project.tasks.register("updatePluginManifest") {
            val manifestFile = project.file("src/main/resources/manifest.json")
            
            inputs.property("version", project.version)
            inputs.property("includes_pack", extension.includesAssetPack)
            outputs.file(manifestFile)

            onlyIf { extension.autoUpdateManifest.get() }

            doLast {
                if (!manifestFile.exists()) {
                    throw GradleException("Could not find manifest.json at ${manifestFile.path}!")
                }

                @Suppress("UNCHECKED_CAST")
                val manifestJson = JsonSlurper().parseText(manifestFile.readText()) as MutableMap<String, Any>
                manifestJson["Version"] = project.version.toString()
                manifestJson["IncludesAssetPack"] = extension.includesAssetPack.get()

                manifestFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifestJson)))
            }
        }
        
        project.tasks.named("processResources") {
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
            classpath(extension.serverJar)

            doFirst {
               if(!extension.serverJar.get().asFile.exists()) {
                   throw GradleException("Hytale Server JAR not found at: ${extension.serverJar.get().asFile.absolutePath}")
               }
            }

            argumentProviders.add(CommandLineArgumentProvider {
                extension.serverArgs.get()
            })
            
            workingDir = serverRunDir
            standardInput = System.`in`
        }
    }
}
