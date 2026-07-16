/*
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.util.Log
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

    fun needsUnsupportedWarning(context: Context): Boolean =
        this == Unprocessed && !isUnprocessedSupported(context)

    companion object {
        private val TAG = AudioInputSource::class.java.simpleName

        fun getByPreferenceValue(value: String?): AudioInputSource? =
            values().find { it.preferenceValue == value }

        fun available(): List<AudioInputSource> = values().toList()

        fun default(context: Context): AudioInputSource =
            if (isUnprocessedSupported(context)) {
                Unprocessed
            } else {
                VoiceRecognition
            }

        fun fromPreferences(context: Context, prefs: Preferences): AudioInputSource =
            prefs.audioInputSource ?: default(context)

        fun isUnprocessedSupported(context: Context): Boolean {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val property =
                audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)

            Log.i(
                TAG,
                "${AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED}: $property"
            )

            return property?.equals("true", ignoreCase = true) == true
        }
    }
}
