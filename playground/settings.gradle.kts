pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

rootProject.name = "playground"

includeBuild("../")

include(":site")
include(":sitelib")
include(":temp")
include(":worker")
