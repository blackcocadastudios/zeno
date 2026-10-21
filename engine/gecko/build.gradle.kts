plugins {
    alias(libs.plugins.zeno.android.library)
}

android {
    namespace = "com.blackcocadastudios.zeno.engine.gecko"
}

dependencies {
    api(projects.core.engine)
    implementation(libs.geckoview)
    implementation(libs.kotlinx.coroutines.android)
}
