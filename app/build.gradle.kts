plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zoomai.studio"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.zoomai.studio"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"
        // Test builds target modern 64-bit phones only — keeps the APK much smaller
        // (ML Kit ships a ~20 MB native library per CPU architecture).
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.camera:camera-core:1.6.2")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.camera:camera-video:1.6.2")
    // ML Kit Pose Detection — on-device, runs fully offline (bundled model).
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
}
