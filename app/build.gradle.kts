plugins {
    id("com.android.application")
}

android {
    namespace = "com.cowboy.app"
    compileSdk = 37

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.cowboy.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
}
