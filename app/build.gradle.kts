plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Checksum-pinned, reproducible builds: dependency coordinates are locked in
// gradle.lockfile and cannot drift to new versions without a deliberate
// `./gradlew --write-locks` + review. Regenerate after any dependency change.
dependencyLocking {
    lockAllConfigurations()
}

// App identity/version — single source of truth in gradle.properties so a
// release bump touches one file instead of these literals.
val s4ApplicationId = providers.gradleProperty("S4_APPLICATION_ID")
val s4VersionCode = providers.gradleProperty("S4_VERSION_CODE")
val s4VersionName = providers.gradleProperty("S4_VERSION_NAME")

val hostJniDir = layout.buildDirectory.dir("host-jni")

// Builds a host (macOS) dylib of slip39_jni from the vendored sources so the
// JVM unit tests can exercise the real native SLIP-39 code (plan Phase 4.1).
tasks.register<Exec>("buildHostSlip39") {
    workingDir = rootProject.projectDir
    commandLine("bash", "tools/host-jni/build-host.sh", hostJniDir.get().asFile.absolutePath)
    inputs.dir("src/main/cpp")
    outputs.dir(hostJniDir)
}

tasks.withType<Test>().configureEach {
    dependsOn("buildHostSlip39")
}

android {
    namespace = "com.s4"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = s4ApplicationId.get()
        minSdk = 26
        targetSdk = 37
        versionCode = s4VersionCode.get().toInt()
        versionName = s4VersionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // minSdk 26 admits 32-bit ARM devices, so armeabi-v7a must ship or
            // they crash with UnsatisfiedLinkError on the first crypto call.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            // Credentials live in ~/.gradle/gradle.properties (never committed).
            // When they are absent (e.g. CI) the config stays empty and the
            // release build is simply unsigned.
            val storeFileProp = providers.gradleProperty("S4_RELEASE_STORE_FILE")
            if (storeFileProp.isPresent) {
                storeFile = file(storeFileProp.get())
                storePassword = providers.gradleProperty("S4_RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("S4_RELEASE_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("S4_RELEASE_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            // Deliberately unminified (R8 off). The app's crypto is vendored C
            // invoked through thin JNI facades by method name; R8 shrinking and
            // unused-code removal can silently break those native bindings. With
            // no ad/analytics SDKs and a ~10 MB APK, shrinking buys nothing.
            // If R8 is ever enabled, it must be verified end-to-end against the
            // instrumented SLIP-39 tests on every shipped ABI.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    testOptions {
        unitTests.all {
            it.systemProperty("slip39.native.library", hostJniDir.get().asFile.resolve("libslip39_jni.dylib").absolutePath)
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
