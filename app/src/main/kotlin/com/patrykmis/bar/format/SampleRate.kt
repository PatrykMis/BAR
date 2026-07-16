/*
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.format

@JvmInline
value class SampleRate(val value: UInt) {
    override fun toString(): String = "$value Hz"

    companion object {
        /**
         * Hardcoded list of sample rates supported by every [Format].
         *
         * Ideally, there would be a way to query what sample rates are supported for a given audio
         * source and then filter that list based on what the [Format] supports. Unfortunately, no such
         * API exists.
         */
        val all = arrayOf(
            SampleRate(8_000u),
            SampleRate(12_000u),
            SampleRate(16_000u),
            SampleRate(24_000u),
            SampleRate(44_100u),
            SampleRate(48_000u)
        )
        val default = all.last()
    }
}
