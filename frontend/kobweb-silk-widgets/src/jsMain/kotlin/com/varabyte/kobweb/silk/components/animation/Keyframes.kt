package com.varabyte.kobweb.silk.components.animation

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.AnimationIterationCount
import com.varabyte.kobweb.compose.css.CSSAnimation
import com.varabyte.kobweb.compose.css.ComparableStyleScope
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.toStyles
import com.varabyte.kobweb.silk.init.SilkStylesheet
import com.varabyte.kobweb.silk.theme.colors.ColorMode
import com.varabyte.kobweb.silk.theme.colors.rememberColorMode
import com.varabyte.kobweb.silk.theme.colors.suffixedWith
import com.varabyte.kobweb.silk.util.titleCamelCaseToKebabCase
import org.jetbrains.compose.web.css.*
import kotlin.reflect.KProperty

private val KeyframesBuilder.comparableKeyframeStyles get() = keyframeStyles.mapValues { (_, create) ->
    ComparableStyleScope().apply {
        create().toStyles().invoke(this)
    }
}

class KeyframesBuilder internal constructor(val colorMode: ColorMode) {
    internal val keyframeStyles = mutableMapOf<CSSKeyframe, () -> Modifier>()

    /** Describe the style of the element when this animation starts. */
    fun from(createStyle: () -> Modifier) {
        keyframeStyles += CSSKeyframe.From to createStyle
    }

    /** Describe the style of the element when this animation ends. */
    fun to(createStyle: () -> Modifier) {
        keyframeStyles += CSSKeyframe.To to createStyle
    }

    /** Describe the style of the element when the animation reaches some percent completion. */
    operator fun CSSSizeValue<CSSUnit.percent>.invoke(createStyle: () -> Modifier) {
        keyframeStyles += CSSKeyframe.Percentage(this) to createStyle
    }

    /**
     * A way to assign multiple percentage values with the same style.
     *
     * For example, this can be useful if you have an animation that changes, then stops for a bit, and then continues
     * to change again.
     *
     * ```
     * val Example by keyframes {
     *    from { Modifier.opacity(0) }
     *    each(20.percent, 80.percent) { Modifier.opacity(1) }
     *    to { Modifier.opacity(1) }
     * }
     * ```
     */
    fun each(vararg keys: CSSSizeValue<CSSUnit.percent>, createStyle: () -> Modifier) {
        keyframeStyles += CSSKeyframe.Combine(keys.toList()) to createStyle
    }

    override fun equals(other: Any?): Boolean {
        if (other !is KeyframesBuilder) return false
        return this === other || this.comparableKeyframeStyles == other.comparableKeyframeStyles
    }

    override fun hashCode(): Int {
        return comparableKeyframeStyles.hashCode()
    }

    internal fun addKeyframesIntoStylesheet(stylesheet: StyleSheet, keyframesName: String) {
        val keyframeRules = keyframeStyles.map { (keyframe, create) ->
            val styles = create().toStyles()

            val cssRuleBuilder = StyleScopeBuilder()
            styles.invoke(cssRuleBuilder)

            CSSKeyframeRuleDeclaration(keyframe, cssRuleBuilder)
        }

        stylesheet.add(CSSKeyframesRuleDeclaration(keyframesName, keyframeRules))
    }
}

/**
 * Define a set of keyframes that can later be references in animations.
 *
 * For example,
 *
 * ```
 * val Bounce = Keyframes("bounce") {
 *   from { Modifier.translateX((-50).percent) }
 *   to { Modifier.translateX((50).percent) }
 * }
 *
 * // Later
 * Div(
 *   Modifier
 *     .size(100.px).backgroundColor(Colors.Red)
 *     .animation(Bounce.toAnimation(
 *       duration = 2.s,
 *       timingFunction = AnimationTimingFunction.EaseIn,
 *       direction = AnimationDirection.Alternate,
 *       iterationCount = AnimationIterationCount.Infinite
 *     )
 *     .toAttrs()
 * )
 * ```
 *
 * Note: You should prefer to create keyframes using the [keyframes] delegate method to avoid needing to duplicate the
 * property name, e.g.
 *
 * ```
 * val Bounce by keyframes {
 *   from { Modifier.translateX((-50).percent) }
 *   to { Modifier.translateX((50).percent) }
 * }
 * ```
 *
 * If you are not using Kobweb, e.g. if you're using these widgets as a standalone library, you will have to use an
 * `@InitSilk` block to register your keyframes:
 *
 * ```
 * val Bounce = Keyframes("bounce") { ... }
 * @InitSilk
 * fun initSilk(ctx: InitSilkContext) {
 *   ctx.config.registerKeyframes(Bounce)
 * }
 * ```
 *
 * Otherwise, the Kobweb Gradle plugin will do this for you.
 */
class Keyframes(val name: String, internal val init: KeyframesBuilder.() -> Unit) {
    companion object {
        internal fun isColorModeAgnostic(build: KeyframesBuilder.() -> Unit): Boolean {
            // A user can use colorMode checks to change the keyframes builder, either by completely changing what sort
            // of keyframes show up across the light version and the dark version, or (more commonly) keeping the same
            // keyframes but changing some color values in the styles.
            return listOf(ColorMode.LIGHT, ColorMode.DARK)
                .map { colorMode -> KeyframesBuilder(colorMode).apply(build) }
                .distinct().count() == 1
        }
    }

    // Note: Need to postpone checking this value, because color modes aren't ready until after a certain point in
    // Silk's initialization.
    val usesColorMode by lazy { !isColorModeAgnostic(init) }
}

/**
 * A delegate provider class which allows you to create a [Keyframes] instance via the `by` keyword.
 */
class KeyframesProvider internal constructor(private val init: KeyframesBuilder.() -> Unit) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>
    ): Keyframes {
        val name = property.name.titleCamelCaseToKebabCase()
        return Keyframes(name, init)
    }
}

fun SilkStylesheet.registerKeyframes(keyframes: Keyframes) = registerKeyframes(keyframes.name, keyframes.init)

/**
 * Construct a [Keyframes] instance where the name comes from the variable name.
 *
 * For example,
 *
 * ```
 * val Bounce by keyframes { ... }
 * ```
 *
 * creates a keyframe entry into the site stylesheet (provided by Silk) with the name "bounce".
 *
 * Title camel case gets converted to snake case, so if the variable was called "AnimBounce", the final name added to
 * the style sheet would be "anim-bounce"
 *
 * Note: You can always construct a [Keyframes] object directly if you need to control the name, e.g.
 *
 * ```
 * // Renamed "Bounce" to "LegacyBounce" but don't want to break some old code.
 * val LegacyBounce = Keyframes("bounce") { ... }
 * ```
 */
fun keyframes(init: KeyframesBuilder.() -> Unit) = KeyframesProvider(init)

@Composable
fun Keyframes.toAnimation(
    duration: CSSSizeValue<out CSSUnitTime>? = null,
    timingFunction: AnimationTimingFunction? = null,
    delay: CSSSizeValue<out CSSUnitTime>? = null,
    iterationCount: AnimationIterationCount? = null,
    direction: AnimationDirection? = null,
    fillMode: AnimationFillMode? = null,
    playState: AnimationPlayState? = null
): CSSAnimation
{
    val finalName = if (this.usesColorMode) {
        this.name.suffixedWith(rememberColorMode().value)
    } else {
        this.name
    }

    return CSSAnimation(
        finalName,
        duration,
        timingFunction,
        delay,
        iterationCount,
        direction,
        fillMode,
        playState
    )
}
