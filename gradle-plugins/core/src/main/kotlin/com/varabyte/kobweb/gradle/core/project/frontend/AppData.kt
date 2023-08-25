package com.varabyte.kobweb.gradle.core.project.frontend

import com.varabyte.kobweb.gradle.core.project.common.KOBWEB_CORE_FQN_PREFIX
import kotlinx.serialization.Serializable

const val APP_SIMPLE_NAME = "App"
const val APP_FQN = "$KOBWEB_CORE_FQN_PREFIX$APP_SIMPLE_NAME"

@Serializable
class AppEntry(val fqn: String)

@Serializable
class AppData(
    val appEntry: AppEntry?,
    val frontendData: FrontendData,
)