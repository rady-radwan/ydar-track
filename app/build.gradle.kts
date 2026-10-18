plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
val keystoreFile = rootProject.file("keystore/ydartrack.jks")

android {
    namespace = "com.rady.worktracker"
    compileSdk = 35
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.rady.worktracker"
        minSdk = 26
        targetSdk = 35
        // v4 was 4; every GitHub build counts up from there so updates always install.
        versionCode = 10 + runNumber
        versionName = "5.0.$runNumber"
    }

    signingConfigs {
        create("ydar") {
            storeFile = keystoreFile
            storePassword = "ydartrack2026"
            keyAlias = "ydartrack"
            keyPassword = "ydartrack2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("ydar")
        }
        debug {
            // One permanent key, so every new build installs over the old one
            // and Google Drive sign-in recognises the app.
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("ydar")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.all { it.testLogging { events("passed", "failed", "standard_out"); showStandardStreams = true } }
    }

    // Code shared with the Windows app
    sourceSets["main"].java.srcDir("../common/src")

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Google Drive backup
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
