import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import zeno.buildlogic.ZenoBuild
import zeno.buildlogic.configureKotlin
import zeno.buildlogic.configureTests
import zeno.buildlogic.lib
import zeno.buildlogic.libs

/** Pure Kotlin module: no Android framework, fast JVM tests. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = ZenoBuild.JAVA_VERSION
                targetCompatibility = ZenoBuild.JAVA_VERSION
            }
            configureKotlin<KotlinJvmProjectExtension>()
            configureTests()

            dependencies {
                "testImplementation"(libs.lib("junit"))
                "testImplementation"(libs.lib("kotlin-test"))
            }
        }
    }
}
