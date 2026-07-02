package com.patrykmis.bar.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import androidx.annotation.StringRes
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.R
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

    fun isSupported(sampleRate: SampleRate): Boolean =
        AudioRecord.getMinBufferSize(
            sampleRate.value.toInt(),
            channelConfig,
            ENCODING
        ) > 0

    companion object {
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        private val default = Stereo
        private val fallback = Mono

        fun getByPreferenceValue(value: String?): AudioChannels? =
            values().find { it.preferenceValue == value }

        fun available(sampleRate: SampleRate): List<AudioChannels> =
            values().filter { it.isSupported(sampleRate) }.ifEmpty { listOf(fallback) }

        fun fromPreferences(prefs: Preferences, sampleRate: SampleRate): AudioChannels {
            val available = available(sampleRate)

            return prefs.audioChannels?.takeIf { it in available }
                ?: default.takeIf { it in available }
                ?: available.first()
        }
    }
}
