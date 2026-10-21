import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import zeno.buildlogic.ZenoBuild
import zeno.buildlogic.configureAndroidCommon
import zeno.buildlogic.lib
import zeno.buildlogic.libs

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = ZenoBuild.TARGET_SDK
                testOptions.animationsDisabled = true
            }

            dependencies {
                "testImplementation"(libs.lib("junit"))
                "testImplementation"(libs.lib("kotlin-test"))
            }
        }
    }
}
