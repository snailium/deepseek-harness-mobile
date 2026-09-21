import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * The version, read from the fork's own `version.properties` at the repository root.
 *
 * It lives there rather than here on purpose. This block used to sit in this file, and upstream
 * rewrote it for its own 0.11.6 and 0.11.7 releases — a merge then took their side silently, with
 * no conflict and no error, leaving an APK that built as plain `0.11.7` while claiming to be
 * upstream's build. A file upstream does not have cannot be merged away.
 *
 * See `version.properties` for the scheme (`<fork>-dsh.<harness>`) and why the harness half is a
 * compatibility claim rather than a counter. `scripts/release.sh` passes `DSH_VERSION_NAME` and
 * checks the harness half against upstream's own compatibility table before building.
 */
val forkVersionFile = rootProject.file("version.properties")
val forkVersionProps = Properties().apply {
    require(forkVersionFile.exists()) { "missing ${forkVersionFile.path} — see its header" }
    forkVersionFile.inputStream().use { load(it) }
}

/** The fork's own release count, e.g. `0.2.0`. */
val forkVersion: String = forkVersionProps.getProperty("forkVersion").trim()

/** The harness line this build is for, e.g. `0.1.6`. A claim, not a counter. */
val harnessVersion: String = forkVersionProps.getProperty("harnessVersion").trim()

/**
 * `DSH_VERSION_NAME` overrides the whole name, which is what the release workflow uses; otherwise
 * the name is composed from the two halves.
 */
val dshVersionName: String = System.getenv("DSH_VERSION_NAME")?.takeIf { it.isNotBlank() }
    ?: "$forkVersion-dsh.$harnessVersion"

/**
 * `versionCode` derives from the fork half **of the effective name**, not from `forkVersion`.
 *
 * It has to follow the override, or a release built with `DSH_VERSION_NAME=0.3.0-…` would ship with
 * the *previous* release's code and Android would refuse to upgrade over it.
 *
 * The fork half is taken alone on purpose: upstream packs its semver into the same integer, so
 * deriving ours from the full name would let a harness bump collide with a fork bump — two builds
 * claiming one code. The fork half is monotonic by itself, which is all Android asks.
 */
val dshVersionCode: Int = dshVersionName
    .substringBefore("-dsh.").substringBefore('-')
    .split('.')
    .mapNotNull { it.toIntOrNull() }
    .let { parts ->
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        major * 10_000 + minor * 100 + patch
    }
    .coerceAtLeast(1)

android {
    namespace = "com.labteto.dshmobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.labteto.dshmobile"
        minSdk = 26
        targetSdk = 35
        versionCode = dshVersionCode
        versionName = dshVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Optional release signing: provide DSH_KEYSTORE / DSH_KEYSTORE_PASSWORD /
    // DSH_KEY_ALIAS / DSH_KEY_PASSWORD (env vars, e.g. from GitHub secrets).
    // Signing activates only when the keystore file actually exists, so a
    // missing keystore silently falls back to an unsigned release APK.
    signingConfigs {
        val keystore = System.getenv("DSH_KEYSTORE")
        if (!keystore.isNullOrBlank() && file(keystore).exists()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("DSH_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DSH_KEY_ALIAS")
                keyPassword = System.getenv("DSH_KEY_PASSWORD")
            }
            buildTypes.getByName("release") {
                signingConfig = signingConfigs.getByName("release")
            }
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // The 11-language claim is only true while every base string has a translation, and the
        // gap is invisible in review — this is the check that actually enforces it, so it is
        // pinned rather than left to the default severity.
        error += listOf("MissingTranslation", "ImpliedQuantity")
        // `HardcodedText` is deliberately absent: it only inspects XML layouts, and this app has
        // none. Compose string literals have to be caught in review.
    }
}

dependencies {
    implementation(project(":core"))

    // The architecture seam as a build-time check — see lint/build.gradle.kts. `lintChecks` puts
    // this module's IssueRegistry on lint's own classpath, so `:app:lintDebug` fails on a
    // presentation-layer dependency rather than a reviewer having to notice it.
    lintChecks(project(":lint"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.appcompat)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    // QR scanning for relay pairing. ZXing rather than ML Kit: it needs no Google Play Services, so
    // it works on a de-Googled device, and pairing is the one flow a user cannot route around.
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The mock harness carries this project's port of the host's answer-acceptance law. The
    // conformance test runs the real encoder through it rather than through a copy, because a copy
    // is a second thing to keep in step and the failure it guards against is a silent one.
    testImplementation(project(":mock-harness"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
