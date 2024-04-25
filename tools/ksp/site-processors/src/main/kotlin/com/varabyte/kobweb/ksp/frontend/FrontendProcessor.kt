package com.varabyte.kobweb.ksp.frontend

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.varabyte.kobweb.common.text.camelCaseToKebabCase
import com.varabyte.kobweb.ksp.common.COMPONENT_STYLE_FQN
import com.varabyte.kobweb.ksp.common.COMPONENT_STYLE_SIMPLE_NAME
import com.varabyte.kobweb.ksp.common.COMPONENT_VARIANT_FQN
import com.varabyte.kobweb.ksp.common.COMPONENT_VARIANT_SIMPLE_NAME
import com.varabyte.kobweb.ksp.common.CSS_NAME_FQN
import com.varabyte.kobweb.ksp.common.CSS_PREFIX_FQN
import com.varabyte.kobweb.ksp.common.CSS_STYLE_BASE_FQN
import com.varabyte.kobweb.ksp.common.CSS_STYLE_BASE_SIMPLE_NAME
import com.varabyte.kobweb.ksp.common.CSS_STYLE_FQN
import com.varabyte.kobweb.ksp.common.CSS_STYLE_SIMPLE_NAME
import com.varabyte.kobweb.ksp.common.INIT_KOBWEB_FQN
import com.varabyte.kobweb.ksp.common.INIT_SILK_FQN
import com.varabyte.kobweb.ksp.common.KEYFRAMES_FQN
import com.varabyte.kobweb.ksp.common.KEYFRAMES_SIMPLE_NAME
import com.varabyte.kobweb.ksp.common.PACKAGE_MAPPING_PAGE_FQN
import com.varabyte.kobweb.ksp.common.PAGE_FQN
import com.varabyte.kobweb.ksp.common.getPackageMappings
import com.varabyte.kobweb.ksp.symbol.getAnnotationsByName
import com.varabyte.kobweb.ksp.symbol.resolveQualifiedName
import com.varabyte.kobweb.ksp.symbol.suppresses
import com.varabyte.kobweb.project.frontend.ComponentStyleEntry
import com.varabyte.kobweb.project.frontend.ComponentVariantEntry
import com.varabyte.kobweb.project.frontend.CssStyleEntry
import com.varabyte.kobweb.project.frontend.FrontendData
import com.varabyte.kobweb.project.frontend.InitKobwebEntry
import com.varabyte.kobweb.project.frontend.InitSilkEntry
import com.varabyte.kobweb.project.frontend.KeyframesEntry
import com.varabyte.kobweb.project.frontend.assertValid
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FrontendProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val genFile: String,
    private val qualifiedPagesPackage: String,
) : SymbolProcessor {
    private val frontendVisitor = FrontendVisitor() // ComponentStyle, ComponentVariant, Keyframes

    private val pageDeclarations = mutableListOf<KSFunctionDeclaration>()

    private val kobwebInits = mutableListOf<InitKobwebEntry>()
    private val silkInits = mutableListOf<InitSilkEntry>()
    private val cssStyles = mutableListOf<CssStyleEntry>()
    private val silkStyles = mutableListOf<ComponentStyleEntry>()
    private val silkVariants = mutableListOf<ComponentVariantEntry>()
    private val keyframesList = mutableListOf<KeyframesEntry>()

    // fqPkg to subdir, e.g. "blog._2022._01" to "01"
    private val packageMappings = mutableMapOf<String, String>()

    // We track all files we depend on so that ksp can perform smart recompilation
    // Even though our output is aggregating so generally requires full reprocessing, this at minimum means processing
    // will be skipped if the only change is deleted file(s) that we do not depend on.
    private val fileDependencies = mutableListOf<KSFile>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        kobwebInits += resolver.getSymbolsWithAnnotation(INIT_KOBWEB_FQN).map { annotatedFun ->
            fileDependencies.add(annotatedFun.containingFile!!)
            val name = (annotatedFun as KSFunctionDeclaration).qualifiedName!!.asString()
            InitKobwebEntry(name, acceptsContext = annotatedFun.parameters.size == 1)
        }

        silkInits += resolver.getSymbolsWithAnnotation(INIT_SILK_FQN).map { annotatedFun ->
            fileDependencies.add(annotatedFun.containingFile!!)
            val name = (annotatedFun as KSFunctionDeclaration).qualifiedName!!.asString()
            InitSilkEntry(name)
        }

        // only process files that are new to this round, since prior declarations are unchanged
        resolver.getNewFiles().forEach { file ->
            file.accept(frontendVisitor, Unit)

            // TODO: consider including this as part of the first visitor? does that matter for performance?
            packageMappings += getPackageMappings(file, qualifiedPagesPackage, PACKAGE_MAPPING_PAGE_FQN, logger).toMap()
                .also { if (it.isNotEmpty()) fileDependencies.add(file) }
        }

        pageDeclarations += resolver.getSymbolsWithAnnotation(PAGE_FQN).map { it as KSFunctionDeclaration }

        return emptyList()
    }

    private inner class FrontendVisitor : KSVisitorVoid() {
        private val cssStyleDeclaration = DeclarationType(
            name = CSS_STYLE_SIMPLE_NAME,
            qualifiedName = CSS_STYLE_FQN,
            displayString = "style rule",
            function = "ctx.theme.registerCssStyle",
        )
        private val styleDeclaration = DeclarationType(
            name = COMPONENT_STYLE_SIMPLE_NAME,
            qualifiedName = COMPONENT_STYLE_FQN,
            displayString = "component style",
            function = "ctx.theme.registerComponentStyle",
        )
        private val variantDeclaration = DeclarationType(
            name = COMPONENT_VARIANT_SIMPLE_NAME,
            qualifiedName = COMPONENT_VARIANT_FQN,
            displayString = "component variant",
            function = "ctx.theme.registerComponentVariants",
        )
        private val keyframesDeclaration = DeclarationType(
            name = KEYFRAMES_SIMPLE_NAME,
            qualifiedName = KEYFRAMES_FQN,
            displayString = "keyframes",
            function = "ctx.stylesheet.registerKeyframes",
        )
        private val declarations = listOf(styleDeclaration, variantDeclaration, keyframesDeclaration)

        val cssStyleClasses = mutableListOf<KSClassDeclaration>()
        val propertyDeclarations = mutableListOf<KSPropertyDeclaration>()

        override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
            propertyDeclarations.add(property)

            val type = property.type.toString()

            val matchingByProvider = declarations.firstOrNull { type == it.providerName }
            if (matchingByProvider != null) {
                logger.warn(
                    "Expected `by`, not assignment. Change \"val ${property.simpleName.asString()} = ...\" to \"val ${property.simpleName.asString()} by ...\"?",
                    property
                )
                return
            }

            val matchingByType = declarations.firstOrNull { type == it.name }
            if (matchingByType != null && !validateOrWarnAboutDeclaration(property, matchingByType)) {
                return
            }

            when (property.type.resolveQualifiedName()) {
                styleDeclaration.qualifiedName -> {
                    silkStyles.add(ComponentStyleEntry(property.qualifiedName!!.asString()))
                }

                variantDeclaration.qualifiedName -> {
                    silkVariants.add(ComponentVariantEntry(property.qualifiedName!!.asString()))
                }

                keyframesDeclaration.qualifiedName -> {
                    keyframesList.add(KeyframesEntry(property.qualifiedName!!.asString()))
                }

                cssStyleDeclaration.qualifiedName -> {
                    cssStyles.add(CssStyleEntry(property.qualifiedName!!.asString(), null))
                }

                else -> null
            }?.also { fileDependencies.add(property.containingFile!!) }
        }

        private fun validateOrWarnAboutDeclaration(
            property: KSPropertyDeclaration,
            declarationInfo: DeclarationType
        ): Boolean {
            val propertyName = property.simpleName.asString()
            if (property.parent !is KSFile) {
                val topLevelSuppression = "TOP_LEVEL_${declarationInfo.suppressionName}"
                if (!property.suppresses(topLevelSuppression)) {
                    logger.warn(
                        "Not registering ${declarationInfo.displayString} `val $propertyName`, as only top-level component styles are supported at this time. Although fixing this is recommended, you can manually register your ${declarationInfo.displayString} inside an @InitSilk block instead (`${declarationInfo.function}($propertyName)`). Suppress this message by adding a `@Suppress(\"$topLevelSuppression\")` annotation.",
                        property
                    )
                }
                return false
            }
            if (!property.isPublic()) {
                val privateSuppression = "PRIVATE_${declarationInfo.suppressionName}"
                if (!property.suppresses(privateSuppression)) {
                    logger.warn(
                        "Not registering ${declarationInfo.displayString} `val $propertyName`, as it is not public. Although fixing this is recommended, you can manually register your ${declarationInfo.displayString} inside an @InitSilk block instead (`${declarationInfo.function}($propertyName)`). Suppress this message by adding a `@Suppress(\"$privateSuppression\")` annotation.",
                        property
                    )
                }
                return false
            }
            return true
        }

        private inner class DeclarationType(
            val name: String,
            val qualifiedName: String,
            val displayString: String,
            val function: String,
            val providerName: String = "${name}Provider",
            val suppressionName: String = displayString.split(" ").joinToString("_") { it.uppercase() }
        )

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val isCssStyleClass = classDeclaration.superTypes
                .filter { it.toString() == CSS_STYLE_SIMPLE_NAME || it.toString() == CSS_STYLE_BASE_SIMPLE_NAME }
                .any {
                    val fqn = it.resolveQualifiedName()
                    fqn == CSS_STYLE_FQN || fqn == CSS_STYLE_BASE_FQN
                }
            if (isCssStyleClass) {
                cssStyleClasses.add(classDeclaration)
            }
            classDeclaration.declarations.forEach { it.accept(this, Unit) }
        }

        override fun visitFile(file: KSFile, data: Unit) {
            file.declarations.forEach { it.accept(this, Unit) }
        }
    }

    override fun finish() {
        val (path, extension) = genFile.split('.')
        val result = getProcessorResult()
        codeGenerator.createNewFileByPath(
            Dependencies(aggregating = true, *result.fileDependencies.toTypedArray()),
            path = path,
            extensionName = extension,
        ).writer().use { writer ->
            writer.write(Json.encodeToString<FrontendData>(result.data))
        }
    }

    /**
     * Get the finalized metadata acquired over all rounds of processing.
     *
     * This function should only be called from [SymbolProcessor.finish] as it relies on all rounds of processing being
     * complete.
     *
     * @return A [Result] containing the finalized [FrontendData] and the file dependencies that should be
     * passed in when using KSP's [CodeGenerator] to store the data.
     */
    fun getProcessorResult(): Result {
        val styleClassDeclarations = frontendVisitor.cssStyleClasses.groupBy { it.simpleName.asString() }

        cssStyles += frontendVisitor.propertyDeclarations.mapNotNull { property ->
            val potentialClasses = styleClassDeclarations[property.type.toString()].orEmpty()
            if (potentialClasses.none { it.qualifiedName?.asString() == property.type.resolveQualifiedName() }) {
                return@mapNotNull null
            }
            fileDependencies.add(property.containingFile!!)
            processCssStyle(property)
        }

        // pages are processed here as they rely on packageMappings, which may be populated over several rounds
        val pages = pageDeclarations.mapNotNull { annotatedFun ->
            processPagesFun(
                annotatedFun = annotatedFun,
                qualifiedPagesPackage = qualifiedPagesPackage,
                packageMappings = packageMappings,
                logger = logger,
            )?.also { fileDependencies.add(annotatedFun.containingFile!!) }
        }

        // TODO: maybe this should be in the gradle plugin(s) itself to also account for library+site definitions?
        val finalRouteNames = pages.map { page -> page.route }.toSet()
        pages.forEach { page ->
            if (page.route.endsWith('/')) {
                val withoutSlashSuffix = page.route.removeSuffix("/")
                if (finalRouteNames.contains(withoutSlashSuffix)) {
                    logger.warn("Your site defines both \"$withoutSlashSuffix\" and \"${page.route}\", which may be confusing to users. Unless this was intentional, removing one or the other is suggested.")
                }
            }
        }

        val frontendData = FrontendData(
            pages,
            kobwebInits,
            silkInits,
            silkStyles,
            silkVariants,
            keyframesList,
            cssStyles,
        ).also {
            it.assertValid(throwError = { msg -> logger.error(msg) })
        }

        return Result(frontendData, fileDependencies)
    }

    /**
     * Represents the result of [FrontendProcessor]'s processing, consisting of the generated [FrontendData] and the
     * files that contained relevant declarations.
     */
    data class Result(val data: FrontendData, val fileDependencies: List<KSFile>)
}

fun processCssStyle(property: KSPropertyDeclaration): CssStyleEntry {
    fun KSAnnotated.getAnnotationValue(fqn: String): String? {
        return this.getAnnotationsByName(fqn).firstOrNull()?.let { it.arguments.first().value.toString() }
    }

    val propertyCssName = property.getAnnotationValue(CSS_NAME_FQN)
        ?: property.simpleName.asString().titleCamelCaseToKebabCase()

    fun getCssStyleEntry(prefix: String?, containedName: String? = null): CssStyleEntry {
        val finalName = buildString {
            prefix?.let { append("$it-") }
            containedName?.let { append("${it}_") }
            append(propertyCssName)
        }
        return CssStyleEntry(property.qualifiedName!!.asString(), finalName)
    }

    val propertyPrefix = property.getAnnotationValue(CSS_PREFIX_FQN)

    val parent = property.parentDeclaration as? KSClassDeclaration
        ?: return getCssStyleEntry(propertyPrefix)

    val parentNameOverride = parent.getAnnotationValue(CSS_NAME_FQN)

    val containerName = if (parentNameOverride != null) {
        parentNameOverride
    } else {
        val relevantParent = if (parent.isCompanionObject) parent.parentDeclaration!! else parent
        relevantParent.simpleName.asString().titleCamelCaseToKebabCase()
    }

    return getCssStyleEntry(propertyPrefix ?: parent.getAnnotationValue(CSS_PREFIX_FQN), containerName)
}

/**
 * Convert a String for a name that is using TitleCamelCase into kebab-case.
 *
 * For example, "ExampleText" to "example-text"
 *
 * Same as [camelCaseToKebabCase], there is special handling for acronyms. See those docs for examples.
 */
// Note: There's really no difference between title case and camel case when going to kebab case, but both are
// provided for symmetry with the reverse methods, and also for expressing intention clearly.
// Note 2: Copied from frontend/browser-ext/.../util/StringExtensions.kt
fun String.titleCamelCaseToKebabCase() = camelCaseToKebabCase()
