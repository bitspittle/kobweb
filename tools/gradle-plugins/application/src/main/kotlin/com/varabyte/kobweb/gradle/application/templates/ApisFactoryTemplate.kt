package com.varabyte.kobweb.gradle.application.templates

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.varabyte.kobweb.project.backend.AppBackendData

fun createApisFactoryImpl(appBackendData: AppBackendData): String {
    // Final code should look something like:
    //
    // class ApisFactoryImpl : ApisFactory {
    //    override fun create(env: Environment, events: Events, logger: Logger): Apis {
    //        val data = MutableData()
    //        val apis = Apis(env, data, logger, apiInterceptor = { ctx -> example.api.interceptRequest(ctx) })
    //        apis.register("/add") { ctx -> example.api.add(ctx) }
    //        apis.register("/remove") { ctx -> example.api.remove(ctx) }
    //        apis.registerStream("/echo") { ctx -> example.api.echo }
    //        apis.registerStream("/chat") { ctx -> example.api.chat }
    //        val initCtx = InitApiContext(env, apis, data, events, logger)
    //        example.init(initCtx)
    //        return apis
    //    }
    //  }

    val fileBuilder = FileSpec.builder("", "ApisFactoryImpl").indent(" ".repeat(4))

    val apiPackage = "com.varabyte.kobweb.api"
    val classApis = ClassName(apiPackage, "Apis")
    val classApisFactory = ClassName(apiPackage, "ApisFactory")
    val classEnvironment = ClassName("$apiPackage.env", "Environment")
    val classMutableData = ClassName("$apiPackage.data", "MutableData")
    val classEvents = ClassName("$apiPackage.event", "Events")
    val classInitApiContext = ClassName("$apiPackage.init", "InitApiContext")
    val classLogger = ClassName("$apiPackage.log", "Logger")
    val backendData = appBackendData.backendData

    fileBuilder.addType(
        TypeSpec.classBuilder("ApisFactoryImpl")
            .addSuperinterface(classApisFactory)
            .addFunction(
                FunSpec.builder("create")
                    .addModifiers(KModifier.OVERRIDE)
                    .addParameter(ParameterSpec("env", classEnvironment))
                    .addParameter(ParameterSpec("events", classEvents))
                    .addParameter(ParameterSpec("logger", classLogger))
                    .returns(classApis)
                    .addCode(CodeBlock.builder().apply {
                        addStatement("val data = %T()", classMutableData)
                        addStatement(
                            buildString {
                                append("val apis = %T(env, data, logger")
                                appBackendData.apiInterceptorMethod?.let { requestInterceptorMethod ->
                                    append(", apiInterceptor = { ctx -> ${requestInterceptorMethod.fqn}(ctx) }")
                                }
                                append(")")
                            },
                            classApis
                        )
                        backendData.apiMethods.sortedBy { entry -> entry.route }.forEach { entry ->
                            addStatement("apis.register(%S) { ctx -> ${entry.fqn}(ctx) }", entry.route)
                        }
                        backendData.apiStreamMethods.sortedBy { entry -> entry.route }.forEach { entry ->
                            addStatement("apis.registerStream(%S, ${entry.fqn})", entry.route)
                        }
                        if (backendData.initMethods.isNotEmpty()) {
                            addStatement("val initCtx = %T(env, apis, data, events, logger)", classInitApiContext)
                            backendData.initMethods.sortedBy { entry -> entry.fqn }.forEach { entry ->
                                addStatement("${entry.fqn}(initCtx)")
                            }
                        }
                        addStatement("")
                        addStatement("return apis")
                    }.build())
                    .build()
            )
            .build()
    )

    return fileBuilder.build().toString()
}
