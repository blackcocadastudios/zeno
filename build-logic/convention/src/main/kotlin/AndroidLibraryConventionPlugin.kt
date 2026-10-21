import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import zeno.buildlogic.ZenoBuild
import zeno.buildlogic.configureAndroidCommon
import zeno.buildlogic.lib
import zeno.buildlogic.libs

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk {
                    version = release(ZenoBuild.COMPILE_SDK) {
                        minorApiLevel = ZenoBuild.COMPILE_SDK_MINOR
                    }
                }
                configureAndroidCommon(this)
                testOptions.targetSdk = ZenoBuild.TARGET_SDK
                lint.targetSdk = ZenoBuild.TARGET_SDK
            }

            dependencies {
                "testImplementation"(libs.lib("junit"))
                "testImplementation"(libs.lib("kotlin-test"))
            }
        }
    }
}
