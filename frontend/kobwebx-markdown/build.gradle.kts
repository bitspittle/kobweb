import com.varabyte.kobweb.gradle.publish.FILTER_OUT_MULTIPLATFORM_PUBLICATIONS
import com.varabyte.kobweb.gradle.publish.set

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    id("com.varabyte.kobweb.internal.publish")
}

group = "com.varabyte.kobwebx"
version = libs.versions.kobweb.libs.get()

kotlin {
    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        jsMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.html.core)

            implementation(projects.frontend.kobwebCore)
        }
    }
}

kobwebPublication {
    artifactId.set("kobwebx-markdown")
    description.set("Classes useful for projects using the Kobweb markdown plugin")
    filter.set(FILTER_OUT_MULTIPLATFORM_PUBLICATIONS)
}
