plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.castor.feature.media"
    compileSdk = 35

    defaultConfig {
        minSdk = 29

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        // Register the Spotify redirect URI scheme so the OS routes
        // the OAuth callback back to the app.
        manifestPlaceholders["spotifyRedirectScheme"] = "com.castor.app"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Project modules
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:security"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    // Material Icons (extended set -- skip, play, pause, etc.)
    implementation(libs.compose.material.icons)

    // Lifecycle
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // OAuth (AppAuth)
    implementation(libs.appauth)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Image loading
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}
