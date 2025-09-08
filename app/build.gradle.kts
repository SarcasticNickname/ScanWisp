plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)

    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

val crashlyticsMappingUpload: Boolean =
    (project.findProperty("crashlyticsMappingUpload") as String?)?.toBoolean() ?: false

// Файл: app/build.gradle

android {
    namespace = "com.myprojects.scanwisp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.myprojects.scanwisp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // debug создаётся автоматически
        // ====================================================================
        // ДОБАВЛЕНО: Шаблон для вашей будущей релизной подписи.
        // Когда создадите keystore.jks, раскомментируйте и заполните.
        // ====================================================================
        /*
        create("release") {
            storeFile = file("path/to/your/keystore.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
        */
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
            initWith(getByName("release")) // Теперь наследуется от нашего явно определенного `release`
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            versionNameSuffix = "-local"
        }
    }

    compileOptions {
        // ИЗМЕНЕНО: Обновляем версию Java до 17
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
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    firebaseCrashlytics {
        mappingFileUploadEnabled = crashlyticsMappingUpload
    }
}

dependencies {
    // ... Все ваши зависимости остаются без изменений

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
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // --- Room (Database) ---
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)

    // --- Data & Storage ---
    implementation(libs.androidx.datastore.preferences)

    // --- UI Utilities ---
    implementation(libs.coil.compose)
    implementation(libs.reorderable)
    implementation(libs.shimmer)

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

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.bundles.compose.debug)
}