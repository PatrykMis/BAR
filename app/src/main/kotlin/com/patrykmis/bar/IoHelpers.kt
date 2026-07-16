/*
 * SPDX-FileCopyrightText: 2024 Andrew Gunnerson
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar

import android.system.Os
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer

fun writeFully(fd: FileDescriptor, buffer: ByteBuffer) {
    while (buffer.hasRemaining()) {
        val n = Os.write(fd, buffer)
        if (n == 0) {
            throw IOException("Unexpected EOF when writing data")
        }
    }
}

fun writeFully(fd: FileDescriptor, bytes: ByteArray, byteOffset: Int, byteCount: Int) {
    var offset = byteOffset
    var remaining = byteCount

    while (remaining > 0) {
        val n = Os.write(fd, bytes, offset, remaining)
        if (n == 0) {
            throw IOException("Unexpected EOF when writing data")
        }

        offset += n
        remaining -= n
    }
}
