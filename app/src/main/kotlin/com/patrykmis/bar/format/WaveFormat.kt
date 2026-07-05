package com.patrykmis.bar.format

import android.media.MediaFormat
import com.patrykmis.bar.audio.AudioSampleFormat
import java.io.FileDescriptor

object WaveFormat : Format() {
    override val name: String = "WAV/PCM"
    override val paramInfo: FormatParamInfo = NoParamInfo
    override val sampleRates: Array<SampleRate> = SampleRate.all
    override val sampleFormats: Array<AudioSampleFormat> = arrayOf(
        AudioSampleFormat.Pcm16,
        AudioSampleFormat.Pcm24,
        AudioSampleFormat.Pcm32,
        AudioSampleFormat.PcmFloat,
    )

    // Should be "audio/vnd.wave" [1], but Android only recognizes "audio/x-wav" [2] for the
    // purpose of picking an appropriate file extension when creating a file via SAF.
    // [1] https://datatracker.ietf.org/doc/html/rfc2361
    // [2] https://android.googlesource.com/platform/external/mime-support/+/refs/tags/android-12.1.0_r5/mime.types#571
    override val mimeTypeContainer: String = "audio/x-wav"
    override val mimeTypeAudio: String = "audio/x-wav"
    override val passthrough: Boolean = true
    override val supported: Boolean = true

    override fun updateMediaFormat(mediaFormat: MediaFormat, param: UInt) {
        // Not needed
    }

    override fun getContainer(fd: FileDescriptor): Container =
        WaveContainer(fd)
}
