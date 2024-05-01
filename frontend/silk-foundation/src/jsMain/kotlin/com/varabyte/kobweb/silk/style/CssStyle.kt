package com.varabyte.kobweb.silk.style

import androidx.compose.runtime.*
import com.varabyte.kobweb.browser.util.kebabCaseToTitleCamelCase
import com.varabyte.kobweb.compose.attributes.ComparableAttrsScope
import com.varabyte.kobweb.compose.css.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.compose.ui.toStyles
import com.varabyte.kobweb.silk.style.component.ClassSelectors
import com.varabyte.kobweb.silk.theme.SilkTheme
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.suffixedWith
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.css.*
import org.w3c.dom.Element

/**
 * A [CssStyle] is a collection of [StyleModifiers] that will be processed by Kobweb and added into a CSS stylesheet.
 *
 * For example, you can declare a style like this:
 *
 * ```
 * val NonInteractiveStyle = CssStyle {
 *   hover { Modifier.cursor(Cursor.NotAllowed) }
 * }
 * ```
 *
 * which will result in a CSS rule in your site's stylesheet like this:
 *
 * ```css
 * .non-interactive:hover {
 *   cursor: not-allowed;
 * }
 * ```
 *
 * While most developers will never see that this is happening, it's still very helpful for debugging, for if you
 * inspect an element that is applying this style in your browser's dev tools, you'll see it applied like so:
 *
 * ```html
 * <div class="non-interactive">...</div>
 * ```
 *
 * This is much easier to understand at a glance than if all the styles were inlined directly into the HTML.
 *
 * You can also subclass [CssStyle] to create a new style that extends an existing one:
 *
 * ```
 * class WidgetSize(
 *     fontSize: CSSLengthNumericValue,
 *     height: CSSLengthNumericValue,
 * ) : CssStyle.Base(Modifier.fontSize(fontSize).height(height) ) {
 *     companion object {
 *         val XS = WidgetSize(...)
 *         val SM = WidgetSize(...)
 *         val MD = WidgetSize(...)
 *         val LG = WidgetSize(...)
 *     }
 * }
 * ```
 *
 * which here will create four classes: `widget-size_xs`, `widget-size_sm`, `widget-size_md`, and `widget-size_lg`.
 * At this point, you can take `Widget` as a method parameter:
 *
 * ```
 * @Composable
 * fun Widget(..., size: WidgetSize, ...) {
 *   val modifier = WidgetStyle.toModifier().then(size.toModifier())
 *   ...
 * }
 * ```
 *
 * resulting in an element like `<div class="widget widget-size_md">...</div>`.
 */
abstract class CssStyle protected constructor(
    internal val init: ComponentModifiers.() -> Unit,
    internal val extraModifiers: @Composable () -> Modifier = { Modifier },
) {
    /**
     * A [CssStyle] when you know you only want to specify the base style, and not any other modifiers like hover.
     */
    abstract class Base protected constructor(
        init: ComponentBaseModifier.() -> Modifier,
        extraModifiers: @Composable () -> Modifier = { Modifier },
    ) : CssStyle({ base { ComponentBaseModifier(colorMode).init() } }, extraModifiers) {
        constructor(init: Modifier, extraModifiers: @Composable () -> Modifier = { Modifier }) : this(
            { init },
            extraModifiers
        )
    }

    /**
     * @param cssRule A selector plus an optional pseudo keyword (e.g. "a", "a:link", and "a::selection")
     */
    private fun <T : StyleScope> GenericStyleSheetBuilder<T>.addStyles(cssRule: String, styles: ComparableStyleScope) {
        cssRule style {
            styles.properties.forEach { entry -> property(entry.key, entry.value) }
            styles.variables.forEach { entry -> variable(entry.key, entry.value) }
        }
    }

    /**
     * Shared logic for using an initial selector name and triggering a callback with the final selector name and
     * CSS styles to be associated with it.
     */
    private fun withFinalSelectorName(
        selectorBaseName: String,
        group: StyleGroup,
        handler: (String, ComparableStyleScope) -> Unit
    ) {
        when (group) {
            is StyleGroup.Light -> handler(selectorBaseName.suffixedWith(ColorMode.LIGHT), group.styles)
            is StyleGroup.Dark -> handler(selectorBaseName.suffixedWith(ColorMode.DARK), group.styles)
            is StyleGroup.ColorAgnostic -> handler(selectorBaseName, group.styles)
            is StyleGroup.ColorAware -> {
                handler(selectorBaseName.suffixedWith(ColorMode.LIGHT), group.lightStyles)
                handler(selectorBaseName.suffixedWith(ColorMode.DARK), group.darkStyles)
            }
        }
    }

    // Collect all CSS selectors (e.g. all base, hover, breakpoints, etc. modifiers) and, if we ever find multiple
    // definitions for the same selector, just combine them together. One way this is useful is you can use
    // `MutableSilkTheme.modifyStyle` to layer additional styles on top of a base style. In almost all
    // practical cases, however, there will only ever be a single selector of each type per component style.
    private fun ComponentModifiers.mergeCssModifiers(init: ComponentModifiers.() -> Unit): Map<CssModifier.Key, CssModifier> {
        return apply(init).cssModifiers
            .groupBy { it.key }
            .mapValues { (_, group) ->
                group.reduce { acc, curr -> acc.mergeWith(curr) }
            }
    }

    private fun Map<CssModifier.Key, CssModifier>.assertNoAttributeModifiers(selectorName: String): Map<CssModifier.Key, CssModifier> {
        return this.onEach { (_, cssModifier) ->
            val attrsScope = ComparableAttrsScope<Element>()
            cssModifier.modifier.toAttrs<AttrsScope<Element>>().invoke(attrsScope)
            if (attrsScope.attributes.isEmpty()) return@onEach

            error(buildString {
                appendLine("ComponentStyle declarations cannot contain Modifiers that specify attributes. Please move Modifiers associated with attributes to the ComponentStyle's `extraModifiers` parameter.")
                appendLine()
                appendLine("Details:")

                append("\tCSS rule: ")
                append("\"$selectorName")
                if (cssModifier.mediaQuery != null) append(cssModifier.mediaQuery)
                if (cssModifier.suffix != null) append(cssModifier.suffix)
                append("\"")

                append(" (do you declare a property called ")
                // ".example" likely comes from `ExampleStyle` while ".example.example-outlined" likely
                // comes from ExampleOutlinedVariant or OutlinedExampleVariant
                val isStyle = selectorName.count { it == '.' } == 1// "Variant" else "Style"
                val styleName = selectorName.substringAfter(".").substringBefore(".")

                if (isStyle) {
                    append("`${styleName.kebabCaseToTitleCamelCase()}Style`")
                } else {
                    // Convert ".example.example-outlined" to "outlined". This could come from a variant
                    // property called OutlinedExampleVariant or ExampleOutlinedVariant
                    val variantPart = selectorName.substringAfterLast(".").removePrefix("$styleName-")
                    append("`${"$styleName-$variantPart".kebabCaseToTitleCamelCase()}Variant`")
                    append(" or ")
                    append("`${"$variantPart-$styleName".kebabCaseToTitleCamelCase()}Variant`")
                }
                appendLine("?)")
                appendLine("\tAttribute(s): ${attrsScope.attributes.keys.joinToString(", ") { "\"$it\"" }}")
                appendLine()
                appendLine("An example of how to fix this:")
                appendLine(
                    """
                    |   // Before
                    |   val ExampleStyle by ComponentStyle {
                    |       base {
                    |          Modifier
                    |              .backgroundColor(Colors.Magenta))
                    |              .tabIndex(0) // <-- The offending attribute modifier
                    |       }
                    |   }
                    |   
                    |   // After
                    |   val ExampleStyle by ComponentStyle(extraModifiers = Modifier.tabIndex(0)) {
                    |       base {
                    |           Modifier.backgroundColor(Colors.Magenta)
                    |       }
                    |   }
                    """.trimMargin()
                )
            })
        }
    }

    /**
     * Adds styles into the given stylesheet for the specified selector.
     *
     * @return The CSS class selectors that were added to the stylesheet, always including the base class, and
     *  potentially additional classes if the style is color mode aware. This lets us avoid applying unnecessary
     *  classnames, making it easier to debug CSS issues in the browser.
     */
    internal fun addStylesInto(selector: String, styleSheet: StyleSheet): ClassSelectors {
        // Always add the base selector name, even if the ComponentStyle is empty. Callers may use empty
        // component styles as classnames, which can still be useful for targeting one element from another, or
        // searching for all elements tagged with a certain class.
        val classNames = mutableListOf(selector)

        val lightModifiers = ComponentModifiers(ColorMode.LIGHT).mergeCssModifiers(init)
            .assertNoAttributeModifiers(selector)
        val darkModifiers = ComponentModifiers(ColorMode.DARK).mergeCssModifiers(init)
            .assertNoAttributeModifiers(selector)

        StyleGroup.from(lightModifiers[CssModifier.BaseKey]?.modifier, darkModifiers[CssModifier.BaseKey]?.modifier)
            ?.let { group ->
                withFinalSelectorName(selector, group) { name, styles ->
                    if (styles.isNotEmpty()) {
                        classNames.add(name)
                        styleSheet.addStyles(name, styles)
                    }
                }
            }

        val allCssRuleKeys = (lightModifiers.keys + darkModifiers.keys).filter { it != CssModifier.BaseKey }
        for (cssRuleKey in allCssRuleKeys) {
            val group = StyleGroup.from(lightModifiers[cssRuleKey]?.modifier, darkModifiers[cssRuleKey]?.modifier)
                ?: continue
            withFinalSelectorName(selector, group) { name, styles ->
                if (styles.isNotEmpty()) {
                    classNames.add(name)

                    val cssRule = "$name${cssRuleKey.suffix.orEmpty()}"
                    if (cssRuleKey.mediaQuery != null) {
                        styleSheet.apply {
                            media(cssRuleKey.mediaQuery) {
                                addStyles(cssRule, styles)
                            }
                        }
                    } else {
                        styleSheet.addStyles(cssRule, styles)
                    }
                }
            }
        }
        return ClassSelectors(classNames)
    }

    internal fun intoImmutableStyle(classSelectors: ClassSelectors) =
        ImmutableCssStyle(classSelectors, extraModifiers)

    @Composable
    fun toModifier(): Modifier = SilkTheme.cssStyles.getValue(this).toModifier()

    companion object // for extensions
}

/**
 * A basic [CssStyle] implementation associated with a CSS selector value.
 */
internal class SimpleCssStyle(
    val selector: String,
    init: ComponentModifiers.() -> Unit,
    extraModifiers: @Composable () -> Modifier,
) : CssStyle(init, extraModifiers) {
    internal fun addStylesInto(styleSheet: StyleSheet): ClassSelectors {
        return addStylesInto(selector, styleSheet)
    }
}

/**
 * A [CssStyle] pared down to read-only data only, which should happen shortly after Silk initializes.
 *
 * @param classSelectors The CSS class selectors associated with this style, including the base class and any
 *  color mode specific classes, used to determine the exact classnames to apply when this style is used.
 * @param extraModifiers Additional modifiers that can be tacked onto this component style, convenient for including
 *   non-style attributes whenever this style is applied.
 */
internal class ImmutableCssStyle(
    classSelectors: ClassSelectors,
    private val extraModifiers: @Composable () -> Modifier
) {
    private val classNames = classSelectors.classNames.toSet()

    @Composable
    fun toModifier(): Modifier {
        val currentClassNames = classNames.filterNot { it.endsWith(ColorMode.current.opposite.name.lowercase()) }
        return (if (currentClassNames.isNotEmpty()) Modifier.classNames(*currentClassNames.toTypedArray()) else Modifier)
            .then(extraModifiers())
    }
}

/**
 * State specific to [ComponentStyle] initialization but not the more general [StyleModifiers] case.
 *
 * For example, color mode is supported here:
 *
 * ```
 * val MyWidgetStyle by ComponentStyle {
 *    ...
 * }
 * ```
 *
 * but not here:
 *
 * ```
 * @InitSilk
 * fun initSilk(ctx: InitSilkContext) {
 *   ctx.stylesheet.registerStyle("body") {
 *     ...
 *   }
 * }
 * ```
 */
interface ComponentModifier {
    /**
     * The current color mode, which may impact the look and feel of the current component style.
     */
    val colorMode: ColorMode
}

class ComponentModifiers internal constructor(override val colorMode: ColorMode) : ComponentModifier, StyleModifiers()

/**
 * Class provided for cases where you only generate a single style (e.g. base), unlike [ComponentModifiers] where you
 * can define a collection of styles.
 */
class ComponentBaseModifier internal constructor(override val colorMode: ColorMode) : ComponentModifier

private sealed interface StyleGroup {
    class Light(val styles: ComparableStyleScope) : StyleGroup
    class Dark(val styles: ComparableStyleScope) : StyleGroup
    class ColorAgnostic(val styles: ComparableStyleScope) : StyleGroup
    class ColorAware(val lightStyles: ComparableStyleScope, val darkStyles: ComparableStyleScope) : StyleGroup

    companion object {
        @Suppress("NAME_SHADOWING") // Shadowing used to turn nullable into non-null
        fun from(lightModifiers: Modifier?, darkModifiers: Modifier?): StyleGroup? {
            val lightStyles = lightModifiers?.let { lightModifiers ->
                ComparableStyleScope().apply { lightModifiers.toStyles().invoke(this) }
            }
            val darkStyles = darkModifiers?.let { darkModifiers ->
                ComparableStyleScope().apply { darkModifiers.toStyles().invoke(this) }
            }

            if (lightStyles == null && darkStyles == null) return null
            if (lightStyles != null && darkStyles == null) return Light(lightStyles)
            if (lightStyles == null && darkStyles != null) return Dark(darkStyles)
            check(lightStyles != null && darkStyles != null)
            return if (lightStyles == darkStyles) {
                ColorAgnostic(lightStyles)
            } else {
                ColorAware(lightStyles, darkStyles)
            }
        }
    }
}

fun CssStyle(extraModifiers: Modifier = Modifier, init: ComponentModifiers.() -> Unit) =
    object : CssStyle(init, { extraModifiers }) {}

fun CssStyle(
    extraModifiers: @Composable () -> Modifier,
    init: ComponentModifiers.() -> Unit
) = object : CssStyle(init, extraModifiers) {}

fun CssStyle.Companion.base(
    extraModifiers: Modifier = Modifier,
    init: ComponentBaseModifier.() -> Modifier
) = base({ extraModifiers }, init)

fun CssStyle.Companion.base(
    extraModifiers: @Composable () -> Modifier,
    init: ComponentBaseModifier.() -> Modifier
) = object : CssStyle(init = { base { ComponentBaseModifier(colorMode).let(init) } }, extraModifiers) {}
