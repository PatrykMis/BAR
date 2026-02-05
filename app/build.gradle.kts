import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val useKeystoreProperties = keystorePropertiesFile.canRead()
val keystoreProperties = Properties()
if (useKeystoreProperties) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

kotlin {
    jvmToolchain(17)
}

android {
    if (useKeystoreProperties) {
        signingConfigs {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"]!!)
                storePassword = keystoreProperties["storePassword"] as String
                enableV2Signing = false
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    namespace = "com.patrykmis.bar"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    buildToolsVersion = "36.1.0"

    androidResources {
        localeFilters += listOf(
            "en",
            "es",
            "fr",
            "iw",
            "pl",
            "ru",
            "sk",
            "tr",
            "zh-rCN"
        )
    }

    defaultConfig {
        applicationId = "com.patrykmis.bar"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = versionCode.toString()

        buildConfigField(
            "String", "PROVIDER_AUTHORITY",
            "APPLICATION_ID + \".provider\""
        )
        resValue("string", "provider_authority", "$applicationId.provider")
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "FORCE_DEBUG_MODE", "true")
        }

        create("debugOpt") {
            buildConfigField("boolean", "FORCE_DEBUG_MODE", "true")

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            signingConfig = signingConfigs.getByName("debug")
        }

        getByName("release") {
            buildConfigField("boolean", "FORCE_DEBUG_MODE", "false")

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            if (useKeystoreProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    buildFeatures {
        buildConfig = true
        resValues = true
        viewBinding = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources.excludes.addAll(
            listOf(
                "DebugProbesKt.bin",
                "META-INF/**.version",
                "kotlin-tooling-metadata.json",
                "kotlin/**.kotlin_builtins"
            )
        )
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
}
