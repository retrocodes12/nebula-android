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
        versionCode = 93
        versionName = "1.82.0"
        // arm only: every phone and TV box Nebula runs on is arm64 or armv7, and libtorrent's native
        // library is the only thing here with a processor. On anything else the engine reports itself
        // unavailable and P2P streams stay hidden (P2p.available).
        ndk.abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        // the launch gate's and the Screens workflow's emulators are x86: only there, and only when asked, do the x86 libraries go in
        if (project.hasProperty("emulatorAbis")) ndk.abiFilters += listOf("x86_64", "x86")
        // on-device tests (device-tests.yml, on an emulator): the subtitle sync's ear against real decoding
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing config: a Gradle -P property, else the environment. CI passes the passwords through the environment (since
    // 2026-09-24): as -P arguments they sat on the command line, readable in /proc by anything else running in the job.
    fun signing(prop: String, env: String): String? =
        (project.findProperty(prop) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }
    val keystorePath = signing("nebulaKeystore", "NEBULA_KEYSTORE_FILE")
    logger.lifecycle("Nebula release signing: keystore ${if (keystorePath != null) "PRESENT -> fixed release key" else "ABSENT -> debug fallback"}")

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = signing("nebulaStorePassword", "NEBULA_STORE_PASSWORD")
                keyAlias = signing("nebulaKeyAlias", "NEBULA_KEY_ALIAS")
                keyPassword = signing("nebulaKeyPassword", "NEBULA_KEY_PASSWORD")
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
    testOptions {
        // app/src/test holds JVM tests of pure functions only. A framework call reached on the way (Media3's language
        // table asks TextUtils) returns its default here instead of throwing "not mocked".
        unitTests.isReturnDefaultValues = true
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
    // Software video + audio decoders for Media3 (FFmpeg, built by NextPlayer's nextlib; the version pairs with Media3's).
    // The phone's chip decodes first; a picture it cannot do (10-bit H.264, an odd profile) falls to the processor — 1.73.0.
    implementation("io.github.anilbeesetti:nextlib-media3ext:1.11.0-0.15.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // P2P streams (P2p.kt). The per-processor artifacts are plain jars carrying lib/<abi>/libtorrent4j.so,
    // which the packager picks up as native libraries; they all pull in the shared Java classes.
    val libtorrent = "2.1.0-35"
    implementation("org.libtorrent4j:libtorrent4j:$libtorrent")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:$libtorrent")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:$libtorrent")

    // JVM unit tests (ci.yml runs them on every branch push). android.jar's org.json is a stub that throws off a
    // device, so the real one goes on the test classpath ahead of it.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// Every test's outcome in the CI log, and one count line at the end: a green run that ran nothing must be visible.
tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    addTestListener(object : org.gradle.api.tasks.testing.TestListener {
        override fun beforeSuite(suite: org.gradle.api.tasks.testing.TestDescriptor) {}
        override fun beforeTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor) {}
        override fun afterTest(testDescriptor: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {}
        override fun afterSuite(suite: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult) {
            if (suite.parent == null) logger.lifecycle(
                "Unit tests: ${result.testCount} run, ${result.successfulTestCount} passed, " +
                    "${result.failedTestCount} failed, ${result.skippedTestCount} skipped",
            )
        }
    })
}
