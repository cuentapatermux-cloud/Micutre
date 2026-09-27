// Copyright 2026 PollNull

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStoreFile = providers.gradleProperty("MICUTRE_RELEASE_STORE_FILE")
val releaseStorePassword = providers.gradleProperty("MICUTRE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.gradleProperty("MICUTRE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.gradleProperty("MICUTRE_RELEASE_KEY_PASSWORD")

android {
    namespace = "com.miclite.voz"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.miclite.voz"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    signingConfigs {
        create("release") {
            // Gradle properties should use forward slashes for Windows paths.
            storeFile = file(releaseStoreFile.get().replace('\\', '/'))
            storePassword = releaseStorePassword.get()
            keyAlias = releaseKeyAlias.get()
            keyPassword = releaseKeyPassword.get()
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
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
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.google.oboe:oboe:1.10.0")
}
