plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jr12866.dartcoach"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jr12866.dartcoach"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // The sherpa-onnx native library is built by
        // tools/build-sherpa.ps1 against this NDK. Telling AGP which one it
        // is stops it trying to resolve a different version.
        ndkVersion = "27.2.12479018"

        // The models are large. Whisper base.en int8 alone is around 250 MB,
        // so the APK cannot fit under the 200 MB limit on Google Play. This is
        // a sideload-only app; see docs/DECISIONS.md.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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

    // For local development, the models are also copied into the app's own
    // assets. A debug APK installed with `adb install` never gets the asset
    // pack, because that only arrives through the Play Store.
    //
    // The release build does not do this: it ships the pack and nothing
    // else, which is the whole point of the pack.
    if (project.hasProperty("withLocalModels")) {
        sourceSets.getByName("main") {
            assets.srcDir(rootProject.file("models-pack/src/main/assets"))
        }
    }

    androidResources {
        // Model files must be readable as-is.
        noCompress += listOf("onnx", "txt")
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

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
