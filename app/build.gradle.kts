import java.util.Properties

plugins {
    // Kotlin compilation is built into AGP 9+ (no kotlin-android plugin needed).
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Owner-private deployment config (setup.url / setup.brand) lives in the
// gitignored local.properties: the setup domain unlocks name->profile
// lookups, so it must never reach the public repo (CLAUDE.md security).
// Absent keys leave the one-step name setup hidden — open-source builds
// still work, they just start at the classic add-addon path.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
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
        // Bump both for every pre-release: the Phase 5 in-app updater will
        // compare versionCode, and Android refuses to upgrade over an equal one.
        versionCode = 15
        versionName = "0.3.0-alpha.15"

        buildConfigField(
            "String", "SETUP_URL",
            "\"${localProps.getProperty("setup.url").orEmpty()}\"",
        )
        buildConfigField(
            "String", "SETUP_BRAND",
            "\"${localProps.getProperty("setup.brand") ?: "OpenStream TV"}\"",
        )
    }

    buildTypes {
        release {
            // R8 on: the onn boxes are 32-bit with 2-3 GB RAM and the owner
            // reported animation jank — minified builds are the single
            // biggest smoothness lever there (box audit, TESTLOG 2026-07-05).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Debug-signed on purpose: `adb install -r` then upgrades the
            // boxes' existing debug installs in place, keeping the addon DB
            // and watch progress. Proper release signing is a Phase 5 task.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true // android.util.Log no-ops in JVM tests
    }
    // Kotlin jvmTarget defaults to compileOptions.targetCompatibility (17),
    // so no explicit kotlin { compilerOptions } block is needed.
}

ksp {
    // Version-controlled Room schema history — needed for safe migrations later.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

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
    compileOnly(libs.errorprone.annotations)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
