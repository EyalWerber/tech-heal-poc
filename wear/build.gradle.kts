plugins {
    alias(libs.plugins.android.application)
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(libs.health.services.client)
    implementation(libs.guava)
}
