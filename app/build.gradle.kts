plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // Исключаем манифест OSGI из PDFBox, который не нужен на Android
            // и может вызывать конфликты при сборке.
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
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

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.bundles.compose.debug)
}