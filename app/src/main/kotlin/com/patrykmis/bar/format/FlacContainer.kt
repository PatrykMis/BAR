/*
 * SPDX-FileCopyrightText: 2022-2024 Andrew Gunnerson
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("OPT_IN_IS_NOT_ENABLED")
@file:OptIn(ExperimentalUnsignedTypes::class)

package com.patrykmis.bar.format

import android.media.MediaCodec
import android.media.MediaFormat
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.patrykmis.bar.writeFully
import java.io.FileDescriptor
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Dummy FLAC container wrapper that updates the STREAMINFO sample count and MD5 fields when
 * complete.
 *
 * [MediaCodec] already produces a well-formed FLAC file, thus this class writes those samples
 * directly to the output file.
 *
 * @param fd Output file descriptor. This class does not take ownership of it and it should not
 * be touched outside of this class until [stop] is called and returns.
 */
class FlacContainer(private val fd: FileDescriptor) : Container, InputSampleConsumer {
    private var isStarted = false
    private var numFrames = 0uL
    private var receivedEof = false
    private var track = -1
    private val md5 = MessageDigest.getInstance("MD5")

    override fun start() {
        if (isStarted) {
            throw IllegalStateException("Container already started")
        }

        Os.lseek(fd, 0, OsConstants.SEEK_SET)
        Os.ftruncate(fd, 0)

        isStarted = true
    }

    override fun stop() {
        if (!isStarted) {
            throw IllegalStateException("Container not started")
        }

        isStarted = false

        if (receivedEof) {
            Log.d(TAG, "Setting sample count and MD5 fields in header")
            setStreamInfoFields(md5.digest())
        }
    }

    override fun release() {
        if (isStarted) {
            stop()
        }
    }

    override fun addTrack(mediaFormat: MediaFormat): Int {
        if (isStarted) {
            throw IllegalStateException("Container already started")
        } else if (track >= 0) {
            throw IllegalStateException("Track already added")
        }

        track = 0
        return track
    }

    override fun writeSamples(
        trackIndex: Int, byteBuffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        if (!isStarted) {
            throw IllegalStateException("Container not started")
        } else if (track < 0) {
            throw IllegalStateException("No track has been added")
        } else if (track != trackIndex) {
            throw IllegalStateException("Invalid track: $trackIndex")
        }

        writeFully(fd, byteBuffer)

        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            receivedEof = true
            Log.d(
                TAG,
                "Received EOF; final presentation timestamp: ${bufferInfo.presentationTimeUs}; " +
                        "input frames: $numFrames"
            )
        }
    }

    override fun consumeInputSamples(byteBuffer: ByteBuffer, frameSize: Int) {
        // FLAC STREAMINFO stores an MD5 checksum of the unencoded interleaved PCM samples.
        numFrames += (byteBuffer.remaining() / frameSize).toULong()
        md5.update(byteBuffer)
    }

    /**
     * Write the sample count and PCM MD5 checksum to the STREAMINFO metadata block of a FLAC file.
     *
     * @throws IOException If FLAC metadata does not appear to be valid or if [numFrames] exceeds
     * the bounds of a 36-bit integer
     */
    private fun setStreamInfoFields(md5: ByteArray) {
        Os.lseek(fd, 0, OsConstants.SEEK_SET)

        // Magic (4 bytes)
        // + metadata block header (4 bytes)
        // + streaminfo block (34 bytes)
        val buf = UByteArray(STREAMINFO_END_OFFSET)

        if (Os.read(fd, buf.asByteArray(), 0, buf.size) != buf.size) {
            throw IOException("EOF reached when reading FLAC headers")
        }

        // Validate the magic
        if (ByteBuffer.wrap(buf.asByteArray(), 0, 4) !=
            ByteBuffer.wrap(FLAC_MAGIC.asByteArray())
        ) {
            throw IOException("FLAC magic not found")
        }

        // Validate that the first metadata block is STREAMINFO and has the correct size
        if (buf[4] and 0x7fu != 0.toUByte()) {
            throw IOException("First metadata block is not STREAMINFO")
        }

        val streamInfoSize = buf[5].toUInt().shl(16) or
                buf[6].toUInt().shl(8) or buf[7].toUInt()
        if (streamInfoSize < 34u) {
            throw IOException("STREAMINFO block is too small")
        }

        if (numFrames >= 2uL.shl(36)) {
            throw IOException("Frame count cannot be represented in FLAC: $numFrames")
        }

        // Total samples field is a 36-bit integer that begins 4 bits into the 21st byte
        buf[STREAMINFO_SAMPLE_COUNT_OFFSET] =
            (buf[STREAMINFO_SAMPLE_COUNT_OFFSET] and 0xf0u) or
                    (numFrames.shr(32) and 0xfu).toUByte()
        buf[STREAMINFO_SAMPLE_COUNT_OFFSET + 1] = (numFrames.shr(24) and 0xffu).toUByte()
        buf[STREAMINFO_SAMPLE_COUNT_OFFSET + 2] = (numFrames.shr(16) and 0xffu).toUByte()
        buf[STREAMINFO_SAMPLE_COUNT_OFFSET + 3] = (numFrames.shr(8) and 0xffu).toUByte()
        buf[STREAMINFO_SAMPLE_COUNT_OFFSET + 4] = (numFrames and 0xffu).toUByte()

        if (md5.size != STREAMINFO_MD5_SIZE) {
            throw IOException("Invalid MD5 digest size: ${md5.size}")
        }
        for (i in md5.indices) {
            buf[STREAMINFO_MD5_OFFSET + i] = md5[i].toUByte()
        }

        Os.lseek(fd, STREAMINFO_SAMPLE_COUNT_OFFSET.toLong(), OsConstants.SEEK_SET)
        val patchSize = STREAMINFO_END_OFFSET - STREAMINFO_SAMPLE_COUNT_OFFSET
        writeFully(fd, buf.asByteArray(), STREAMINFO_SAMPLE_COUNT_OFFSET, patchSize)
    }

    companion object {
        private val TAG = FlacContainer::class.java.simpleName
        private val FLAC_MAGIC = ubyteArrayOf(0x66u, 0x4cu, 0x61u, 0x43u) // fLaC
        private const val STREAMINFO_SAMPLE_COUNT_OFFSET = 21
        private const val STREAMINFO_MD5_OFFSET = 26
        private const val STREAMINFO_MD5_SIZE = 16
        private const val STREAMINFO_END_OFFSET = STREAMINFO_MD5_OFFSET + STREAMINFO_MD5_SIZE
    }
}
