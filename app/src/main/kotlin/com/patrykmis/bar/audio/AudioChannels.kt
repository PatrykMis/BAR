package com.patrykmis.bar.audio

import android.content.Context
import android.media.AudioFormat
import androidx.annotation.StringRes
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.R
import com.patrykmis.bar.format.Format
import com.patrykmis.bar.format.SampleRate

enum class AudioChannels(
    val preferenceValue: String,
    @StringRes private val labelRes: Int,
    val count: Int,
    val channelConfig: Int,
) {
    Mono("mono", R.string.audio_channels_mono, 1, AudioFormat.CHANNEL_IN_MONO),
    Stereo("stereo", R.string.audio_channels_stereo, 2, AudioFormat.CHANNEL_IN_STEREO);

    fun displayName(context: Context): String = context.getString(labelRes)

    fun isSupported(
        format: Format,
        sampleRate: SampleRate,
        sampleFormat: AudioSampleFormat,
    ): Boolean =
        format.isSampleFormatSupported(sampleRate, this, sampleFormat)

    companion object {
        val default = Stereo
        val fallback = Mono

        fun getByPreferenceValue(value: String?): AudioChannels? =
            values().find { it.preferenceValue == value }

        fun getByCount(count: Int): AudioChannels? =
            values().find { it.count == count }

        fun available(
            format: Format,
            sampleRate: SampleRate,
            sampleFormat: AudioSampleFormat,
        ): List<AudioChannels> =
            values()
                .filter { it.isSupported(format, sampleRate, sampleFormat) }
                .ifEmpty { listOf(fallback) }

        fun fromPreferences(
            prefs: Preferences,
            format: Format,
            sampleRate: SampleRate,
            sampleFormat: AudioSampleFormat,
        ): AudioChannels {
            val available = available(format, sampleRate, sampleFormat)

            return prefs.audioChannels?.takeIf { it in available }
                ?: default.takeIf { it in available }
                ?: available.first()
        }
    }
}
