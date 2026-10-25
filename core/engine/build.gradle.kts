plugins {
    alias(libs.plugins.zeno.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
