package com.varabyte.kobweb.silk.init

import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.silk.components.document.TocBorderedVariant
import com.varabyte.kobweb.silk.components.document.TocStyle
import com.varabyte.kobweb.silk.components.graphics.FitWidthImageVariant
import com.varabyte.kobweb.silk.components.graphics.ImageStyle
import com.varabyte.kobweb.silk.components.navigation.AlwaysUnderlinedLinkVariant
import com.varabyte.kobweb.silk.components.navigation.LinkStyle
import com.varabyte.kobweb.silk.components.navigation.LinkVars
import com.varabyte.kobweb.silk.components.navigation.UncoloredLinkVariant
import com.varabyte.kobweb.silk.components.navigation.UndecoratedLinkVariant
import com.varabyte.kobweb.silk.theme.colors.palette.link
import com.varabyte.kobweb.silk.theme.colors.palette.toPalette
import com.varabyte.kobweb.silk.theme.modifyStyle

// Note: This expects to be called after `initSilkWidgets` is called first.
fun initSilkWidgetsKobweb(ctx: InitSilkContext) {
    val mutableTheme = ctx.theme

    mutableTheme.palettes.apply {
        light.apply {
            link.set(
                default = Colors.Blue,
                visited = Colors.Purple,
            )
        }
        dark.apply {
            link.set(
                default = Colors.Cyan,
                visited = Colors.Violet,
            )
        }
    }

    mutableTheme.modifyStyle(SilkColorsStyle) {
        val palette = colorMode.toPalette()
        Modifier
            .setVariable(LinkVars.DefaultColor, palette.link.default)
            .setVariable(LinkVars.VisitedColor, palette.link.visited)
    }

    // TODO: Automate the creation of this list (with a Gradle task?)

    mutableTheme.registerStyle(ImageStyle)
    mutableTheme.registerVariants(FitWidthImageVariant)

    mutableTheme.registerStyle(LinkStyle)
    mutableTheme.registerVariants(
        UncoloredLinkVariant,
        UndecoratedLinkVariant,
        AlwaysUnderlinedLinkVariant,
    )

    mutableTheme.registerStyle(TocStyle)
    mutableTheme.registerVariants(TocBorderedVariant)
}
