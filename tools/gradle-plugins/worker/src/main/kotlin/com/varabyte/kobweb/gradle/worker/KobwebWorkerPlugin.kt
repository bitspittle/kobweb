package com.varabyte.kobweb.gradle.worker

import com.varabyte.kobweb.gradle.core.KobwebCorePlugin
import com.varabyte.kobweb.gradle.core.extensions.kobwebBlock
import com.varabyte.kobweb.gradle.core.kmp.JsTarget
import com.varabyte.kobweb.gradle.core.kmp.buildTargets
import com.varabyte.kobweb.gradle.core.ksp.addKspArguments
import com.varabyte.kobweb.gradle.core.ksp.addKspDependency
import com.varabyte.kobweb.gradle.core.ksp.applyKspPlugin
import com.varabyte.kobweb.gradle.core.ksp.configureKspTask
import com.varabyte.kobweb.gradle.core.util.KobwebVersionUtil
import com.varabyte.kobweb.gradle.core.util.configureHackWorkaroundSinceWebpackTaskIsBrokenInContinuousMode
import com.varabyte.kobweb.gradle.core.util.generateModuleMetadataFor
import com.varabyte.kobweb.gradle.core.util.suggestKobwebProjectName
import com.varabyte.kobweb.gradle.worker.extensions.createWorkerBlock
import com.varabyte.kobweb.gradle.worker.extensions.worker
import com.varabyte.kobweb.gradle.worker.tasks.KobwebGenerateWorkerMetadataTask
import com.varabyte.kobweb.ksp.KOBWEB_METADATA_WORKER_SUBFOLDER
import com.varabyte.kobweb.ksp.KSP_WORKER_FQCN_KEY
import com.varabyte.kobweb.ksp.KSP_WORKER_OUTPUT_PATH_KEY
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.utils.provider

@Suppress("unused") // Used by Gradle
class KobwebWorkerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(KobwebCorePlugin::class.java)
        project.applyKspPlugin()
        project.tasks.withType<KotlinWebpack>().configureHackWorkaroundSinceWebpackTaskIsBrokenInContinuousMode()

        project.kobwebBlock.apply {
            kspProcessorDependency.convention("com.varabyte.kobweb:kobweb-ksp-worker-processor:${KobwebVersionUtil.version}")
            createWorkerBlock(project)
        }

        val kobwebGenerateWorkerMetadataTask =
            project.tasks.register("kobwebGenerateWorkerMetadata", KobwebGenerateWorkerMetadataTask::class.java)

        project.buildTargets.withType<KotlinJsIrTarget>().configureEach {
            val jsTarget = JsTarget(this)
            project.addKspDependency(jsTarget)
            project.configureKspTask(jsTarget) {
                project.addKspArguments(
                    KSP_WORKER_OUTPUT_PATH_KEY to "${project.suggestKobwebProjectName()}/${project.kobwebBlock.worker.name.get()}.js",
                )
                if (project.kobwebBlock.worker.fqcn.isPresent) {
                    project.addKspArguments(
                        KSP_WORKER_FQCN_KEY to project.kobwebBlock.worker.fqcn.get(),
                    )
                }
            }
            project.generateModuleMetadataFor(jsTarget)
            project.tasks.named<ProcessResources>(jsTarget.processResources) {
                from(kobwebGenerateWorkerMetadataTask)
            }

            val jsBrowserDistributionTask by provider { project.tasks.named(jsTarget.browserDistribution) }
            val copyWorkerJsOutput = project.tasks.register<Sync>("kobwebCopyWorkerJsOutput") {
                val genResDir = project.layout.buildDirectory.dir("generated/kobweb/worker/output")

                from(jsBrowserDistributionTask)
                // NOTE: I originally also included the .js.map file, but it doesn't seem to get loaded by the browser,
                // and meanwhile its presence causes the Kotlin/JS compiler to spit out a huuuuuuge warning. So for now
                // I'm leaving it out, but we may revisit this decision later.
                include("*.js")

                val suggestedProjectName = project.suggestKobwebProjectName()
                eachFile {
                    path = "$KOBWEB_METADATA_WORKER_SUBFOLDER/$suggestedProjectName/$path"
                }
                into(genResDir)
            }

            // Use the "Jar" task instead of "ProcessResources" because the jsBrowserProductionWebpack task
            // runs AFTER the jsProcessResources task.
            project.tasks.named<Jar>(jsTarget.jar) {
                from(copyWorkerJsOutput)
            }
        }
    }
}
