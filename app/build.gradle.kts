import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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

    compileSdk = 34
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.patrykmis.bar"
        minSdk = 30
        targetSdk = 33
        versionCode = 1
        versionName = versionCode.toString()
        resourceConfigurations.addAll(
            listOf(
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
        )

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
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_17)
        targetCompatibility(JavaVersion.VERSION_17)
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
    buildFeatures {
        buildConfig = true
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
