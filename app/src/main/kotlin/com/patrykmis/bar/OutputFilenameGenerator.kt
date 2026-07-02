package com.patrykmis.bar

import android.net.Uri
import android.util.Log
import com.patrykmis.bar.output.OutputDirUtils
import java.text.ParsePosition
import java.time.DateTimeException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.SignStyle
import java.time.temporal.ChronoField
import java.time.temporal.Temporal

data class OutputFilename(
    val value: String,
    val redacted: String,
) {
    override fun toString() = redacted
}

/**
 * Helper class for determining a recording's output filename.
 */
class OutputFilenameGenerator {
    private lateinit var _recordingTimestamp: ZonedDateTime
    val recordingTimestamp: ZonedDateTime
        get() = synchronized(this) {
            _recordingTimestamp
        }

    private val formatter = FORMATTER

    val redactor = object : OutputDirUtils.Redactor {
        override fun redact(msg: String): String = msg

        override fun redact(uri: Uri): String = Uri.decode(uri.toString())
    }

    private lateinit var _filename: OutputFilename
    val filename: OutputFilename
        get() = synchronized(this) {
            _filename
        }

    private fun formatRecordingTimestamp(): String {
        if (!this::_recordingTimestamp.isInitialized) {
            _recordingTimestamp = ZonedDateTime.now()
        }

        return formatter.format(_recordingTimestamp)
    }

    fun update(): OutputFilename {
        synchronized(this) {
            val newFilename = "${formatRecordingTimestamp()}_mic"
                // AOSP's SAF automatically replaces invalid characters with underscores, but just in
                // case an OEM fork breaks that, do the replacement ourselves to prevent directory
                // traversal attacks.
                .replace('/', '_').trim()

            _filename = OutputFilename(newFilename, redactor.redact(newFilename))

            Log.i(TAG, "Updated filename: $_filename")

            return _filename
        }
    }

    private fun parseTimestamp(input: String, startPos: Int): Temporal? {
        val pos = ParsePosition(startPos)
        val parsed = formatter.parse(input, pos)

        return parsed.query(ZonedDateTime::from)
    }

    fun parseTimestampFromFilename(name: String): Temporal? {
        val redacted = redactTruncate(name)
        val timestamp = try {
            // The date is guaranteed to be at the beginning of the filename. Try to parse it,
            // ignoring unparsed text at the end.
            parseTimestamp(name, 0)
        } catch (e: DateTimeParseException) {
            null
        } catch (e: DateTimeException) {
            Log.w(TAG, "Unexpected non-DateTimeParseException error", e)
            null
        }

        Log.d(TAG, "Parsed $timestamp from $redacted")

        return timestamp
    }

    companion object {
        private val TAG = OutputFilenameGenerator::class.java.simpleName

        // Eg. 20220429_180249.123-0400
        private val FORMATTER = DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('_')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .appendOffset("+HHMMss", "+0000")
            .toFormatter()

        fun redactTruncate(msg: String): String = buildString {
            val n = 2

            if (msg.length > 2 * n) {
                append(msg.substring(0, n))
            }
            append("<...>")
            if (msg.length > 2 * n) {
                append(msg.substring(msg.length - n))
            }
        }
    }
}
