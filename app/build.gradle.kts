plugins {
    // Kotlin compilation is built into AGP 9+ (no kotlin-android plugin needed).
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.openstream.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.openstream.tv"
        // minSdk 23: Compose >= 1.8 requires API 23. The owner's lowest-end
        // devices (onn boxes) run API 29+, so 23 is a comfortable floor.
        // Decision recorded in docs/DECISIONS.md.
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            // No shrinking yet (YAGNI until release phase); debug-first project.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
    // Kotlin jvmTarget defaults to compileOptions.targetCompatibility (17),
    // so no explicit kotlin { compilerOptions } block is needed.
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.tv.material)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
