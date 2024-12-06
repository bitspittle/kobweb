package com.varabyte.kobweb.gradle.application

import com.varabyte.kobweb.ProcessorMode
import com.varabyte.kobweb.gradle.application.buildservices.KobwebTaskListener
import com.varabyte.kobweb.gradle.application.extensions.AppBlock
import com.varabyte.kobweb.gradle.application.extensions.app
import com.varabyte.kobweb.gradle.application.extensions.createAppBlock
import com.varabyte.kobweb.gradle.application.extensions.export
import com.varabyte.kobweb.gradle.application.extensions.remoteDebugging
import com.varabyte.kobweb.gradle.application.extensions.server
import com.varabyte.kobweb.gradle.application.ksp.kspBackendFile
import com.varabyte.kobweb.gradle.application.ksp.kspFrontendFile
import com.varabyte.kobweb.gradle.application.tasks.KobwebBrowserCacheIdTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebCacheAppDataTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebCopySupplementalResourcesTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebCopyTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebCopyWorkerJsOutputTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebCreateServerScriptsTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebExportConfInputs
import com.varabyte.kobweb.gradle.application.tasks.KobwebExportTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebGenIndexConfInputs
import com.varabyte.kobweb.gradle.application.tasks.KobwebGenSiteEntryConfInputs
import com.varabyte.kobweb.gradle.application.tasks.KobwebGenerateApisFactoryTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebGenerateSiteEntryTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebGenerateSiteIndexTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebGenerateTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebListRoutesTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebStartTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebStopTask
import com.varabyte.kobweb.gradle.application.tasks.KobwebUnpackServerJarTask
import com.varabyte.kobweb.gradle.core.KobwebCorePlugin
import com.varabyte.kobweb.gradle.core.extensions.kobwebBlock
import com.varabyte.kobweb.gradle.core.kmp.JsTarget
import com.varabyte.kobweb.gradle.core.kmp.JvmTarget
import com.varabyte.kobweb.gradle.core.kmp.buildTargets
import com.varabyte.kobweb.gradle.core.kmp.kotlin
import com.varabyte.kobweb.gradle.core.ksp.applyKspPlugin
import com.varabyte.kobweb.gradle.core.ksp.kspExcludedSources
import com.varabyte.kobweb.gradle.core.ksp.setKspMode
import com.varabyte.kobweb.gradle.core.ksp.setupKspJs
import com.varabyte.kobweb.gradle.core.ksp.setupKspJvm
import com.varabyte.kobweb.gradle.core.registerKobwebMigrationTasks
import com.varabyte.kobweb.gradle.core.tasks.KobwebTask
import com.varabyte.kobweb.gradle.core.util.configureHackWorkaroundSinceWebpackTaskIsBrokenInContinuousMode
import com.varabyte.kobweb.gradle.core.util.getBuildScripts
import com.varabyte.kobweb.gradle.core.util.getResourceSources
import com.varabyte.kobweb.gradle.core.util.getTransitiveJsDependencyResults
import com.varabyte.kobweb.gradle.core.util.isDescendantOf
import com.varabyte.kobweb.gradle.core.util.kobwebCacheFile
import com.varabyte.kobweb.gradle.core.util.namedOrNull
import com.varabyte.kobweb.project.KobwebFolder
import com.varabyte.kobweb.project.conf.KobwebConfFile
import com.varabyte.kobweb.server.api.ServerEnvironment
import com.varabyte.kobweb.server.api.SiteLayout
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.Sync
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import javax.inject.Inject
import kotlin.io.path.exists

val Project.kobwebFolder: KobwebFolder
    get() = KobwebFolder.fromChildPath(layout.projectDirectory.asFile.toPath())
        ?: throw GradleException("This project is not a Kobweb project but is applying the Kobweb plugin.")

@Suppress("unused") // KobwebApplicationPlugin is found by Gradle via reflection
class KobwebApplicationPlugin @Inject constructor(
    private val buildEventsListenerRegistry: BuildEventsListenerRegistry
) : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(KobwebCorePlugin::class.java)
        project.applyKspPlugin()
        project.setKspMode(ProcessorMode.APP)

        // A Kobweb Server Plugin is one which is loaded by the Kobweb server when it starts up. It's a way for users to
        // configure their ktor server in ways that Kobweb does not currently expose.
        val kobwebServerPluginConfiguration = project.configurations.register("kobwebServerPlugin") {
            isCanBeConsumed = false
            isTransitive = false
        }

        val kobwebFolder = project.kobwebFolder
        val kobwebConf = with(KobwebConfFile(kobwebFolder)) {
            if (!path.exists()) {
                throw GradleException("Missing conf.yaml file from Kobweb folder. Did you delete it?")
            }
            content!!
        }
        @Suppress("DEPRECATION")
        if (kobwebConf.site.routePrefix.isNotEmpty()) {
            if (kobwebConf.site.basePath.isEmpty()) {
                project.logger.warn("w: This project is setting `routePrefix` in `.kobweb/conf.yaml`. Please rename `routePrefix` to `basePath`. We are conforming to a standard name used by many other web frameworks.")
            } else {
                project.logger.warn("w: This project is setting `routePrefix` in `.kobweb/conf.yaml` in addition to `basePath`. This value is unused in this case and should be removed.")
            }
        }

        val kobwebBlock = project.kobwebBlock
        val appBlock = kobwebBlock.createAppBlock(kobwebFolder, kobwebConf)

        val env =
            project.findProperty("kobwebEnv")?.let { ServerEnvironment.valueOf(it.toString()) } ?: ServerEnvironment.DEV
        val runLayout =
            project.findProperty("kobwebRunLayout")?.let { SiteLayout.valueOf(it.toString()) } ?: SiteLayout.FULLSTACK
        val exportLayout =
            project.findProperty("kobwebExportLayout")?.let { SiteLayout.valueOf(it.toString()) }
                ?: SiteLayout.FULLSTACK

        project.extra["kobwebBuildTarget"] =
            project.findProperty("kobwebBuildTarget")?.let { BuildTarget.valueOf(it.toString()) }
                ?: if (env == ServerEnvironment.DEV) BuildTarget.DEBUG else BuildTarget.RELEASE
        val buildTarget = project.kobwebBuildTarget

        val kobwebGenSiteIndexTask = project.tasks.register<KobwebGenerateSiteIndexTask>(
            "kobwebGenSiteIndex", kobwebBlock.app, KobwebGenIndexConfInputs(kobwebConf), buildTarget
        )

        val kobwebCopySupplementalResourcesTask = project.tasks.register<KobwebCopySupplementalResourcesTask>(
            "kobwebCopySupplementalResources",
            kobwebBlock.app,
            kobwebGenSiteIndexTask.map { it.getGenIndexFile() }
        )
        val kobwebCopyWorkerJsOutputTask =
            project.tasks.register<KobwebCopyWorkerJsOutputTask>("kobwebCopyWorkerJsOutput", kobwebBlock.app)

        val kobwebUnpackServerJarTask = project.tasks.register<KobwebUnpackServerJarTask>("kobwebUnpackServerJar")
        val kobwebCreateServerScriptsTask = project.tasks
            .register<KobwebCreateServerScriptsTask>("kobwebCreateServerScripts", exportLayout)

        val kobwebStartTask = run {
            val reuseServer = project.findProperty("kobwebReuseServer")?.toString()?.toBoolean() ?: true
            project.tasks.register<KobwebStartTask>(
                "kobwebStart", kobwebBlock.app.server.remoteDebugging, env, runLayout, reuseServer
            )
        }

        val kobwebSyncServerPluginJarsTask = project.tasks.register<Sync>("kobwebSyncServerPluginJars") {
            group = "kobweb"
            description = "Copy all Kobweb server plugin jars (if any) into the server's plugins directory"

            from(kobwebServerPluginConfiguration)
            into(project.projectDir.resolve(".kobweb/server/plugins"))
        }

        kobwebStartTask.configure {
            // Gradle does not consider changes to build logic when running in continuous mode:
            // https://docs.gradle.org/current/userguide/command_line_interface.html#sec:changes_to_build_logic_are_not_considered
            // So, we manually add files that affect build logic (build scripts) as inputs to `kobwebStart`,
            // which ensures that changing these files does in fact cause the build to re-execute in continuous mode.
            // NOTE: To ensure build scripts from kobweb library modules are also watched for changes, we add
            // build scripts from all modules as inputs. Users with many non-kobweb modules may choose to override this
            // value to exclude build scripts from unrelated modules.
            watchFiles.from(project.provider { project.rootProject.subprojects.map { it.layout.getBuildScripts() } })
            serverJar.set(kobwebUnpackServerJarTask.map { RegularFile { it.getServerJar() } })
            serverPluginsDir.set(kobwebSyncServerPluginJarsTask.map {
                project.objects.directoryProperty().apply { set(it.destinationDir) }.get()
            })
            val devScript = kobwebConf.server.files.dev.script
            val devScriptFile = project.file(devScript)
            doLast {
                if (env == ServerEnvironment.DEV && !devScriptFile.exists()) {
                    throw GradleException(
                        "e: Your .kobweb/conf.yaml dev script (\"$devScript\") could not be found. This will cause " +
                            "the page to fail to load with a 500 error. Perhaps search your build/ directory for " +
                            "\"${devScript.substringAfterLast('/')}\" to find the right path."
                    )
                }
            }
        }
        project.tasks.register<KobwebStopTask>("kobwebStop")

        val kobwebCleanSiteTask = project.tasks.register<KobwebTask>(
            "kobwebCleanSite",
            "Cleans all site artifacts generated by a previous export"
        )
        kobwebCleanSiteTask.configure {
            val traceConfig = kobwebBlock.app.export.traceConfig
            val projectRoot = projectLayout.projectDirectory.asFile
            doLast {
                traceConfig.orNull?.let { traceConfig ->
                    projectRoot.resolve(traceConfig.root.toFile()).deleteRecursively()
                }
                projectRoot.resolve(kobwebConf.server.files.prod.siteRoot).deleteRecursively()
            }
        }
        val kobwebCleanFolderTask = project.tasks.register<KobwebTask>(
            "kobwebCleanFolder",
            "Cleans all transient files that live in the .kobweb folder"
        )
        kobwebCleanFolderTask.configure {
            dependsOn(kobwebCleanSiteTask)
            doLast {
                projectLayout.projectDirectory.asFile
                    .resolve(kobwebFolder.path.resolve("server").toFile())
                    .deleteRecursively()
            }
        }

        val kobwebCacheAppDataTask = project.tasks.register<KobwebCacheAppDataTask>("kobwebCacheAppData")
        val kobwebExportTask = project.tasks
            .register<KobwebExportTask>(
                "kobwebExport",
                appBlock.export,
                KobwebExportConfInputs(kobwebConf),
                exportLayout,
            )

        val kobwebListRoutesTask = project.tasks.register<KobwebListRoutesTask>("kobwebListRoutes")

        project.tasks.register<KobwebBrowserCacheIdTask>("kobwebBrowserCacheId") {
            browser.set(kobwebBlock.app.export.browser)
        }

        val taskListenerService = project.gradle.sharedServices
            .registerIfAbsent("kobweb-task-listener", KobwebTaskListener::class.java) {
                parameters.kobwebStartTaskName = kobwebStartTask.name
                parameters.isKobwebStartBuild = kobwebStartTask.name in project.gradle.startParameter.taskNames
                parameters.kobwebFolderFile = kobwebFolder.path.toFile()
            }
        buildEventsListenerRegistry.onTaskCompletion(taskListenerService)

        project.tasks.withType<KotlinWebpack>().configureHackWorkaroundSinceWebpackTaskIsBrokenInContinuousMode()
        project.buildTargets.withType<KotlinJsIrTarget>().configureEach {
            val jsTarget = JsTarget(this)

            // Beginning with Kotlin 2.1.0, the Kotlin Gradle plugin began using uncompressed klibs for
            // inter-project dependencies. This breaks our code responsible for detecting and extracting metadata from
            // kobweb library dependencies, as the required resources (e.g. `KOBWEB_METADATA_FRONTEND`) are not present
            // in non-packed klibs. Thus, we override this default and explicitly request packed klibs
            // NOTE: this is not necessary to do for the Jvm target as it is still using jars for its classpath.
            //
            // This is intended as a temporary workaround until we find a way to make unpacked klibs compatible with
            // kobweb libraries.
            // See also: https://youtrack.jetbrains.com/issue/KT-65285/Use-uncompressed-Klibs
//            @OptIn(ExperimentalKotlinGradlePluginApi::class)
//            run {
//                listOf(jsTarget.compileClasspath, jsTarget.runtimeClasspath).forEach { configurationName ->
//                    project.configurations.named(configurationName).configure {
//                        attributes.attribute(KlibPackaging.ATTRIBUTE, project.objects.named(KlibPackaging.PACKED))
//                    }
//                }
//            }

            project.setupKspJs(jsTarget, appBlock.cssPrefix)

            project.tasks.named<ProcessResources>(jsTarget.processResources) {
                inputs.property("kobweb.publicPath", kobwebBlock.publicPath)
                inputs.property("kobweb.genDir", kobwebBlock.app.genDir)
                // No one should define their own root `public/index.html` files anywhere in their resources.
                val generatedRoot = project.layout.buildDirectory.dir(kobwebBlock.app.genDir).get().asFile
                val projectDir = project.projectDir
                filesMatching("${kobwebBlock.publicPath.get()}/index.html") {
                    if (!file.isDescendantOf(generatedRoot)) {
                        throw GradleException(
                            "You are not supposed to define the root index file yourself. Kobweb provides its own. " +
                                "Use the `kobweb.app.index { ... }` block if you need to modify the generated index file. " +
                                "Problematic file: ${file.relativeToOrSelf(projectDir)}"
                        )
                    }
                }
            }

            val kobwebGenSiteEntryTask = project.tasks.register<KobwebGenerateSiteEntryTask>(
                "kobwebGenSiteEntry",
                appBlock,
                kobwebConf.site.basePathOrRoutePrefix,
                buildTarget,
                KobwebGenSiteEntryConfInputs(kobwebConf),
            )

            kobwebCacheAppDataTask.configure {
                appFrontendMetadataFile.set(project.kspFrontendFile(jsTarget))
                compileClasspath.from(project.configurations.named(jsTarget.compileClasspath))
                appDataFile.set(this.kobwebCacheFile("appData.json"))
            }

            kobwebGenSiteEntryTask.configure {
                appDataFile.set(kobwebCacheAppDataTask.flatMap { it.appDataFile })
                dependencies.set(project.getTransitiveJsDependencyResults())
            }

            kobwebGenSiteIndexTask.configure {
                compileClasspath.from(project.configurations.named(jsTarget.compileClasspath))
                dependencies.set(project.getTransitiveJsDependencyResults())
            }

            val jsRunTasks = listOf(
                jsTarget.browserDevelopmentRun, jsTarget.browserProductionRun,
                jsTarget.browserRun, jsTarget.run,
            )
            // Users should be using Kobweb tasks instead of the standard multiplatform tasks, but they
            // probably don't know that. We do our best to work even in those cases, but warn the user to prefer
            // the Kobweb commands instead.
            jsRunTasks.forEach { taskName ->
                project.tasks.namedOrNull(taskName)?.configure {
                    doFirst {
                        logger.error("With Kobweb, you should run `gradlew kobwebStart` instead. Some site behavior may not work.")
                    }
                }
            }

            // configure both kobwebCopySupplementalResourcesTask & kobwebCopyWorkerJsOutputTask
            project.tasks.withType<KobwebCopyTask>().configureEach {
                publicPath.set(kobwebBlock.publicPath)
                runtimeClasspath.from(project.configurations.named(jsTarget.runtimeClasspath))
            }

            project.kspExcludedSources.from(kobwebGenSiteEntryTask)
            project.kotlin.sourceSets.named(jsTarget.mainSourceSet) {
                kotlin.srcDir(kobwebGenSiteEntryTask)
                resources.srcDir(kobwebCopySupplementalResourcesTask)
                resources.srcDir(kobwebCopyWorkerJsOutputTask)
            }

            // When exporting, both dev + production webpack actions are triggered - dev for the temporary server
            // that runs, and production for generating the final JS file for the site. However, these tasks share some
            // output directories (see https://youtrack.jetbrains.com/issue/KT-56305), so the following order
            // declaration is needed for gradle to be happy. Note also that we don't configure the task directly by its
            // name, as it may not yet exist (for some reason). Pending https://github.com/gradle/gradle/issues/16543,
            // we simply match it by its name amongst all tasks of its type.
            project.tasks
                .matching { it.name == jsTarget.compileProductionExecutableKotlin }
                .configureEach { mustRunAfter(kobwebStartTask) }

            kobwebStartTask.configure {
                // PROD env uses files copied over into a site folder by the export task, so it doesn't need to trigger
                // much.
                if (env == ServerEnvironment.DEV) {
                    val webpackTask = project.tasks.named(jsTarget.browserDevelopmentWebpack)
                    dependsOn(webpackTask)
                }
            }

            kobwebExportTask.configure {
                appDataFile.set(kobwebCacheAppDataTask.flatMap { it.appDataFile })
                publicPath.set(kobwebBlock.publicPath)
                publicResources.from(kobwebBlock.publicPath.map { publicPath ->
                    project.getResourceSources(jsTarget).map { srcDirSet ->
                        srcDirSet.matching { include("$publicPath/**") }
                    }
                })
                // Exporting ALWAYS spins up a dev server, so that way it loads the files it needs from dev locations
                // before outputting them into a final prod folder.
                check(env == ServerEnvironment.DEV)

                dependsOn(kobwebCleanSiteTask)
                dependsOn(kobwebCreateServerScriptsTask)
                dependsOn(kobwebStartTask)
                dependsOn(project.tasks.namedOrNull(jsTarget.browserProductionWebpack))
            }

            kobwebListRoutesTask.configure {
                appDataFile.set(kobwebCacheAppDataTask.flatMap { it.appDataFile })
            }
        }
        project.buildTargets.withType<KotlinJvmTarget>().configureEach {
            val jvmTarget = JvmTarget(this)

            project.setupKspJvm(jvmTarget)

            // PROD env uses files copied over into a site folder by the export task, so it doesn't need to trigger
            // much.
            kobwebStartTask.configure {
                if (env == ServerEnvironment.DEV) {
                    // If this site has server routes, make sure we built the jar that our servers can load
                    dependsOn(project.tasks.namedOrNull(jvmTarget.jar))
                }
            }

            val kobwebGenApisFactoryTask = project.tasks
                .register<KobwebGenerateApisFactoryTask>("kobwebGenApisFactory", kobwebBlock.app)

            kobwebGenApisFactoryTask.configure {
                kspGenFile.set(project.kspBackendFile(jvmTarget))
                compileClasspath.from(project.configurations.named(jvmTarget.compileClasspath))
            }

            project.kspExcludedSources.from(kobwebGenApisFactoryTask)
            project.kotlin.sourceSets.named(jvmTarget.mainSourceSet) {
                kotlin.srcDir(kobwebGenApisFactoryTask)
            }
        }

        // Convenience task in case you quickly want to run all "kobwebGen..." tasks
        project.tasks.register("kobwebGenAll") {
            group = "kobweb"
            description = "Run all Kobweb code generation tasks"

            dependsOn(project.tasks.withType<KobwebGenerateTask>())
        }

        project.registerKobwebMigrationTasks()

        project.afterEvaluate {
            @Suppress("DEPRECATION")
            val legacyOverride = kobwebBlock.app.legacyRouteRedirectStrategy.orNull
            if (legacyOverride != null) {
                project.logger.warn(
                    buildString {
                        val legacyRoutesWereEnabled = legacyOverride != AppBlock.LegacyRouteRedirectStrategy.DISALLOW
                        append(if (legacyRoutesWereEnabled) 'e' else 'w')
                        append(": The `legacyRouteRedirectStrategy` property is no longer functional. Please remove it at your earliest convenience.")

                        if (legacyOverride != AppBlock.LegacyRouteRedirectStrategy.DISALLOW) {
                            append(" As this was previously set to a value ($legacyOverride) that allowed automatically redirecting routes, please note that your site's behavior might have been affected by this change. Please review https://github.com/varabyte/kobweb#removing-the-legacy-route-strategy at your earliest convenience.")
                        }
                    }
                )
            }
        }
    }
}

val Project.kobwebBuildTarget get() = project.extra["kobwebBuildTarget"] as BuildTarget
