package com.patrykmis.bar.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import androidx.annotation.StringRes
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.R

enum class AudioInputSource(
    val preferenceValue: String,
    @StringRes private val labelRes: Int,
    val source: Int,
) {
    Unprocessed(
        "unprocessed",
        R.string.audio_source_unprocessed,
        MediaRecorder.AudioSource.UNPROCESSED,
    ),
    VoiceRecognition(
        "voice_recognition",
        R.string.audio_source_voice_recognition,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
    ),
    Microphone(
        "microphone",
        R.string.audio_source_microphone,
        MediaRecorder.AudioSource.MIC,
    );

    fun displayName(context: Context): String = context.getString(labelRes)

    fun isAvailable(context: Context): Boolean =
        when (this) {
            Unprocessed -> isUnprocessedAvailable(context)
            VoiceRecognition, Microphone -> true
        }

    companion object {
        fun getByPreferenceValue(value: String?): AudioInputSource? =
            values().find { it.preferenceValue == value }

        fun available(context: Context): List<AudioInputSource> =
            values().filter { it.isAvailable(context) }

        fun default(context: Context): AudioInputSource =
            if (Unprocessed.isAvailable(context)) {
                Unprocessed
            } else {
                VoiceRecognition
            }

        fun fromPreferences(context: Context, prefs: Preferences): AudioInputSource =
            prefs.audioInputSource?.takeIf { it.isAvailable(context) } ?: default(context)

        private fun isUnprocessedAvailable(context: Context): Boolean {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            return audioManager
                .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
                ?.equals("true", ignoreCase = true) == true
        }
    }
}
