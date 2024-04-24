@file:Suppress("LeakingThis") // Following official Gradle guidance

package com.varabyte.kobweb.gradle.library.extensions

import com.varabyte.kobweb.gradle.core.extensions.KobwebBlock
import com.varabyte.kobweb.gradle.core.util.IndexHead
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Nested
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType

/**
 * A sub-block for defining all properties relevant to a Kobweb library.
 */
abstract class LibraryBlock : ExtensionAware {
    /**
     * A sub-block for defining properties related to the "index.html" document generated by Kobweb
     *
     * It's expected that this will be done, in general, by the app itself, but libraries are given the ability to
     * add their own tweaks, in case they provide functionality that depend on something being present in the final
     * HTML.
     */
    abstract class IndexBlock : ExtensionAware {
        /**
         * A hook for adding elements to the `<head>` of the app's generated `index.html` file.
         *
         * You should normally use [IndexHead.add] to add new elements to the head block:
         * ```
         * kobweb.library.index.head.add {
         *    link(href = "styles.css", rel = "stylesheet")
         * }
         * ```
         * Use [IndexHead.set] to override any previously set values.
         *
         * Note that apps will have the option to opt-out of including these elements.
         */
        @get:Nested
        abstract val head: IndexHead
    }

    init {
        extensions.create<IndexBlock>("index")
    }
}

val LibraryBlock.index: LibraryBlock.IndexBlock
    get() = extensions.getByType<LibraryBlock.IndexBlock>()

val KobwebBlock.library: LibraryBlock
    get() = extensions.getByType<LibraryBlock>()

internal fun KobwebBlock.createLibraryBlock(): LibraryBlock {
    return extensions.create<LibraryBlock>("library")
}
