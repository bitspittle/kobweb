package com.varabyte.kobweb.gradle.application.ksp

import com.varabyte.kobweb.gradle.core.kmp.JsTarget
import com.varabyte.kobweb.gradle.core.kmp.JvmTarget
import com.varabyte.kobweb.ksp.KOBWEB_METADATA_BACKEND
import com.varabyte.kobweb.ksp.KOBWEB_METADATA_FRONTEND
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

/**
 * A reference to the frontend metadata file generated by KSP.
 *
 * The file is associated as an output of the KSP task that creates it, allowing gradle to properly generate task
 * dependencies when it is used as a task input.
 */
fun Project.kspFrontendFile(jsTarget: JsTarget): Provider<RegularFile> {
    return tasks.named(jsTarget.kspKotlin).map { kspTask ->
        RegularFile {
            kspTask.outputs.files.asFileTree.matching { include(KOBWEB_METADATA_FRONTEND) }.singleFile
        }
    }
}

// Ideally this would return a Provider<RegularFile?> instead, but nullable providers are not supported by Gradle.
// See https://github.com/gradle/gradle/issues/12388
/**
 * A reference to the backend metadata file generated by KSP.
 *
 * Note that a [FileTree] is returned because the file may not exist if there are no backend sources. This will always
 * contain 0 or 1 files.
 *
 * The file is associated as an output of the KSP task that creates it, allowing gradle to properly generate task
 * dependencies when it is used as a task input.
 */
fun Project.kspBackendFile(jvmTarget: JvmTarget): Provider<FileTree> {
    return tasks.named(jvmTarget.kspKotlin).map { kspTask ->
        kspTask.outputs.files.asFileTree.matching { include(KOBWEB_METADATA_BACKEND) }
    }
}
