plugins {
    alias(libs.plugins.zeno.android.library)
}

android {
    namespace = "com.blackcocadastudios.zeno.engine.gecko"
}

dependencies {
    api(projects.core.engine)
    implementation(libs.geckoview)
    // GeckoView's experimental APIs are opted into with AndroidX's @OptIn.
    implementation(libs.androidx.annotation.experimental)
    implementation(libs.kotlinx.coroutines.android)
}
