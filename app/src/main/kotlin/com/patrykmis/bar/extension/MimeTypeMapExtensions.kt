/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.extension

import android.webkit.MimeTypeMap

private val MIME_TYPES_COMPAT = hashMapOf(
    // Android 9 and 10 used libcore's MimeUtils, which supported fewer MIME types than the
    // mime-support database used by newer Android releases.
    "audio/mp4" to "m4a",
)

fun MimeTypeMap.getExtensionFromMimeTypeCompat(mimeType: String): String? =
    getExtensionFromMimeType(mimeType) ?: MIME_TYPES_COMPAT[mimeType]
