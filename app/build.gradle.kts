plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")

android {
    namespace = "com.angmarx.deadmandraw"
    compileSdk = 35

    signingConfigs {
        create("release") {
            if (!releaseStorePath.isNullOrBlank()) {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.angmarx.deadmandraw"
        minSdk = 23
        targetSdk = 35
        versionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("ANDROID_VERSION_NAME") ?: "1.0"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    buildTypes {
        getByName("release") {
            if (!releaseStorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
