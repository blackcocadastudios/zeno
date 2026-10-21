package zeno.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Values shared by every module. Changing one of these is an architectural decision (see docs/adr). */
object ZenoBuild {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36
    const val MIN_SDK = 26
    val JAVA_VERSION = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

internal fun Project.configureAndroidCommon(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = ZenoBuild.COMPILE_SDK

        defaultConfig.apply {
            minSdk = ZenoBuild.MIN_SDK
        }

        compileOptions.apply {
            sourceCompatibility = ZenoBuild.JAVA_VERSION
            targetCompatibility = ZenoBuild.JAVA_VERSION
        }

        testOptions.apply {
            unitTests.isIncludeAndroidResources = true
            unitTests.isReturnDefaultValues = true
        }

        lint.apply {
            abortOnError = true
            checkDependencies = false
            warningsAsErrors = false
            xmlReport = true
            htmlReport = true
        }
    }

    configureKotlin<KotlinAndroidProjectExtension>()
    configureTests()
}

internal fun Project.configureTests() {
    tasks.withType<Test>().configureEach {
        maxHeapSize = "2g"
        // Robolectric renders Compose screenshots with the real graphics stack.
        systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        testLogging {
            events("failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

internal inline fun <reified T : KotlinBaseExtension> Project.configureKotlin() = configure<T> {
    val options = when (this) {
        is KotlinAndroidProjectExtension -> compilerOptions
        is KotlinJvmProjectExtension -> compilerOptions
        else -> error("Unsupported Kotlin extension: $this")
    }
    options.apply {
        jvmTarget.set(ZenoBuild.JVM_TARGET)
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
