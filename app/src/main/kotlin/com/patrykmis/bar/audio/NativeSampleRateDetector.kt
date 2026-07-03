package com.patrykmis.bar.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.os.SystemClock
import android.util.Log

object NativeSampleRateDetector {
    private val TAG = NativeSampleRateDetector::class.java.simpleName

    private const val FALLBACK_SAMPLE_RATE = 48_000
    private val SAMPLE_RATES = intArrayOf(48_000, 44_100)
    private val CHANNEL_CONFIGS = listOf(
        ChannelConfig("default", AudioFormat.CHANNEL_IN_DEFAULT),
        ChannelConfig("mono", AudioFormat.CHANNEL_IN_MONO),
        ChannelConfig("stereo", AudioFormat.CHANNEL_IN_STEREO),
    )

    fun detect(): Result {
        val startMs = SystemClock.elapsedRealtime()

        Log.i(
            TAG,
            "Starting native sample rate buffer heuristic: " +
                    "sampleRates=${SAMPLE_RATES.joinToString()}, " +
                    "channels=${CHANNEL_CONFIGS.joinToString { it.name }}, " +
                    "encoding=${AudioChannels.ENCODING}"
        )

        val attempts = CHANNEL_CONFIGS.flatMap { channelConfig ->
            SAMPLE_RATES.map { sampleRate ->
                testConfiguration(sampleRate, channelConfig)
            }
        }

        val selectedAttempt = selectBestAttempt(attempts)
        val detectedSampleRate = selectedAttempt?.sampleRate ?: FALLBACK_SAMPLE_RATE
        val fallbackUsed = selectedAttempt == null
        val durationMs = SystemClock.elapsedRealtime() - startMs

        Log.i(
            TAG,
            "Native sample rate buffer heuristic result: " +
                    "sampleRate=$detectedSampleRate, " +
                    "fallbackUsed=$fallbackUsed, " +
                    "selectedChannel=${selectedAttempt?.channelName ?: "none"}, " +
                    "selectedMinBuffer=${selectedAttempt?.minBufferSize ?: "none"}, " +
                    "durationMs=$durationMs"
        )

        return Result(
            detectedSampleRate,
            fallbackUsed,
            selectedAttempt?.channelName,
            durationMs,
            attempts,
        )
    }

    private fun testConfiguration(sampleRate: Int, channelConfig: ChannelConfig): Attempt {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig.value,
            AudioChannels.ENCODING,
        )
        val error = if (minBufferSize > 0) {
            null
        } else {
            "minBufferSize=$minBufferSize"
        }

        val attempt = Attempt(
            sampleRate,
            channelConfig.name,
            channelConfig.value,
            minBufferSize,
            error,
        )

        Log.i(
            TAG,
            "Native sample rate buffer test: " +
                    "sampleRate=${attempt.sampleRate}, " +
                    "channel=${attempt.channelName}(${attempt.channelConfig}), " +
                    "encoding=${AudioChannels.ENCODING}, " +
                    "minBufferSize=${attempt.minBufferSize}, " +
                    "supported=${attempt.minBufferSize > 0}, " +
                    "error=${attempt.error ?: "none"}"
        )

        return attempt
    }

    private fun selectBestAttempt(attempts: List<Attempt>): Attempt? {
        for (channelConfig in CHANNEL_CONFIGS) {
            val attemptsForChannel = attempts
                .filter {
                    it.channelName == channelConfig.name &&
                            it.minBufferSize > 0
                }

            if (attemptsForChannel.isNotEmpty()) {
                return attemptsForChannel.minWith(
                    compareBy<Attempt> { it.minBufferSize }
                        .thenBy { SAMPLE_RATES.indexOf(it.sampleRate) }
                )
            }
        }

        return null
    }

    data class Result(
        val detectedSampleRate: Int,
        val fallbackUsed: Boolean,
        val selectedChannelName: String?,
        val durationMs: Long,
        val attempts: List<Attempt>,
    )

    data class Attempt(
        val sampleRate: Int,
        val channelName: String,
        val channelConfig: Int,
        val minBufferSize: Int,
        val error: String?,
    )

    private data class ChannelConfig(
        val name: String,
        val value: Int,
    )
}
