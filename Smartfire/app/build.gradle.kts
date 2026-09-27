plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.smartfire"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartfire"
        minSdk = 25
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"
    }
}
