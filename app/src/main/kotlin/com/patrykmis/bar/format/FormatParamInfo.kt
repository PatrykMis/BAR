/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.format

sealed class FormatParamInfo(val default: UInt) {
    /**
     * Ensure that [param] is valid.
     *
     * @throws IllegalArgumentException if [param] is invalid
     */
    abstract fun validate(param: UInt)

    /**
     * Convert a potentially-invalid [param] value to the nearest valid value.
     */
    abstract fun toNearest(param: UInt): UInt

    /**
     * Format [param] to present as a user-facing string.
     */
    abstract fun format(param: UInt): String
}

enum class RangedParamType {
    CompressionLevel,
    Bitrate,
}

class RangedParamInfo(
    val type: RangedParamType,
    val range: UIntRange,
    val stepSize: UInt,
    default: UInt,
    val scaleDefaultByChannels: Boolean = true,
) : FormatParamInfo(default) {
    init {
        require(stepSize > 0u) { "stepSize must be greater than 0" }
    }

    val values: List<UInt> = when (type) {
        RangedParamType.Bitrate -> buildPresetBitrates()
        RangedParamType.CompressionLevel -> buildSteppedValues()
    }

    private fun buildPresetBitrates(): List<UInt> {
        val values = COMMON_BITRATES.filter { it in range } + listOfNotNull(
            default.takeIf { it in range },
            range.last.takeIf { it !in COMMON_BITRATES },
        )

        return values.distinct().sorted()
    }

    private fun buildSteppedValues(): List<UInt> = buildList {
        var value = range.first

        while (value <= range.last) {
            add(value)

            if (range.last - value < stepSize) {
                break
            }

            value += stepSize
        }

        if (lastOrNull() != range.last) {
            add(range.last)
        }

        if (default in range) {
            add(default)
        }
    }.distinct().sorted()

    override fun validate(param: UInt) {
        if (param !in range) {
            throw IllegalArgumentException(
                "Parameter ${format(param)} is not in the range: " +
                        "[${format(range.first)}, ${format(range.last)}]"
            )
        }
    }

    /** Clamp [param] to [range] and snap to the nearest selectable value. */
    override fun toNearest(param: UInt): UInt = values[indexOfNearest(param)]

    fun indexOfNearest(param: UInt): Int {
        var nearestIndex = 0
        var nearestDistance = UInt.MAX_VALUE

        for ((index, value) in values.withIndex()) {
            val distance = if (value > param) {
                value - param
            } else {
                param - value
            }

            if (
                distance < nearestDistance ||
                distance == nearestDistance && value > values[nearestIndex]
            ) {
                nearestIndex = index
                nearestDistance = distance
            }
        }

        return nearestIndex
    }

    override fun format(param: UInt): String =
        when (type) {
            RangedParamType.CompressionLevel -> param.toString()
            RangedParamType.Bitrate -> "${param / 1_000u} kbps"
        }

    companion object {
        private val COMMON_BITRATES = listOf(
            8_000u,
            16_000u,
            24_000u,
            32_000u,
            48_000u,
            64_000u,
            80_000u,
            96_000u,
            112_000u,
            128_000u,
            160_000u,
            192_000u,
            224_000u,
            256_000u,
            320_000u,
            384_000u,
            510_000u,
        )
    }
}

object NoParamInfo : FormatParamInfo(0u) {
    override fun validate(param: UInt) {
        // Always valid
    }

    override fun toNearest(param: UInt): UInt = param

    override fun format(param: UInt): String = ""
}
