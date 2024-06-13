package com.varabyte.kobweb.compose.css

import org.jetbrains.compose.web.css.*

sealed class OverscrollBehavior private constructor(private val value: String) : StylePropertyValue {
    override fun toString() = value

    sealed class SingleValue(value: String) : OverscrollBehavior(value)
    class RepeatableValue internal constructor(value: String) : SingleValue(value)
    private class Keyword(value: String) : SingleValue(value)
    private class TwoValue(x: RepeatableValue, y: RepeatableValue) : OverscrollBehavior("$x $y")

    companion object {
        // Keyword
        val Auto get() = RepeatableValue("auto")
        val Contain get() = RepeatableValue("contain")
        val None get() = RepeatableValue("none")

        fun of(x: RepeatableValue, y: RepeatableValue): OverscrollBehavior = TwoValue(x, y)

        // Global
        val Inherit: SingleValue get() = Keyword("inherit")
        val Initial: SingleValue get() = Keyword("initial")
        val Revert: SingleValue get() = Keyword("revert")
        val Unset: SingleValue get() = Keyword("unset")
    }
}

fun StyleScope.overscrollBehavior(overscrollBehavior: OverscrollBehavior) {
    property("overscroll-behavior", overscrollBehavior)
}

fun StyleScope.overscrollBehaviorBlock(overscrollBehavior: OverscrollBehavior.SingleValue) {
    property("overscroll-behavior-block", overscrollBehavior)
}

fun StyleScope.overscrollBehaviorInline(overscrollBehavior: OverscrollBehavior.SingleValue) {
    property("overscroll-behavior-inline", overscrollBehavior)
}

fun StyleScope.overscrollBehaviorX(overscrollBehavior: OverscrollBehavior.SingleValue) {
    property("overscroll-behavior-x", overscrollBehavior)
}

fun StyleScope.overscrollBehaviorY(overscrollBehavior: OverscrollBehavior.SingleValue) {
    property("overscroll-behavior-y", overscrollBehavior)
}

class ScrollBehavior private constructor(private val value: String) : StylePropertyValue {
    override fun toString() = value

    companion object {
        // Keyword
        val Auto get() = ScrollBehavior("auto")
        val Smooth get() = ScrollBehavior("smooth")

        // Global
        val Inherit get() = ScrollBehavior("inherit")
        val Initial get() = ScrollBehavior("initial")
        val Revert get() = ScrollBehavior("revert")
        val Unset get() = ScrollBehavior("unset")
    }
}

fun StyleScope.scrollBehavior(scrollBehavior: ScrollBehavior) {
    property("scroll-behavior", scrollBehavior)
}

// region Scroll snap
// See https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-snap-type
sealed class ScrollSnapType private constructor(private val value: String) : StylePropertyValue {
    override fun toString() = value

    private class Keyword(value: String) : ScrollSnapType(value)
    class Axis internal constructor(value: String) : ScrollSnapType(value)
    private class AxisWithStrictness(axis: Axis, strictness: Strictness) : ScrollSnapType("$axis $strictness")

    enum class Strictness : StylePropertyValue {
        Mandatory, Proximity;

        override fun toString() = name.lowercase()
    }

    companion object {
        // Keyword
        val None: ScrollSnapType get() = Keyword("none")

        // Axes
        val X get() = Axis("x")
        val Y get() = Axis("y")
        val Block get() = Axis("block")
        val Inline get() = Axis("inline")
        val Both get() = Axis("both")

        fun of(axis: Axis, strictness: Strictness): ScrollSnapType = AxisWithStrictness(axis, strictness)

        // Global
        val Inherit: ScrollSnapType get() = Keyword("inherit")
        val Initial: ScrollSnapType get() = Keyword("initial")
        val Revert: ScrollSnapType get() = Keyword("revert")
        val Unset: ScrollSnapType get() = Keyword("unset")
    }
}

class ScrollSnapAxis private constructor(private val value: String) : StylePropertyValue {
    override fun toString() = value

    companion object {
        // Keyword
        @Deprecated("Use `ScrollSnapType.X` instead.", ReplaceWith("ScrollSnapType.X"))
        val X get() = ScrollSnapAxis("x")

        @Deprecated("Use `ScrollSnapType.Y` instead.", ReplaceWith("ScrollSnapType.Y"))
        val Y get() = ScrollSnapAxis("y")

        @Deprecated("Use `ScrollSnapType.Block` instead.", ReplaceWith("ScrollSnapType.Block"))
        val Block get() = ScrollSnapAxis("block")

        @Deprecated("Use `ScrollSnapType.Inline` instead.", ReplaceWith("ScrollSnapType.Inline"))
        val Inline get() = ScrollSnapAxis("inline")

        @Deprecated("Use `ScrollSnapType.Both` instead.", ReplaceWith("ScrollSnapType.Both"))
        val Both get() = ScrollSnapAxis("both")
    }
}

class ScrollSnapMode private constructor(private val value: String) : StylePropertyValue {
    override fun toString() = value

    companion object {
        // Keyword
        @Deprecated(
            "Use `ScrollSnapType.Strictness.Mandatory` instead.",
            ReplaceWith("ScrollSnapType.Strictness.Mandatory")
        )
        val Mandatory get() = ScrollSnapMode("mandatory")

        @Deprecated(
            "Use `ScrollSnapType.Strictness.Proximity` instead.",
            ReplaceWith("ScrollSnapType.Strictness.Proximity")
        )
        val Proximity get() = ScrollSnapMode("proximity")
    }
}

fun StyleScope.scrollSnapType(scrollSnapType: ScrollSnapType) {
    property("scroll-snap-type", scrollSnapType)
}

@Deprecated("`ScrollSnapAxis` and `ScrollSnapMode` are deprecated. Use `ScrollSnapType.Axis` and `ScrollSnapType.Strictness` instead.")
fun StyleScope.scrollSnapType(axis: ScrollSnapAxis, mode: ScrollSnapMode? = null) {
    val value = buildString {
        append(axis.toString())
        if (mode != null) {
            append(' ')
            append(mode.toString())
        }
    }
    property("scroll-snap-type", value)
}
// endregion

// region Scroll padding

fun StyleScope.scrollPadding(
    top: CSSLengthOrPercentageNumericValue = 0.px,
    right: CSSLengthOrPercentageNumericValue = 0.px,
    bottom: CSSLengthOrPercentageNumericValue = 0.px,
    left: CSSLengthOrPercentageNumericValue = 0.px
) {
    property("scroll-padding", "$top $right $bottom $left")
}

fun StyleScope.scrollPadding(value: CSSLengthOrPercentageNumericValue) {
    property("scroll-padding", "$value")
}

fun StyleScope.scrollPaddingTop(value: CSSLengthOrPercentageNumericValue) {
    property("scroll-padding-top", value)
}

fun StyleScope.scrollPaddingRight(value: CSSLengthOrPercentageNumericValue) {
    property("scroll-padding-right", value)
}

fun StyleScope.scrollPaddingBottom(value: CSSLengthOrPercentageNumericValue) {
    property("scroll-padding-bottom", value)
}

fun StyleScope.scrollPaddingLeft(value: CSSLengthOrPercentageNumericValue) {
    property("scroll-padding-left", value)
}

fun StyleScope.scrollPaddingInline(vararg value: CSSLengthOrPercentageNumericValue) {
    property("scroll-padding-inline", value.joinToString(" "))
}

fun StyleScope.scrollPaddingInlineStart(value: CSSLengthOrPercentageNumericValue) {
    property("scroll-padding-inline-start", value)
}

fun StyleScope.scrollPaddingInlineEnd(value: CSSLengthOrPercentageNumericValue) {
    property("scroll-padding-inline-end", value)
}

fun StyleScope.scrollPaddingBlock(vararg value: CSSLengthOrPercentageNumericValue) {
    property("scroll-padding-block", value.joinToString(" "))
}

fun StyleScope.scrollPaddingBlockStart(value: CSSLengthOrPercentageNumericValue) {
    property("scroll-padding-block-start", value)
}

fun StyleScope.scrollPaddingBlockEnd(value: CSSLengthOrPercentageNumericValue) {
    property("scroll-padding-block-end", value)
}

// endregion

// region Scroll snap align

// See: https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-snap-align
sealed class ScrollSnapAlign private constructor(private val value: String) : StylePropertyValue {
    override fun toString() = value

    private class Keyword(value: String) : ScrollSnapAlign(value)
    class Alignment internal constructor(value: String) : ScrollSnapAlign(value)
    private class TwoAxis(block: Alignment, inline: Alignment) : ScrollSnapAlign("$block $inline")

    companion object {
        // Keyword
        val None get() = Alignment("none")
        val Start get() = Alignment("start")
        val End get() = Alignment("end")
        val Center get() = Alignment("center")

        fun of(blockAxis: Alignment, inlineAxis: Alignment): ScrollSnapAlign = TwoAxis(blockAxis, inlineAxis)

        // Global
        val Inherit: ScrollSnapAlign get() = Keyword("inherit")
        val Initial: ScrollSnapAlign get() = Keyword("initial")
        val Revert: ScrollSnapAlign get() = Keyword("revert")
        val Unset: ScrollSnapAlign get() = Keyword("unset")
    }
}

fun StyleScope.scrollSnapAlign(scrollSnapAlign: ScrollSnapAlign) {
    property("scroll-snap-align", scrollSnapAlign)
}

// endregion

// region Scroll snap stop

// See: https://developer.mozilla.org/en-US/docs/Web/CSS/scroll-snap-stop
class ScrollSnapStop private constructor(private val value: String) : StylePropertyValue {
    override fun toString() = value

    companion object {
        // Keyword
        val Normal get() = ScrollSnapStop("normal")
        val Always get() = ScrollSnapStop("always")

        // Global
        val Inherit get() = ScrollSnapStop("inherit")
        val Initial get() = ScrollSnapStop("initial")
        val Revert get() = ScrollSnapStop("revert")
        val Unset get() = ScrollSnapStop("unset")
    }
}

fun StyleScope.scrollSnapStop(scrollSnapStop: ScrollSnapStop) {
    property("scroll-snap-stop", scrollSnapStop)
}

// endregion

// region Scroll margin

fun StyleScope.scrollMargin(
    top: CSSLengthNumericValue = 0.px,
    right: CSSLengthNumericValue = 0.px,
    bottom: CSSLengthNumericValue = 0.px,
    left: CSSLengthNumericValue = 0.px
) {
    property("scroll-margin", "$top $right $bottom $left")
}

fun StyleScope.scrollMargin(vararg value: CSSLengthNumericValue) {
    property("scroll-margin", value.joinToString(" "))
}

fun StyleScope.scrollMarginTop(value: CSSLengthNumericValue) {
    property("scroll-margin-top", value)
}

fun StyleScope.scrollMarginRight(value: CSSLengthNumericValue) {
    property("scroll-margin-right", value)
}

fun StyleScope.scrollMarginBottom(value: CSSLengthNumericValue) {
    property("scroll-margin-bottom", value)
}

fun StyleScope.scrollMarginLeft(value: CSSLengthNumericValue) {
    property("scroll-margin-left", value)
}

fun StyleScope.scrollMarginInline(vararg value: CSSLengthNumericValue) {
    property("scroll-margin-inline", value.joinToString(" "))
}

fun StyleScope.scrollMarginInlineStart(value: CSSLengthNumericValue) {
    property("scroll-margin-inline-start", value)
}

fun StyleScope.scrollMarginInlineEnd(value: CSSLengthNumericValue) {
    property("scroll-margin-inline-end", value)
}

fun StyleScope.scrollMarginBlock(vararg value: CSSLengthNumericValue) {
    property("scroll-margin-block", value.joinToString(" "))
}

fun StyleScope.scrollMarginBlockStart(value: CSSLengthNumericValue) {
    property("scroll-margin-block-start", value)
}

fun StyleScope.scrollMarginBlockEnd(value: CSSLengthNumericValue) {
    property("scroll-margin-block-end", value)
}

// endregion
