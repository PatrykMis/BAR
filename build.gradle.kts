/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-FileCopyrightText: 2023-2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
    alias(libs.plugins.aboutlibraries.android) apply false
    alias(libs.plugins.android.application) apply false
}

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}
