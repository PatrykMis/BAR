package com.patrykmis.bar.format

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.patrykmis.bar.audio.AudioChannels
import java.io.FileDescriptor

object AacFormat : Format() {
    override val name: String = "M4A/AAC"
    override val paramInfo: FormatParamInfo
        get() = bitrateConfigurations
            .getValue(SampleRate(48_000u) to AudioChannels.Stereo)
            .toParamInfo()
    override val sampleRates: Array<SampleRate>
        get() = SampleRate.all.filter { sampleRate ->
            bitrateConfigurations.keys.any { it.first == sampleRate }
        }.toTypedArray()

    // https://datatracker.ietf.org/doc/html/rfc6381#section-3.1
    override val mimeTypeContainer: String = "audio/mp4"
    override val mimeTypeAudio: String = MediaFormat.MIMETYPE_AUDIO_AAC
    override val passthrough: Boolean = false
    override val supported: Boolean = true

    override fun paramInfo(sampleRate: SampleRate, audioChannels: AudioChannels): FormatParamInfo =
        bitrateConfiguration(sampleRate, audioChannels).toParamInfo()

    override fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt) {
        mediaFormat.apply {
            val sampleRate = SampleRate(getInteger(MediaFormat.KEY_SAMPLE_RATE).toUInt())
            val audioChannels = AudioChannels.getByCount(getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                ?: throw IllegalArgumentException(
                    "Unsupported channel count: ${getInteger(MediaFormat.KEY_CHANNEL_COUNT)}"
                )
            val profile = bitrateConfiguration(sampleRate, audioChannels).profile(param)

            setInteger(MediaFormat.KEY_AAC_PROFILE, profile)
            setInteger(MediaFormat.KEY_BIT_RATE, param.toInt())
        }
    }

    override fun getContainer(fd: FileDescriptor): Container =
        MediaMuxerContainer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    private fun bitrateConfiguration(
        sampleRate: SampleRate,
        audioChannels: AudioChannels,
    ): AacBitrates = bitrateConfigurations[sampleRate to audioChannels]
        ?: throw IllegalArgumentException(
            "Unsupported AAC configuration: sampleRate=$sampleRate, channels=$audioChannels"
        )

    private fun AacBitrates.toParamInfo(): RangedParamInfo =
        RangedParamInfo(
            RangedParamType.Bitrate,
            minBitrate..maxBitrate,
            step,
            defaultBitrate,
            scaleDefaultByChannels = false,
        )

    private fun AacBitrates.profile(bitrate: UInt): Int =
        when {
            switchBitrateV2 != null && bitrate < switchBitrateV2 ->
                MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS

            bitrate < switchBitrate ->
                MediaCodecInfo.CodecProfileLevel.AACObjectHE

            else ->
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
        }

    private data class AacBitrates(
        val minBitrate: UInt,
        val maxBitrate: UInt,
        val step: UInt,
        val defaultBitrate: UInt,
        val switchBitrate: UInt,
        val switchBitrateV2: UInt?,
    )

    // AAC bitrate ranges and profile switch points are based on the Fraunhofer FDK AAC encoder
    // documentation shipped in AOSP:
    // https://android.googlesource.com/platform/external/aac/+/refs/heads/main/documentation/aacEncoder.pdf
    private val bitrateConfigurations = mapOf(
        (SampleRate(44_100u) to AudioChannels.Mono) to AacBitrates(
            18_000u,
            160_000u,
            8_875u,
            96_000u,
            40_000u,
            null,
        ),
        (SampleRate(44_100u) to AudioChannels.Stereo) to AacBitrates(
            18_000u,
            320_000u,
            18_875u,
            128_000u,
            64_000u,
            40_000u,
        ),
        (SampleRate(48_000u) to AudioChannels.Mono) to AacBitrates(
            18_000u,
            288_000u,
            16_875u,
            128_000u,
            56_000u,
            null,
        ),
        (SampleRate(48_000u) to AudioChannels.Stereo) to AacBitrates(
            28_000u,
            510_000u,
            30_125u,
            192_000u,
            128_000u,
            56_000u,
        ),
    )
}
