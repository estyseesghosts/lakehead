plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "me.foxtails.lakehead"
    compileSdk = 36

    defaultConfig {
        applicationId = "me.foxtails.lakehead"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Debug keystore re-used for release in this slice. The Android Gradle
    // Plugin auto-creates ~/.android/debug.keystore on first run, so the
    // build does not depend on a pre-existing keystore on a fresh machine.
    //
    // To wire a real Play upload key, add a non-committed
    // `keystore.properties` (see `keystore.properties.example`) and read
    // it through `Properties().apply { load(...) }` here. AGENTS.md §7
    // forbids committing real secrets; this slice ships the template
    // and an example consumer, no real values.
    signingConfigs {
        // No extra `release` signing config in this slice; we reuse
        // `signingConfigs.debug` (AGP-managed) directly from the
        // `release` build type below.
    }

    buildTypes {
        getByName("debug") {
            // Default; explicitly recorded for symmetry with `release`.
            isMinifyEnabled = false
            isShrinkResources = false
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isCrunchPngs = true
            // Use the debug keystore so `assembleRelease` produces a
            // runnable APK without committing a release key. Swap to a
            // dedicated `signingConfigs.release { ... }` once a real
            // Play keystore is available.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":feature:home"))
    implementation(project(":feature:allapps"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":platform:launcherapps"))
    implementation(project(":platform:shortcuts"))
    implementation(project(":platform:feed"))
    implementation(project(":platform:widgets"))
    implementation(project(":platform:notifications"))
    implementation(project(":data:repository"))
    implementation(project(":data:database"))
    implementation(project(":core:model"))
    implementation(project(":core:grid"))
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.json:json:20231013")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}