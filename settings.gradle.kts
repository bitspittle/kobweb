pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "kobweb"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":common:kobweb-common")
include(":common:kobweb-serialization")
include(":common:kobwebx-serialization-kotlinx")
include(":common:client-server-internal")
include(":frontend:kobweb-core")
include(":frontend:kobweb-compose")
include(":frontend:kobweb-silk")
include(":frontend:kobweb-worker")
include(":frontend:kobweb-worker-interface")
include(":frontend:silk-foundation")
include(":frontend:silk-widgets")
include(":frontend:silk-widgets-kobweb")
include(":frontend:silk-icons-fa")
include(":frontend:silk-icons-mdi")
include(":frontend:kobwebx-markdown")
include(":frontend:compose-html-ext")
include(":frontend:browser-ext")
include(":backend:kobweb-api")
include(":backend:server")
include(":backend:server-plugin")
include(":tools:gradle-plugins:core")
include(":tools:gradle-plugins:library")
include(":tools:gradle-plugins:application")
include(":tools:gradle-plugins:worker")
include(":tools:gradle-plugins:extensions:markdown")
include(":tools:ksp:site-processors")
include(":tools:ksp:worker-processor")
include(":tools:ksp:ksp-ext")
include(":tools:processor-common")
