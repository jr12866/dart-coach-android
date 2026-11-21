plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.util.Properties

/**
 * Release signing is configured from local.properties, which is gitignored.
 *
 * The properties are deliberately read at configuration time rather than in a
 * shell script, so `bundleRelease` is the same command everywhere and the
 * secrets never enter the build script:
 *
 *   dartcoach.keystore=/absolute/path/to/dart-coach-release.jks
 *   dartcoach.keystore.password=...
 *   dartcoach.key.alias=dart-coach
 *   dartcoach.key.password=...
 *
 * A missing release signing configuration is allowed while the app is still
 * being developed. The release bundle then remains unsigned and Play will
 * reject it, but debug builds and the test suite still work.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val releaseKeystorePath = localProperties.getProperty("dartcoach.keystore")
val releaseKeystorePassword = localProperties.getProperty("dartcoach.keystore.password")
val releaseKeyAlias = localProperties.getProperty("dartcoach.key.alias")
val releaseKeyPassword = localProperties.getProperty("dartcoach.key.password")
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

/**
 * Whether this is a release build.
 *
 * `gradle bundleRelease` and `gradle assembleRelease` both build a release
 * variant. The local-model assets are only for debug builds, because the
 * release build ships the models in the asset pack and a duplicate copy in
 * the base module makes the bundle fail.
 *
 * Only a task that *packages* a release counts. The base module's assets are
 * shared by every variant, so the decision has to be made once for the whole
 * build, and a task that merely has "Release" in its name - such as
 * `testReleaseUnitTest` - is not a release build of the app. Counting it
 * meant that running the test suites and the debug build in one command
 * quietly left the models out of the debug APK, and the app then died on the
 * device with `Load 'silero_vad.onnx' failed` and nothing else.
 */
fun isReleaseBuild(): Boolean =
    gradle.startParameter.taskNames.any {
        it.contains("Release", ignoreCase = true) &&
            (it.contains("assemble", ignoreCase = true) ||
                it.contains("bundle", ignoreCase = true))
    }

android {
    namespace = "com.jr12866.dartcoach"

    /**
     * compileSdk 36, because Play requires it.
     *
     * From 31 August 2026, a new app or an app update has to target Android 16
     * (API level 36) to be accepted by Google Play. The extension to
     * 1 November 2026 has passed, so targetSdk 35 is no longer accepted for a
     * new release.
     *
     * See docs/PUBLISHING.md for the requirement and its source.
     */
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jr12866.dartcoach"
        minSdk = 26
        targetSdk = 36

        /**
         * The version code goes up for every bundle that reaches Play.
         *
         * Play will not accept a second bundle with a version code it has
         * already seen, even for a track that has not been rolled out, so a
         * change to the manifest - the screen orientation fix, for instance -
         * needs a new code. 3 is the bundle that declares the screen
         * orientation as optional, which is what Play asked for.
         */
        versionCode = 3
        versionName = "0.2.1"
        buildFeatures {
            buildConfig = true
        }

        // The sherpa-onnx native library is built by
        // tools/build-sherpa.ps1 against this NDK. Telling AGP which one it
        // is stops it trying to resolve a different version.
        ndkVersion = "27.2.12479018"

        // The models are large. Whisper base.en int8 alone is around 250 MB,
        // so they cannot go in the base module. They ship in an install-time
        // Play Asset Delivery pack instead; see docs/PUBLISHING.md.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        // The models are already compressed and are large; compressing them
        // again buys nothing and slows the first read.
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // The models ship as a Play Asset Delivery pack, not in the app's own
    // assets, so that the APK stays small enough for the Play Store.
    assetPacks += listOf(":models-pack")

    // Play Store distribution is through an app bundle. This also makes
    // `bundleRelease` the single release build command.
    bundle {
        abi { enableSplit = true }
        language { enableSplit = false }
    }

    // For local development, the models are also copied into the app's own
    // assets. A debug APK installed with `adb install` never gets the asset
    // pack, because that only arrives through the Play Store.
    //
    // The release build does not do this: it ships the pack and nothing
    // else, which is the whole point of the pack. Copying the models into
    // the base module as well would put the same asset in both modules and
    // the bundle would fail to build.
    //
    // The models are copied into a directory of their own rather than the
    // root of the assets, and the app reads them from there when they are
    // there. The reason is the asset pack: its own asset directory is
    // added to the app's asset sources as well, and sharing one source
    // directory between the two makes the base module and the pack claim
    // the same asset - which the bundle build rejects.
    //
    // The `local-models` directory is checked for at runtime, so the same
    // source works for the debug APK and the release bundle.
    if (project.hasProperty("withLocalModels") && !isReleaseBuild()) {
        sourceSets.getByName("main") {
            assets.srcDir(rootProject.file("models-pack/src/main/assets"))
        }
    }

    lint {
        // `InvalidFragmentVersionForActivityResult` is a false positive here.
        //
        // The check fires when `registerForActivityResult()` is used and the
        // resolved androidx.fragment is older than 1.3.0. The reason it
        // exists is that older FragmentActivity versions mishandled
        // `onRequestPermissionsResult`. Since androidx.activity 1.9.0,
        // ComponentActivity no longer extends FragmentActivity at all: the
        // Activity Result API is handled by ActivityResultRegistry and
        // SavedStateRegistry, with no Fragment involved.
        //
        // This app depends on androidx.activity 1.9.3 and has no
        // androidx.fragment on the classpath, so lint finds no fragment
        // version and assumes the old, broken one. There is no Fragment here
        // to upgrade.
        //
        // The permission request was proved on the Pixel 7: the dialog
        // appears, the grant arrives through the activity result callback,
        // and the coach starts listening.
        //
        // This only affects `lintVitalRelease`, which runs on the APK path.
        // `bundleRelease` does not depend on it, so the bundle that Play
        // receives was never affected.
        disable += "InvalidFragmentVersionForActivityResult"
    }

    androidResources {
        // Model files must be readable as-is.
        noCompress += listOf("onnx", "txt")
    }

    // Google Play receives an app bundle. A release APK is not published.
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Column, Row and the rest of the layout primitives live here, not in
    // compose-ui. Without it the compiler cannot find Column's inline
    // method and fails with an internal backend error.
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // The navigation between the coach and the Pro screens.
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // The Pro purchase.
    //
    // The KTX module carries the coroutine extensions, so the billing calls
    // read as suspend functions rather than listeners.
    implementation("com.android.billingclient:billing-ktx:9.1.0")

    // The player's own settings and history, kept on the phone.
    //
    // DataStore is used rather than SharedPreferences because the profile is
    // written as a unit - the whole hit-rate map - and DataStore writes are
    // transactional, so a write cannot leave half a profile behind.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // piper-phonemize is the text frontend for the Piper voice: it turns text
    // into the phoneme codes the model was trained on. It bundles its own
    // copy of the espeak-ng data, which is why sherpa-onnx's own espeak-ng
    // directory is not used.
    implementation("com.github.csukuangfj.piper-phonemize:piper-phonemize:v1.5.9")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
