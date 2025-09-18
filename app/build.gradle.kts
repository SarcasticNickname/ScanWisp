plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)

    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val crashlyticsMappingUpload: Boolean =
    (project.findProperty("crashlyticsMappingUpload") as String?)?.toBoolean() ?: false


android {
    namespace = "com.myprojects.scanwisp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myprojects.scanwisp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "com.myprojects.scanwisp.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {

    }

    buildTypes {
        // ====================================================================
        // ИЗМЕНЕНО: Явно определяем `release` блок со всеми настройками.
        // ====================================================================
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true // ДОБАВЛЕНО: Включаем сжатие ресурсов
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // signingConfig = signingConfigs.getByName("release") // Раскомментируете, когда настроите signingConfigs
        }

        getByName("debug") {
            versionNameSuffix = "-debug"
        }

        create("releaseLocal") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            versionNameSuffix = "-local"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"

            // Говорим Gradle брать первый встреченный файл, если пути дублируются.
            pickFirsts += "META-INF/LICENSE.md"
            pickFirsts += "META-INF/LICENSE.txt"
            pickFirsts += "META-INF/LICENSE-notice.md"
            pickFirsts += "META-INF/NOTICE.md"
            pickFirsts += "META-INF/NOTICE.txt"
            pickFirsts += "META-INF/ASL-2.0.txt"
            pickFirsts += "META-INF/LGPL-3.0.txt"
            pickFirsts += "META-INF/licenses/ASM"
            pickFirsts += "META-INF/kotlin-tooling-metadata.json"
        }
    }

    firebaseCrashlytics {
        mappingFileUploadEnabled = crashlyticsMappingUpload
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {

    // --- AndroidX & Jetpack Core ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- Compose ---
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)

    // --- Hilt (Dependency Injection) ---
    implementation(libs.hilt.android)
    implementation(libs.androidx.material3.window.size.class1.android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.metrics.performance)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.espresso.intents)
    testImplementation(libs.junit.jupiter)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // --- Room (Database) ---
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    // --- Data & Storage ---
    implementation(libs.androidx.datastore.preferences)

    // --- UI Utilities ---
    implementation(libs.coil.compose)
    implementation(libs.shimmer)
    implementation("sh.calvin.reorderable:reorderable:3.0.0")


    // --- PDF & Document Processing ---
    implementation(libs.pdfbox.android)
    implementation(libs.google.mlkit.scanner)

    // --- Google Services ---
    implementation(libs.google.ads)
    implementation(libs.google.ump)

    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.1.1"))

    // 1. Firebase Analytics
    implementation("com.google.firebase:firebase-analytics")

    // 2. Firebase Crashlytics
    implementation("com.google.firebase:firebase-crashlytics")

    // 3. Firebase Remote Config
    implementation("com.google.firebase:firebase-config")

    // 4. In-App Review (Play Core KTX)
    implementation("com.google.android.play:review-ktx:2.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    /**
     * ==========================================================
    Hilt + WorkManager
     * ==========================================================
     */
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    /**
     * ==========================================================
    Основная библиотека WorkManager
     * ==========================================================
     */
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("androidx.tracing:tracing-ktx:1.3.0-alpha02")

    // --- Unit тесты (src/test) ---
    testImplementation(libs.junit) // Основной фреймворк для тестов
    testImplementation("io.mockk:mockk:1.13.10") // Библиотека для создания моков (заглушек)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0") // Для тестирования корутин
    testImplementation("app.cash.turbine:turbine:1.1.0") // Для удобного тестирования Flow

    // --- Интеграционные и UI тесты (src/androidTest) ---
    androidTestImplementation(libs.androidx.junit) // Расширения JUnit для Android
    androidTestImplementation(libs.androidx.espresso.core) // Espresso для UI-тестов
    androidTestImplementation(platform(libs.androidx.compose.bom)) // BOM для Compose
    androidTestImplementation(libs.androidx.compose.ui.test.junit4) // Основной инструмент для UI-тестов в Compose
    androidTestImplementation("io.mockk:mockk-android:1.13.10") // Версия MockK для Android-тестов
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    androidTestImplementation("app.cash.turbine:turbine:1.1.0")
    androidTestImplementation("androidx.work:work-testing:2.9.0")

    // Hilt для тестов
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51.1")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.51.1")

    // Room для тестов
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    // Для навигации в UI-тестах
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.7")

    // --- Отладочные зависимости для Compose ---
    debugImplementation(libs.bundles.compose.debug)

    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.timber)

    baselineProfile(project(":baselineprofile"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}