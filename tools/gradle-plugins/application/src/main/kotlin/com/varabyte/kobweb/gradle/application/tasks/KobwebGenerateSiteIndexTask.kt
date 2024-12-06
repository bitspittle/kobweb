package com.varabyte.kobweb.gradle.application.tasks

import com.varabyte.kobweb.common.navigation.BasePath
import com.varabyte.kobweb.gradle.application.BuildTarget
import com.varabyte.kobweb.gradle.application.extensions.AppBlock
import com.varabyte.kobweb.gradle.application.extensions.index
import com.varabyte.kobweb.gradle.application.templates.createIndexFile
import com.varabyte.kobweb.gradle.core.util.HtmlUtil
import com.varabyte.kobweb.gradle.core.util.hasDependencyNamed
import com.varabyte.kobweb.project.conf.KobwebConf
import kotlinx.html.link
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.util.prefixIfNot
import javax.inject.Inject

class KobwebGenIndexConfInputs(
    @get:Input val title: String,
    @get:Input val basePath: String,
    @get:Input val script: String,
) {
    constructor(kobwebConf: KobwebConf) : this(
        title = kobwebConf.site.title,
        basePath = kobwebConf.site.basePathOrRoutePrefix,
        script = kobwebConf.server.files.dev.script
    )
}

abstract class KobwebGenerateSiteIndexTask @Inject constructor(
    private val appBlock: AppBlock,
    @get:Nested val confInputs: KobwebGenIndexConfInputs,
    @get:Input val buildTarget: BuildTarget,
) : KobwebGenerateTask("Generate an index.html file for this Kobweb project") {
    @get:Nested
    val indexBlock: AppBlock.IndexBlock = appBlock.index

    @get:InputFiles
    abstract val compileClasspath: ConfigurableFileCollection

    @get:Internal
    abstract val dependencies: ListProperty<ResolvedDependencyResult>

    @get:Input
    val hasFaDependency: Provider<Boolean>
        get() = dependencies.hasDependencyNamed("com.varabyte.kobwebx:silk-icons-fa")

    @get:Input
    val hasMdiDependency: Provider<Boolean>
        get() = dependencies.hasDependencyNamed("com.varabyte.kobwebx:silk-icons-mdi")

    @OutputFile
    fun getGenIndexFile(): Provider<RegularFile> = appBlock.getGenJsResRoot().map { it.file("index.html") }

    @TaskAction
    fun execute() {
        // Collect all <head> elements together. These will almost always be defined only by the app, but libraries are
        // allowed to declare some as well. If they do, they will have embedded serialized html in their library
        // artifacts.
        val headElements = mutableListOf(indexBlock.serializedHead.get())

        if (hasFaDependency.get()) {
            headElements.add(HtmlUtil.serializeHeadContents {
                link {
                    rel = "stylesheet"
                    href = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css"
                }
            })
        }

        if (hasMdiDependency.get()) {
            headElements.add(HtmlUtil.serializeHeadContents {
                link {
                    rel = "stylesheet"
                    href =
                        "https://fonts.googleapis.com/css2?family=Material+Icons&family=Material+Icons+Outlined&family=Material+Icons+Two+Tone&family=Material+Icons+Round&family=Material+Icons+Sharp"
                }
            })
        }

//        compileClasspath.forEach { file ->
//            var libraryMetadata: LibraryMetadata? = null
//
//            file.searchZipFor(KOBWEB_METADATA_LIBRARY) { bytes ->
//                libraryMetadata = Json.decodeFromString(LibraryMetadata.serializer(), bytes.decodeToString())
//            }
//
//            libraryMetadata?.index?.headElements?.let { headElementsStr ->
//                if (headElementsStr.isBlank()) {
//                    return@forEach
//                }
//                // There doesn't seem to be a better way to pretty print the <head> contents using kotlinx.html
//                val headPrettyPrint = document {
//                    append.head { unsafe { raw(headElementsStr) } }
//                }.serialize().lines().drop(3).dropLast(2).joinToString("\n").trimIndent()
//
//                val optedOut = indexBlock.excludeHtmlForDependencies.get().any { file.name.startsWith(it) }
//                if (!optedOut) {
//                    if (indexBlock.suppressHtmlWarningsForDependencies.get()
//                            .none { file.name.startsWith(it) }
//                    ) {
//                        val dep = file.nameWithoutExtension.substringBeforeLast("-js-")
//                        logger.warn(buildString {
//                            appendLine()
//                            appendLine("Dependency artifact \"${file.name}\" will add the following <head> elements to your site's index.html:")
//                            appendLine(headPrettyPrint)
//                            appendLine("You likely want to let them do this, as it is probably necessary for the library's functionality, but you should still audit what they're doing.")
//                            append(
//                                "Add `kobweb { app { index { excludeHtmlForDependencies.add(\"$dep\") } } }` to your build.gradle.kts file to reject these elements (or `suppressHtmlWarningsForDependencies.add(\"$dep\")` to hide this message)."
//                            )
//                        })
//                    }
//                    headElements.add(headElementsStr)
//                } else {
//                    logger.warn(buildString {
//                        appendLine()
//                        appendLine("Dependency artifact \"${file.name}\" was prevented from adding the following <head> elements to your site's index.html:")
//                        append(headPrettyPrint)
//                    })
//                }
//            }
//        }

        val basePath = BasePath(confInputs.basePath)
        getGenIndexFile().get().asFile.writeText(
            createIndexFile(
                confInputs.title,
                indexBlock.lang.get(),
                headElements,
                // Our script will always exist at the root folder, so be sure to ground it,
                // e.g. "example.js" -> "/example.js", so the root will be searched even if we're visiting a page in
                // a subdirectory.
                basePath.prependTo(confInputs.script.substringAfterLast("/").prefixIfNot("/")),
                indexBlock.scriptAttributes.get(),
                buildTarget
            )
        )
    }
}
