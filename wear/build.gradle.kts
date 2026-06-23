plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.ptsdalert.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ptsdalert.wear"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(libs.health.services.client)
    implementation(libs.guava)
    // Compose runtime required by kotlin.compose plugin (pre-existing scaffold constraint)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
}
