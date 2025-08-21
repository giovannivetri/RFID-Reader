import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android) // Corrected alias
    alias(libs.plugins.kotlin.compose)
}

android {
    // Load keystore properties
    val keystorePropertiesFile = file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        try {
            keystorePropertiesFile.inputStream().use { input ->
                keystoreProperties.load(input)
            }
        } catch (e: Exception) {
            println("Error loading keystore.properties: ${e.message}")
            // Handle error, perhaps by throwing an exception or using default values
        }
    } else {
        println("keystore.properties not found in app module. Release signing will not be configured.")
    }

    namespace = "com.example.lettore_rfid"
    compileSdk = 36

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists() && keystoreProperties.getProperty("storeFile") != null) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else {
                // This block is to allow the project to sync and build debug variants
                // if the keystore.properties or its values are missing.
                // Release builds will fail if these are not properly set.
                println("WARNING: Release signing information not found or incomplete in keystore.properties.")
                // Set dummy values or leave them to cause a build failure for release type,
                // which is often desired if signing info is missing.
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.lettore_rfid"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12" // Assicurati che sia compatibile con la tua versione di Kotlin
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.material) // Rimosso - Sostituito da Compose Material
    implementation(libs.androidx.activity) // Mantenuto, utile anche con Compose

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}