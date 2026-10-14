import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.local.gemmalokal"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.local.gemmalokal"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { jniLibs { useLegacyPackaging = true } }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")
}
