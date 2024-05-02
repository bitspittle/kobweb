@file:Suppress("LeakingThis") // Following official Gradle guidance

package com.varabyte.kobweb.gradle.library.extensions

import com.varabyte.kobweb.gradle.core.extensions.KobwebBlock
import kotlinx.html.HEAD
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
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
         * A list of head element builders to add to the generated index.html file.
         *
         * The reason this is exposed as a list instead of a property is so that different tasks can add their own
         * values (usually scripts or stylesheets) independently of one another.
         */
        abstract val head: ListProperty<HEAD.() -> Unit>
    }

    /**
     * If set, add a prefix to all CSS names generated for this library.
     *
     * This includes CssStyle, ComponentStyle, ComponentVariant, and Keyframes properties.
     *
     * For example, if you are working on a bootstrap library and set the default prefix to "bs", then a property like
     * `val ButtonStyle = CssStyle { ... }` would generate a CSS classname `bs-button` instead of just `button`.
     *
     * NOTE: You can override prefixes on a case-by-case basis by setting the `@CssPrefix` annotation on the property
     * itself.
     *
     * If you are writing an app and simply refactoring it into pieces for organizational purposes, then you don't need
     * to set this. However, if you plan to publish your library for others to use, then setting a prefix is a good
     * practice to reduce the chance of name collisions for when they are defining their own styles.
     */
    abstract val cssPrefix: Property<String>

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
