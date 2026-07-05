package com.patrykmis.bar.format

import android.media.MediaFormat
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.audio.AudioChannels
import com.patrykmis.bar.audio.AudioSampleFormat
import java.io.FileDescriptor

sealed class Format {
    /** User-facing name of the format. */
    abstract val name: String

    /** Details about the format parameter range and default value. */
    abstract val paramInfo: FormatParamInfo

    /** Details about the format parameter for a specific recording configuration. */
    open fun paramInfo(sampleRate: SampleRate, audioChannels: AudioChannels): FormatParamInfo =
        paramInfo

    /** Sample rates that should be offered for this format. */
    abstract val sampleRates: Array<SampleRate>

    /** Input PCM sample formats that can be offered for this output format. */
    abstract val sampleFormats: Array<AudioSampleFormat>

    /** The default sample rate for new selections of this format. */
    val defaultSampleRate: SampleRate
        get() = sampleRates.last()

    fun defaultParam(sampleRate: SampleRate, audioChannels: AudioChannels): UInt =
        when (val info = paramInfo(sampleRate, audioChannels)) {
            is RangedParamInfo -> when (info.type) {
                RangedParamType.Bitrate -> {
                    val scaled = if (info.scaleDefaultByChannels) {
                        info.default * audioChannels.count.toUInt()
                    } else {
                        info.default
                    }
                    info.toNearest(scaled.coerceAtMost(info.range.last))
                }

                RangedParamType.CompressionLevel -> info.default
            }

            NoParamInfo -> info.default
        }

    fun defaultParam(audioChannels: AudioChannels): UInt =
        defaultParam(defaultSampleRate, audioChannels)

    /** The MIME type of the container storing the encoded audio stream. */
    abstract val mimeTypeContainer: String

    /**
     * The MIME type of the encoded audio stream inside the container.
     *
     * May be the same as [mimeTypeContainer] for some formats.
     */
    abstract val mimeTypeAudio: String

    /** Whether the format takes the PCM samples as is without encoding. */
    abstract val passthrough: Boolean

    /** Whether the format is supported on the current device. */
    abstract val supported: Boolean

    /**
     * Create a [MediaFormat] representing the encoded audio with parameters matching the specified
     * input PCM audio format.
     *
     * @param sampleRate Must be one of [sampleRates].
     * @param audioChannels Must be supported by the selected input source.
     * @param sampleFormat Must be one of [sampleFormats].
     * @param param Format-specific parameter value. Must be valid according to [paramInfo].
     *
     * @throws IllegalArgumentException if [FormatParamInfo.validate] fails
     */
    fun getMediaFormat(
        sampleRate: SampleRate,
        audioChannels: AudioChannels,
        sampleFormat: AudioSampleFormat,
        param: UInt?,
    ): MediaFormat {
        require(sampleFormat in sampleFormats) {
            "Unsupported sample format for $name: $sampleFormat"
        }

        val info = paramInfo(sampleRate, audioChannels)

        if (param != null) {
            info.validate(param)
        }

        val format = MediaFormat().apply {
            setString(MediaFormat.KEY_MIME, mimeTypeAudio)
            setInteger(MediaFormat.KEY_CHANNEL_COUNT, audioChannels.count)
            setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate.value.toInt())
            setInteger(MediaFormat.KEY_PCM_ENCODING, sampleFormat.encoding)
            setInteger(KEY_X_FRAME_SIZE_IN_BYTES, sampleFormat.frameSize(audioChannels))
        }

        updateMediaFormat(format, param ?: info.default)

        return format
    }

    /**
     * Update [mediaFormat] with parameter keys relevant to the format-specific parameter.
     *
     * @param param Guaranteed to be valid according to [paramInfo]
     */
    protected abstract fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt)

    /**
     * Create an [Encoder] that produces [mediaFormat] output.
     *
     * @param mediaFormat The [MediaFormat] instance returned by [getMediaFormat].
     * @param container The [Container] instance returned by [getContainer].
     *
     * @throws Exception if the device does not support encoding with the parameters set in
     * [mediaFormat] or if configuring the encoder fails.
     */
    fun getEncoder(mediaFormat: MediaFormat, container: Container): Encoder =
        if (passthrough) {
            PassthroughEncoder(mediaFormat, container)
        } else {
            MediaCodecEncoder(mediaFormat, container)
        }

    fun isSampleFormatSupported(
        sampleRate: SampleRate,
        audioChannels: AudioChannels,
        sampleFormat: AudioSampleFormat,
    ): Boolean {
        if (sampleFormat !in sampleFormats ||
            sampleRate !in sampleRates ||
            !sampleFormat.isCaptureSupported(sampleRate, audioChannels)
        ) {
            return false
        }

        if (passthrough) {
            return true
        }

        val mediaFormat = try {
            getMediaFormat(
                sampleRate,
                audioChannels,
                sampleFormat,
                defaultParam(sampleRate, audioChannels),
            )
        } catch (e: Exception) {
            return false
        }

        return try {
            MediaCodecEncoder.findEncoderForFormat(mediaFormat) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Create a container muxer that takes encoded input and writes the muxed output to [fd].
     *
     * @param fd The container does not take ownership of the file descriptor.
     */
    abstract fun getContainer(fd: FileDescriptor): Container

    companion object {
        const val KEY_X_FRAME_SIZE_IN_BYTES = "x-frame-size-in-bytes"

        val all: Array<Format> = arrayOf(OpusFormat, AacFormat, FlacFormat, WaveFormat)
        private val default: Format = all.first { it.supported }

        data class RecordingSettings(
            val format: Format,
            val formatParam: UInt?,
            val sampleRate: SampleRate,
            val audioChannels: AudioChannels,
            val sampleFormat: AudioSampleFormat,
        )

        /** Find output format by name. */
        fun getByName(name: String): Format? = all.find { it.name == name }

        /**
         * Get the saved format from the preferences or fall back to the default.
         *
         * The parameter, if set, is clamped to the format's allowed parameter range. The sample
         * rate is returned only if it is valid for the selected format.
         */
        fun fromPreferences(prefs: Preferences): RecordingSettings {
            // Use the saved format if it is valid and supported on the current device. Otherwise, fall
            // back to the default.
            val format = prefs.format
                ?.let {
                    if (it.supported) {
                        it
                    } else {
                        null
                    }
                }
                ?: default

            val preferredSampleRate = prefs.getFormatSampleRate(format)
                ?.let { SampleRate(it) }
                ?.takeIf { it in format.sampleRates }

            val preferredSampleFormat = prefs.getFormatSampleFormat(format)
                ?.takeIf { it in format.sampleFormats }

            val sampleRates = format.sampleRates
                .asList()
                .withPreferred(preferredSampleRate, format.defaultSampleRate)
            val audioChannels = AudioChannels.values()
                .asList()
                .withPreferred(prefs.audioChannels, AudioChannels.default, AudioChannels.fallback)
            val sampleFormats = format.sampleFormats
                .asList()
                .withPreferred(preferredSampleFormat, AudioSampleFormat.default)

            val recordingSettings = sampleRates.asSequence()
                .flatMap { sampleRate ->
                    audioChannels.asSequence().flatMap { audioChannels ->
                        sampleFormats.asSequence().map { sampleFormat ->
                            RecordingSettings(format, null, sampleRate, audioChannels, sampleFormat)
                        }
                    }
                }
                .firstOrNull {
                    format.isSampleFormatSupported(it.sampleRate, it.audioChannels, it.sampleFormat)
                }
                ?: RecordingSettings(
                    format,
                    null,
                    format.defaultSampleRate,
                    AudioChannels.fallback,
                    AudioSampleFormat.default,
                )

            // Convert the saved value to the nearest valid value (eg. in case bitrate range or step
            // size in changed in a future version)
            val param = prefs.getFormatParam(format)?.let {
                format.paramInfo(
                    recordingSettings.sampleRate,
                    recordingSettings.audioChannels,
                ).toNearest(it)
            }

            return recordingSettings.copy(formatParam = param)
        }

        private fun <T> List<T>.withPreferred(vararg preferred: T?): List<T> =
            (preferred.filterNotNull() + this).distinct()
    }
}
