import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.aboutlibraries.android)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val useKeystoreProperties = keystorePropertiesFile.canRead()
val keystoreProperties = Properties()
if (useKeystoreProperties) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

kotlin {
    jvmToolchain(21)
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
        version = release(37) {
            minorApiLevel = 1
        }
    }
    buildToolsVersion = "37.0.0"

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
        targetSdk = 37
        versionCode = 1
        versionName = versionCode.toString()
        testInstrumentationRunner = "com.patrykmis.bar.AccessibilityChecksTestRunner"

        buildConfigField(
            "String", "PROVIDER_AUTHORITY",
            "APPLICATION_ID + \".provider\""
        )
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "FORCE_DEBUG_MODE", "true")
            applicationIdSuffix = ".debug"
        }
        release {
            buildConfigField("boolean", "FORCE_DEBUG_MODE", "false")

            optimization {
                enable = true
            }

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
    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.accessibility)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
