plugins {
    alias(libs.plugins.aboutlibraries.android) apply false
    alias(libs.plugins.android.application) apply false
}

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}
