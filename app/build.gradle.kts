plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.pinnr.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pinnr.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.7.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
        buildConfigField("String", "UPDATE_REPO", "\"FaradayMicrosystems/pinnr\"")
    }

    // Fixed key from GitHub secrets so each build can install over the last one
    // (Android refuses updates signed with a different key; uninstalling deletes pins).
    val ksFile = System.getenv("PINNR_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (ksFile != null) create("pinnr") {
            storeFile = ksFile
            storePassword = System.getenv("PINNR_KEY_PASSWORD")
            keyAlias = "pinnr"
            keyPassword = System.getenv("PINNR_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            if (ksFile != null) signingConfig = signingConfigs.getByName("pinnr")
        }
        release {
            isMinifyEnabled = false
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
}

dependencies {
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("androidx.car.app:app:1.4.0")
    implementation("androidx.car.app:app-projected:1.4.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
