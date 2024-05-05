package com.varabyte.kobweb.silk.style

import androidx.compose.runtime.*
import com.varabyte.kobweb.browser.util.kebabCaseToTitleCamelCase
import com.varabyte.kobweb.compose.attributes.ComparableAttrsScope
import com.varabyte.kobweb.compose.css.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.compose.ui.toAttrs
import com.varabyte.kobweb.compose.ui.toStyles
import com.varabyte.kobweb.silk.theme.SilkTheme
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.suffixedWith
import org.jetbrains.compose.web.attributes.AttrsScope
import org.jetbrains.compose.web.css.*
import org.w3c.dom.Element
import org.jetbrains.compose.web.css.StyleScope as JbStyleScope

// TODO: Docs
sealed interface CssKind

// TODO: Docs
sealed interface RestrictedKind : CssKind

// TODO: Docs
sealed interface UnspecifiedKind : CssKind

// TODO: Docs
interface ComponentKind : CssKind

/** Represents a list of CSS selectors that target classes. */
internal value class ClassSelectors(private val value: List<String>) {
    // Selectors may be ".someStyle" or ".someStyle.someVariant" - only the last part is relevant to the specific style
    val classNames get() = value.map { it.substringAfterLast('.') }
    operator fun plus(other: ClassSelectors) = ClassSelectors(value + other.value)
}

/**
 * A class which allows a user to define styles that will be added into the site's CSS stylesheet.
 *
 * This is important because some functionality is only available when defined in the stylesheet, e.g. link colors,
 * media queries, and pseudo classes.
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
 * You can also subclass [CssStyle] to create a way to limit the parameters that users can specify in order to generate
 * a style:
 *
 * ```
 * class WidgetSize(
 *     fontSize: CSSLengthNumericValue,
 *     height: CSSLengthNumericValue,
 * ) : CssStyle.Restricted.Base(Modifier.fontSize(fontSize).height(height) ) {
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
 *
 * This is a particularly useful pattern for when you want to allow users to pass in a parameter into a method that you
 * provide:
 *
 * ```
 * @Composable
 * fun Widget(..., size: WidgetSize, ...) {
 *   val modifier = WidgetStyle.toModifier().then(size.toModifier())
 *   ...
 * }
 * ```
 *
 * which would result in an element like `<div class="widget widget-size_md">...</div>`.
 */
abstract class CssStyle<K : CssKind> internal constructor(
    internal val init: CssStyleScope.() -> Unit,
    internal val extraModifier: @Composable () -> Modifier = { Modifier },
) {
    /**
     * A [CssStyle] for creating custom style types restricted to fixed parameters.
     *
     * Whereas CSS styles are by default open-ended and let you define any combination of modifiers that you want, it
     * can sometimes be useful to present a user with a constructor of fixed parameters, having the style then created
     * for it behind the scenes.
     *
     * For example:
     *
     * ```
     * class MyButtonBehavior(fontSize: CSSLengthNumericValue, hoverColor: CSSColorValue) : CssStyle.Restricted(init = {
     *   base { Modifier.fontSize(fontSize) }
     *   hover { Modifier.backgroundColor(hoverColor) }
     * }) {
     *   companion object {
     *     val Quiet = MyButtonBehavior(1.cssRem, Colors.Gray)
     *     val Loud = MyButtonBehavior(2.cssRem, Colors.Red)
     *   }
     * }
     * ```
     *
     * A user can declare an instance of this class in their own code:
     *
     * ```
     * val UserButtonBehavior = MyButtonBehavior(1.5.cssRem, Colors.Blue)
     * ```
     *
     * which will automatically create a CSS class corresponding to the property.
     */
    abstract class Restricted(
        init: CssStyleScope.() -> Unit,
        extraModifier: @Composable () -> Modifier = { Modifier },
    ) : CssStyle<RestrictedKind>(init, extraModifier) {
        /**
         * Like [Restricted] but when you know you only want to specify the base style.
         */
        abstract class Base(
            init: CssStyleBaseScope.() -> Modifier,
            extraModifier: @Composable () -> Modifier = { Modifier },
        ) : Restricted({ base { CssStyleBaseScope(colorMode).init() } }, extraModifier) {
            constructor(init: Modifier, extraModifier: @Composable () -> Modifier = { Modifier }) : this(
                { init },
                extraModifier
            )
        }
    }

    /**
     * @param cssRule A selector plus an optional pseudo keyword (e.g. "a", "a:link", and "a::selection")
     */
    private fun <T : JbStyleScope> GenericStyleSheetBuilder<T>.addStyles(
        cssRule: String,
        styles: ComparableStyleScope
    ) {
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
    private fun CssStyleScope.mergeCssModifiers(init: CssStyleScope.() -> Unit): Map<CssModifier.Key, CssModifier> {
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
                appendLine("ComponentStyle declarations cannot contain Modifiers that specify attributes. Please move Modifiers associated with attributes to the ComponentStyle's `extraModifier` parameter.")
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
                val isStyle = selectorName.count { it == '.' } == 1
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
                    |   val ExampleStyle by ComponentStyle(extraModifier = Modifier.tabIndex(0)) {
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

        val lightModifiers = CssStyleScope(ColorMode.LIGHT).mergeCssModifiers(init)
            .assertNoAttributeModifiers(selector)
        val darkModifiers = CssStyleScope(ColorMode.DARK).mergeCssModifiers(init)
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
        ImmutableCssStyle(classSelectors, extraModifier)

    companion object // for extensions
}

/**
 * A basic [CssStyle] implementation associated with a CSS selector value.
 */
internal class SimpleCssStyle(
    val selector: String,
    init: CssStyleScope.() -> Unit,
    extraModifier: @Composable () -> Modifier,
) : CssStyle<UnspecifiedKind>(init, extraModifier) {
    internal fun addStylesInto(styleSheet: StyleSheet): ClassSelectors {
        return addStylesInto(selector, styleSheet)
    }
}

/**
 * A [CssStyle] that, when applied, should always ALSO apply the style it depends on and layer on top of it.
 *
 * This may seem similar to defining variants for [ComponentKind] styles, except this doesn't enforce type safety, which
 * variants are good for. Also, this is more a case where the user will interact with the extended CSS style directly,
 * vs. variants where the user will interact with the base style and then specify zero or more variants on top of it.
 *
 * ```
 * // Extending:
 * val BaseStyle = CssStyle { ... }
 * val ExtendedStyle = BaseStyle.extended { ... }
 *
 * Box(ExtendedStyle.toModifier()) // includes BaseStyle automatically
 *
 * // Variants:
 * sealed interface LabelKind : ComponentKind
 * val LabelStyle = CssStyle<LabelKind> { ... }
 * val BoldLabelVariant = LabelStyle.addVariant { ... }
 * val ItalicLabelVariant = LabelStyle.addVariant { ... }
 *
 * Box(LabelStyle.toModifier(BoldLabelVariant, ItalicLabelVariant))
 * ```
 *
 * Note: The `ComponentKind` interface you create does not HAVE to be sealed. However, it's a recommended best practice
 * as a way to indicate your intention that this interface is unique and tied specifically to a CSS style.
 */
internal class ExtendingCssStyle(
    init: CssStyleScope.() -> Unit,
    extraModifier: @Composable () -> Modifier,
    val baseStyle: CssStyle<UnspecifiedKind>
) : CssStyle<UnspecifiedKind>(init, extraModifier = {
    var currBaseStyle: CssStyle<UnspecifiedKind>? = baseStyle
    var finalModifier = extraModifier()
    // Handle "extended" chains (like Base <- Extended <- MoreExtended; MoreExtended should include both)
    while (currBaseStyle != null) {
        finalModifier = finalModifier.then(currBaseStyle!!.toModifier())
        currBaseStyle = (currBaseStyle as? ExtendingCssStyle)?.baseStyle
    }
    finalModifier
})

/**
 * A [CssStyle] pared down to read-only data only, which should happen shortly after Silk initializes.
 *
 * @param classSelectors The CSS class selectors associated with this style, including the base class and any
 *  color mode specific classes, used to determine the exact classnames to apply when this style is used.
 * @param extraModifier Additional modifiers that can be tacked onto this component style, convenient for including
 *   non-style attributes whenever this style is applied.
 */
internal class ImmutableCssStyle(
    classSelectors: ClassSelectors,
    private val extraModifier: @Composable () -> Modifier
) {
    private val classNames = classSelectors.classNames.toSet()

    @Composable
    fun toModifier(): Modifier {
        val currentClassNames = classNames.filterNot { it.endsWith(ColorMode.current.opposite.name.lowercase()) }
        return (if (currentClassNames.isNotEmpty()) Modifier.classNames(*currentClassNames.toTypedArray()) else Modifier)
            .then(extraModifier())
    }
}

/**
 * Properties that all css style scope implementations promise to support.
 *
 * Note: the "Base" suffix here is different from the concept of a base style (e.g. base `{ ... }`). This is confusing
 * and we, the authors of the codebase, express our deepest apologies! In practice, this class will never be imported
 * directly by user code.
 */
interface CssStyleScopeBase {
    val colorMode: ColorMode
}

/**
 * An extension to [StyleScope] which adds extra information only relevant to [CssStyle] blocks.
 *
 * For example, color mode is supported here:
 *
 * ```
 * val MyWidgetStyle = CssStyle {
 *    if (colorMode.isDark()) { ... }
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
class CssStyleScope internal constructor(override val colorMode: ColorMode) : CssStyleScopeBase, StyleScope()

/**
 * A simplified subset of [CssStyleScope].
 *
 * This scope is provided for cases where you only generate a single style (e.g. base), unlike [CssStyleScope] where you
 * can define a collection of styles.
 */
class CssStyleBaseScope internal constructor(override val colorMode: ColorMode) : CssStyleScopeBase

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

fun CssStyle(extraModifier: Modifier = Modifier, init: CssStyleScope.() -> Unit) =
    object : CssStyle<UnspecifiedKind>(init, { extraModifier }) {}

fun CssStyle(
    extraModifier: @Composable () -> Modifier,
    init: CssStyleScope.() -> Unit
) = object : CssStyle<UnspecifiedKind>(init, extraModifier) {}

fun CssStyle.Companion.base(
    extraModifier: Modifier = Modifier,
    init: CssStyleBaseScope.() -> Modifier
) = base({ extraModifier }, init)

fun CssStyle.Companion.base(
    extraModifier: @Composable () -> Modifier,
    init: CssStyleBaseScope.() -> Modifier
) = object : CssStyle<UnspecifiedKind>(init = { base { CssStyleBaseScope(colorMode).let(init) } }, extraModifier) {}

fun <K : ComponentKind> CssStyle(extraModifier: Modifier = Modifier, init: CssStyleScope.() -> Unit) =
    object : CssStyle<K>(init, { extraModifier }) {}

fun <K : ComponentKind> CssStyle(
    extraModifier: @Composable () -> Modifier,
    init: CssStyleScope.() -> Unit
) = object : CssStyle<K>(init, extraModifier) {}

fun <K : ComponentKind> CssStyle.Companion.base(
    extraModifier: Modifier = Modifier,
    init: CssStyleBaseScope.() -> Modifier
) = base<K>({ extraModifier }, init)

fun <K : ComponentKind> CssStyle.Companion.base(
    extraModifier: @Composable () -> Modifier,
    init: CssStyleBaseScope.() -> Modifier
) = CssStyle<K>(extraModifier) { base { CssStyleBaseScope(colorMode).let(init) } }

fun CssStyle<UnspecifiedKind>.extendedBy(extraModifier: Modifier = Modifier, init: CssStyleScope.() -> Unit) =
    extendedBy({ extraModifier }, init)

fun CssStyle<UnspecifiedKind>.extendedBy(
    extraModifier: @Composable () -> Modifier,
    init: CssStyleScope.() -> Unit
): CssStyle<UnspecifiedKind> = ExtendingCssStyle(init, extraModifier, this)

fun CssStyle<UnspecifiedKind>.extendedByBase(
    extraModifier: Modifier = Modifier,
    init: CssStyleBaseScope.() -> Modifier
) =
    extendedByBase({ extraModifier }, init)

fun CssStyle<UnspecifiedKind>.extendedByBase(
    extraModifier: @Composable () -> Modifier,
    init: CssStyleBaseScope.() -> Modifier
) = extendedBy(extraModifier) {
    base { CssStyleBaseScope(colorMode).let(init) }
}

@Suppress("FunctionName") // Inline so it can be called from @Composable methods
@Composable
private fun CssStyle<*>._toModifier(): Modifier = SilkTheme.cssStyles.getValue(this).toModifier()

@Composable
fun CssStyle<UnspecifiedKind>.toModifier(): Modifier = _toModifier()

@Composable
fun <A : AttrsScope<*>> CssStyle<UnspecifiedKind>.toAttrs(finalHandler: (A.() -> Unit)? = null): A.() -> Unit {
    return this.toModifier().toAttrs(finalHandler)
}

@Composable
fun CssStyle<RestrictedKind>.toModifier(): Modifier = _toModifier()

@Composable
fun <K : ComponentKind> CssStyle<K>.toModifier(vararg variants: CssStyleVariant<K>?): Modifier {
    return _toModifier()
        .then(variants.toList().combine()?.toModifier() ?: Modifier)
}

@Composable
fun Iterable<CssStyle<UnspecifiedKind>>.toModifier(): Modifier {
    return fold<_, Modifier>(Modifier) { acc, style -> acc.then(style.toModifier()) }
}

@Composable
fun <A : AttrsScope<*>> Iterable<CssStyle<UnspecifiedKind>>.toAttrs(finalHandler: (A.() -> Unit)? = null): A.() -> Unit {
    return this.toModifier().toAttrs(finalHandler)
}
