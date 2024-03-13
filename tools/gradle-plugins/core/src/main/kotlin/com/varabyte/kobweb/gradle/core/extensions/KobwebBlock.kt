@file:Suppress("LeakingThis") // Following official Gradle guidance

package com.varabyte.kobweb.gradle.core.extensions

import com.varabyte.kobweb.gradle.core.util.KobwebVersionUtil
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.getByType

/**
 * A gradle block used for initializing values that configure a Kobweb project.
 *
 * This class also exposes a handful of methods useful for querying the project.
 */
abstract class KobwebBlock : ExtensionAware {
    /**
     * An interface used for tagging Gradle extensions which generate files for Kobweb.
     *
     * This enables a consisted approach for tasks to determine where their output should go.
     */
    interface FileGeneratingBlock : ExtensionAware {
        /** The path to the root where generated files will be placed, relative to the project build directory. */
        val genDir: Property<String>
    }

    /**
     * The path to the root where all generated content will be written to by default,
     * relative to the project build directory.
     *
     * Setting this property is a convenient way to configure the root location of all generated content.
     * For finer control, some sub-blocks provide a `genDir` property for specifying the location of their output,
     * potentially bypassing this root path.
     */
    abstract val baseGenDir: Property<String>

    /**
     * The root package of all pages.
     *
     * Any composable function not under this root will be ignored, even if annotated by @Page.
     *
     * An initial '.' means this should be prefixed by the project group, e.g. ".pages" -> "com.example.pages"
     */
    abstract val pagesPackage: Property<String>

    /**
     * The root package of all apis.
     *
     * Any function not under this root will be ignored, even if annotated by @Api.
     *
     * An initial '.' means this should be prefixed by the project group, e.g. ".api" -> "com.example.api"
     */
    abstract val apiPackage: Property<String>

    /**
     * The path of public resources inside the project's resources folder, e.g. "public" ->
     * "src/jsMain/resources/public"
     */
    abstract val publicPath: Property<String>

    /** The KSP processor dependency that should be applied to the project, in string dependency notation. */
    abstract val kspProcessorDependency: Property<String>

    init {
        baseGenDir.convention("generated/kobweb")
        pagesPackage.convention(".pages")
        apiPackage.convention(".api")
        publicPath.convention("public")
        kspProcessorDependency.convention("com.varabyte.kobweb:kobweb-ksp-site-processors:${KobwebVersionUtil.version}")
    }
}

val Project.kobwebBlock get() = project.extensions.getByType<KobwebBlock>()
