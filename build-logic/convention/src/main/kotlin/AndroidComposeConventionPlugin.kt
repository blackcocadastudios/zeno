import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import zeno.buildlogic.lib
import zeno.buildlogic.libs

/** Enables Jetpack Compose on an Android application or library module. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.withPlugin("com.android.application") {
                configureCompose(extensions.getByType<ApplicationExtension>())
            }
            pluginManager.withPlugin("com.android.library") {
                configureCompose(extensions.getByType<LibraryExtension>())
            }
        }
    }

    private fun Project.configureCompose(extension: CommonExtension) {
        extension.buildFeatures.apply {
            compose = true
        }

        dependencies {
            val bom = platform(libs.lib("androidx-compose-bom"))
            "implementation"(bom)
            "testImplementation"(bom)
            "androidTestImplementation"(bom)

            "implementation"(libs.lib("androidx-compose-ui"))
            "implementation"(libs.lib("androidx-compose-ui-graphics"))
            "implementation"(libs.lib("androidx-compose-foundation"))
            "implementation"(libs.lib("androidx-compose-animation"))
            "implementation"(libs.lib("androidx-compose-material3"))
            "implementation"(libs.lib("androidx-compose-ui-tooling-preview"))
            "debugImplementation"(libs.lib("androidx-compose-ui-tooling"))
            "debugImplementation"(libs.lib("androidx-compose-ui-test-manifest"))

            "testImplementation"(libs.lib("androidx-compose-ui-test-junit4"))
            "testImplementation"(libs.lib("robolectric"))
            "testImplementation"(libs.lib("roborazzi"))
            "testImplementation"(libs.lib("roborazzi-compose"))
            "testImplementation"(libs.lib("roborazzi-junit-rule"))
        }
    }
}
