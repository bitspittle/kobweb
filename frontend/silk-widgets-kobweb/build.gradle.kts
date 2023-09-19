import com.varabyte.kobweb.gradle.publish.FILTER_OUT_MULTIPLATFORM_PUBLICATIONS
import com.varabyte.kobweb.gradle.publish.set

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    id("com.varabyte.kobweb.internal.publish")
}

group = "com.varabyte.kobweb"
version = libs.versions.kobweb.libs.get()

kotlin {
    js {
        browser()
        binaries.executable()
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.html.core)

                implementation(project(":frontend:kobweb-core"))
                api(project(":frontend:silk-widgets"))
            }
        }
    }
}

kobwebPublication {
    artifactId.set("silk-kobweb-widgets")
    description.set("Silk UI components tightly integrated with Kobweb functionality -- they cannot be used without Kobweb")
    filter.set(FILTER_OUT_MULTIPLATFORM_PUBLICATIONS)
}
