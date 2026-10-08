import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProperties = Properties()

val localPropertiesFile =
    rootProject.file("local.properties")

if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use {
        localProperties.load(it)
    }
}

android {

    namespace = "com.jarvis.app"

    compileSdk = 35

    defaultConfig {

        applicationId = "com.jarvis.app"

        minSdk = 28

        targetSdk = 35

        versionCode = 1

        versionName = "1.0"

        buildConfigField(
            "String",
            "GROQ_API_KEY",
            "\"${localProperties.getProperty(
                "GROQ_API_KEY",
                ""
            )}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {

        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {

        sourceCompatibility =
            JavaVersion.VERSION_17

        targetCompatibility =
            JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
