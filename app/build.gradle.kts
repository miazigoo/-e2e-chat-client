plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

fun projectPropertyOrEnv(name: String, fallback: String): String =
    providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: fallback

fun String.asBuildConfigString(): String = "\"$this\""

val defaultDebugApiBaseUrl = "http://10.0.2.2:8000/api/v1/"
val defaultReleaseApiBaseUrl = "https://api.example.com/api/v1/"

val debugApiBaseUrl = projectPropertyOrEnv("SECURE_CHAT_DEBUG_API_BASE_URL", defaultDebugApiBaseUrl)
val releaseApiBaseUrl = projectPropertyOrEnv("SECURE_CHAT_RELEASE_API_BASE_URL", defaultReleaseApiBaseUrl)
val debugHttpLoggingEnabled = projectPropertyOrEnv("SECURE_CHAT_DEBUG_HTTP_LOGGING", "true").toBoolean()
val releaseHttpLoggingEnabled = projectPropertyOrEnv("SECURE_CHAT_RELEASE_HTTP_LOGGING", "false").toBoolean()
val debugSignalProtocolEnabled = projectPropertyOrEnv("SECURE_CHAT_DEBUG_SIGNAL_PROTOCOL", "false").toBoolean()
val releaseSignalProtocolEnabled = projectPropertyOrEnv("SECURE_CHAT_RELEASE_SIGNAL_PROTOCOL", "false").toBoolean()
val debugAuthHintsVisible = projectPropertyOrEnv("SECURE_CHAT_SHOW_DEBUG_AUTH_INFO", "true").toBoolean()

android {
    namespace = "com.example.securechatapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.securechatapp"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", debugApiBaseUrl.asBuildConfigString())
        buildConfigField("boolean", "ENABLE_HTTP_LOGGING", debugHttpLoggingEnabled.toString())
        buildConfigField("boolean", "ENABLE_SIGNAL_PROTOCOL", "false")
        buildConfigField("boolean", "SHOW_DEBUG_AUTH_INFO", "false")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", debugApiBaseUrl.asBuildConfigString())
            buildConfigField("boolean", "ENABLE_HTTP_LOGGING", debugHttpLoggingEnabled.toString())
            buildConfigField("boolean", "ENABLE_SIGNAL_PROTOCOL", debugSignalProtocolEnabled.toString())
            buildConfigField("boolean", "SHOW_DEBUG_AUTH_INFO", debugAuthHintsVisible.toString())
            isMinifyEnabled = false
        }
        release {
            buildConfigField("String", "API_BASE_URL", releaseApiBaseUrl.asBuildConfigString())
            buildConfigField("boolean", "ENABLE_HTTP_LOGGING", releaseHttpLoggingEnabled.toString())
            buildConfigField("boolean", "ENABLE_SIGNAL_PROTOCOL", releaseSignalProtocolEnabled.toString())
            buildConfigField("boolean", "SHOW_DEBUG_AUTH_INFO", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.google.material)
    implementation(libs.coil.compose)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.datastore.core)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("org.whispersystems:signal-protocol-android:2.8.1")
}

tasks.configureEach {
    if (name.contains("Release", ignoreCase = false)) {
        doFirst {
            check(releaseApiBaseUrl != defaultReleaseApiBaseUrl) {
                "SECURE_CHAT_RELEASE_API_BASE_URL must be configured for release builds."
            }
        }
    }
}
