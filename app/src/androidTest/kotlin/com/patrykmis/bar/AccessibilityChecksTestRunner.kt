/*
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar

import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.runner.AndroidJUnitRunner
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType

class AccessibilityChecksTestRunner : AndroidJUnitRunner() {
    init {
        AccessibilityChecks.enable()
            .setRunChecksFromRootView(true)
            .setThrowExceptionFor(AccessibilityCheckResultType.ERROR)
    }
}
