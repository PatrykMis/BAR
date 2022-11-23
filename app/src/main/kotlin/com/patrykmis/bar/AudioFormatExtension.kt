package com.patrykmis.bar

import android.media.AudioFormat

val AudioFormat.frameSizeInBytesCompat: Int
    get() = frameSizeInBytes
