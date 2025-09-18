@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.myprojects.scanwisp.baselineprofile"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Указываем Gradle, что этот модуль предназначен для профилирования модуля :app
    targetProjectPath = ":app"
}

dependencies {
    // Используем псевдонимы из libs.versions.toml для консистентности
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.compose.ui.test.junit4)
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}