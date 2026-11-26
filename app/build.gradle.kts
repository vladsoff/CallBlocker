plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 6
android {
    namespace = "com.fundata.callblocker"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.fundata.callblocker"
        minSdk = 29
        targetSdk = 33
        versionCode = runNumber
        versionName = "1.0.$runNumber"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("sign/keystore.jks")
            storePassword = System.getenv("KEY_STORE_PASSWORD") ?: "123456"
            keyAlias = "key0"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "123456"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    applicationVariants.configureEach {
        val appVersionName = versionName
        val buildTypeName = buildType.name

        outputs.configureEach {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "CallBlocker_${appVersionName}_${buildTypeName}.apk"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
