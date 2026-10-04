plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.crmod.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.crmod.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
        // Адрес бэка зашит в приложение. Пользователь его не вводит и не видит.
        buildConfigField("String", "SERVER", "\"http://147.45.185.70:3654\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Подписываем отладочным ключом, чтобы apk ставился на телефон без своего keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Библиотеки ONNX Runtime весят много, поэтому отдельный apk под каждый процессор:
    // arm64-v8a — телефон, x86_64 — эмулятор.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.2")
}
