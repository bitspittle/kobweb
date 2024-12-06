package com.varabyte.kobweb.gradle.application.tasks

import com.varabyte.kobweb.project.frontend.AppData
import com.varabyte.kobweb.project.frontend.merge
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

// NOTE: This task in meant as an internal API so it does not inherit from KobwebTask
/**
 * Collect all app data from the current site and all library dependencies, writing the result to [appDataFile].
 *
 * This is done so that multiple tasks can read the same values from a single, cached file. Those tasks should take
 * `appDataFile` as an input and then deserialize it in their execute method:
 *
 * ```
 * // Configuring the task
 * myAppDataUsingTask.configure {
 *   appDataFile.set(kobwebCacheAppDataTask.flatMap { it.appDataFile })
 * }
 *
 * // Inside the task
 * @get:InputFile
 * abstract val appDataFile: RegularFileProperty
 *
 * @TaskAction
 * fun execute() {
 *   val appData = Json.decodeFromString<AppData>(appDataFile.get().asFile.readText())
 *   // ...
 * }
 * ```
 */
abstract class KobwebCacheAppDataTask : DefaultTask() {
    init {
        description =
            "Search the project and merge all app data, saving it into a file, at which point it can be looked up by downstream tasks that need it."
    }

    @get:InputFile
    abstract val appFrontendMetadataFile: RegularFileProperty

    @get:InputFiles
    abstract val compileClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val appDataFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val appData = Json.decodeFromString<AppData>(appFrontendMetadataFile.get().asFile.readText())
        val mergedFrontendData = buildList {
            add(appData.frontendData)
//            compileClasspath.forEach { file ->
//                file.searchZipFor(KOBWEB_METADATA_FRONTEND) { bytes ->
//                    add(Json.decodeFromString<FrontendData>(bytes.decodeToString()))
//                }
//            }
        }
            .merge(throwError = { throw GradleException(it) })

        appDataFile.get().asFile.writeText(Json.encodeToString(AppData(appData.appEntry, mergedFrontendData)))
    }
}
