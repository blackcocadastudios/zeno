// Every plugin used by any module is declared here so the build classpath is
// identical for all projects. The Kotlin JVM plugin entry also pins the Kotlin
// Gradle Plugin version that AGP's built-in Kotlin support resolves.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
}
