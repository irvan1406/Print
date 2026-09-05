plugins {
    id("com.android.application")
}

android {
    namespace = "com.cetakpro.print"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cetakpro.print"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        buildConfigField("String", "WEB_APP_URL", "\"https://irvanmaulana.my.id/\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
}
