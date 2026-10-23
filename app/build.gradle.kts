plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.joyplayer.iptv"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.joyplayer.iptv"
        minSdk = 23
        targetSdk = 34
        val vc = (project.findProperty("vcode") as String?)?.toIntOrNull() ?: 1
        versionCode = vc
        versionName = "1.0.$vc"
    }
    val ks = rootProject.file("keystore/joyplayer.jks")
    if (ks.exists()) {
        signingConfigs {
            create("joy") {
                storeFile = ks
                storePassword = "joyplayer123"
                keyAlias = "joyplayer"
                keyPassword = "joyplayer123"
            }
        }
    }
    buildTypes {
        debug {
            if (ks.exists()) signingConfig = signingConfigs.getByName("joy")
            // optimized build: not debuggable + R8 (much faster and lighter, same install / update)
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("com.google.android.gms:play-services-cast-framework:21.5.0")
}
