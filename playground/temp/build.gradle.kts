plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

group = "playground.temp"
version = "1.0-SNAPSHOT"


kotlin {
    jvm()
    js {
        browser()
    }
}
