/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.extension

import android.os.Build

val Thread.threadIdCompat: Long
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        this.threadId()
    } else {
        @Suppress("DEPRECATION")
        this.id
    }
