package com.varabyte.kobweb.compose.ui.modifiers

import com.varabyte.kobweb.compose.css.*
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.styleModifier
import org.jetbrains.compose.web.css.*
class ColumnRuleScope internal constructor(private val styleScope: StyleScope) {
    fun color(color: CSSColorValue) = styleScope.borderColor(color)
    fun style(lineStyle: LineStyle) = styleScope.borderStyle(lineStyle)
    fun width(width: CSSLengthNumericValue) = styleScope.borderWidth(width)
    fun width(topAndBottom: CSSLengthNumericValue, leftAndRight: CSSLengthNumericValue) =
        styleScope.borderWidth(topAndBottom, leftAndRight)

    fun width(top: CSSLengthNumericValue, leftAndRight: CSSLengthNumericValue, bottom: CSSLengthNumericValue) =
        styleScope.borderWidth(top, leftAndRight, bottom)

    fun width(
        top: CSSLengthNumericValue,
        right: CSSLengthNumericValue,
        bottom: CSSLengthNumericValue,
        left: CSSLengthNumericValue
    ) =
        styleScope.borderWidth(top, right, bottom, left)
}

fun Modifier.columnRule(scope: ColumnRuleScope.() -> Unit) = styleModifier {
    ColumnRuleScope(this).apply(scope)
}

fun Modifier.columnRule(
    width: CSSLengthNumericValue? = null,
    style: LineStyle? = null,
    color: CSSColorValue? = null
) = styleModifier {
    columnRule(width, style, color)
}