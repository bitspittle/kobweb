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

                api(project(":frontend:kobweb-compose"))
                api(project(":frontend:silk-foundation"))
                implementation(project(":frontend:compose-html-ext"))
            }
        }
    }
}

kobwebPublication {
    artifactId.set("silk-widgets")
    description.set("The subset of Silk that doesn't depend on Kobweb at all, extracted into its own library in case projects want to use it without Kobweb")
    filter.set(FILTER_OUT_MULTIPLATFORM_PUBLICATIONS)
}
