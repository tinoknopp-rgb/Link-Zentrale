plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Für IzzyOnDroid/F-Droid: keinen verschlüsselten Google-Abhängigkeitsblock in die APK schreiben
    dependenciesInfo { includeInApk = false; includeInBundle = false }
    namespace = "de.tino.links"
    compileSdk = 35
    defaultConfig {
        applicationId = "de.tino.links"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VC") ?: "1").toInt()
        versionName = "1." + (System.getenv("VC") ?: "1")
        // Woher die App ihre eigenen Updates holt – kommt aus dem Build (kein Konto fest im Code)
        buildConfigField("String", "APP_REPO", "\"" + (System.getenv("APP_REPO") ?: "") + "\"")
    }
    // Signatur-Schlüssel kommt als GitHub-Secret (KEYSTORE_B64) – nie im Code
    signingConfigs {
        create("tino") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "debug.jks")
            storePassword = System.getenv("KEYSTORE_PASS") ?: "android"
            keyAlias = System.getenv("KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("KEYSTORE_PASS") ?: "android"
        }
    }
    buildTypes {
        debug { if (System.getenv("KEYSTORE_FILE") != null) signingConfig = signingConfigs.getByName("tino") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) signingConfigs.getByName("tino") else signingConfigs.getByName("debug")
        }
    }
    packaging { resources.excludes += "META-INF/*" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")   // echtes org.json für Auftrag-Tests
    implementation("com.goterl:lazysodium-android:5.1.0@aar")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
}
