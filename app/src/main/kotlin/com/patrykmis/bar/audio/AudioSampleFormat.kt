/*
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.annotation.StringRes
import com.patrykmis.bar.R
import com.patrykmis.bar.format.Format
import com.patrykmis.bar.format.SampleRate

enum class AudioSampleFormat(
    val preferenceValue: String,
    @StringRes private val labelRes: Int,
    val encoding: Int,
    val bytesPerSample: Int,
) {
    Pcm16(
        "pcm_16bit",
        R.string.audio_sample_format_pcm_16bit,
        AudioFormat.ENCODING_PCM_16BIT,
        2,
    ),
    Pcm24(
        "pcm_24bit",
        R.string.audio_sample_format_pcm_24bit,
        AudioFormat.ENCODING_PCM_24BIT_PACKED,
        3,
    ),
    Pcm32(
        "pcm_32bit",
        R.string.audio_sample_format_pcm_32bit,
        AudioFormat.ENCODING_PCM_32BIT,
        4,
    ),
    PcmFloat(
        "pcm_float",
        R.string.audio_sample_format_pcm_float,
        AudioFormat.ENCODING_PCM_FLOAT,
        4,
    );

    fun displayName(context: Context): String = context.getString(labelRes)

    val needsHigherBitDepthWarning: Boolean
        get() = this != Pcm16

    fun frameSize(audioChannels: AudioChannels): Int =
        bytesPerSample * audioChannels.count

    fun isCaptureSupported(sampleRate: SampleRate, audioChannels: AudioChannels): Boolean =
        AudioRecord.getMinBufferSize(
            sampleRate.value.toInt(),
            audioChannels.channelConfig,
            encoding,
        ) > 0

    companion object {
        val default = Pcm16

        fun getByPreferenceValue(value: String?): AudioSampleFormat? =
            values().find { it.preferenceValue == value }

        fun available(
            format: Format,
            sampleRate: SampleRate,
            audioChannels: AudioChannels,
        ): List<AudioSampleFormat> =
            format.sampleFormats
                .filter { format.isSampleFormatSupported(sampleRate, audioChannels, it) }
                .ifEmpty { listOf(default) }
    }
}
