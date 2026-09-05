plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nuvio.ckplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nuvio.ckplayer"
        minSdk = 26
        targetSdk = 34
        versionCode = 66
        versionName = "1.58.0"
        // arm only: every phone and TV box Nebula runs on is arm64 or armv7, and libtorrent's native
        // library is the only thing here with a processor. On anything else the engine reports itself
        // unavailable and P2P streams stay hidden (P2p.available).
        ndk.abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }

    // Read signing config from Gradle -P properties (passed explicitly on the CI
    // command line) — reliably seen by findProperty regardless of daemon/env.
    val keystorePath = (project.findProperty("nebulaKeystore") as String?)?.takeIf { it.isNotBlank() }
    logger.lifecycle("Nebula release signing: keystore ${if (keystorePath != null) "PRESENT -> fixed release key" else "ABSENT -> debug fallback"}")

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = project.findProperty("nebulaStorePassword") as String?
                keyAlias = project.findProperty("nebulaKeyAlias") as String?
                keyPassword = project.findProperty("nebulaKeyPassword") as String?
            }
        }
    }

    buildTypes {
        debug {
            // debug-signed by default so local/dev builds need no keystore
        }
        release {
            isMinifyEnabled = false
            // One fixed release key in CI (from repo secrets) so every build shares
            // a signature and updates install over the top — no uninstall. Falls
            // back to debug signing for local builds without the keystore.
            signingConfig = if (keystorePath != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // libtorrent's native library is ~16 MB per processor uncompressed. This APK is sideloaded
        // over a TV's downloader and a short link, so download size beats the small load-time win
        // of storing it flat: 11 MB compressed instead of 30 MB for the two processors we ship.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")

    val media3 = "1.11.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // P2P streams (P2p.kt). The per-processor artifacts are plain jars carrying lib/<abi>/libtorrent4j.so,
    // which the packager picks up as native libraries; they all pull in the shared Java classes.
    val libtorrent = "2.1.0-35"
    implementation("org.libtorrent4j:libtorrent4j:$libtorrent")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:$libtorrent")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:$libtorrent")
}
