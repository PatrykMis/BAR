package com.patrykmis.bar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.patrykmis.bar.format.Format
import com.patrykmis.bar.format.SampleRate
import com.patrykmis.bar.output.Retention
import java.io.File

class Preferences(private val context: Context) {
    companion object {
        private val TAG = Preferences::class.java.simpleName

        const val PREF_PERMISSIONS = "permissions"
        const val PREF_INITIALLY_PAUSED = "initially_paused"
        const val PREF_OUTPUT_DIR = "output_dir"
        const val PREF_OUTPUT_FORMAT = "output_format"
        const val PREF_INHIBIT_BATT_OPT = "inhibit_batt_opt"
        const val PREF_VERSION = "version"

        // Not associated with a UI preference
        private const val PREF_DEBUG_MODE = "debug_mode"
        private const val PREF_FORMAT_NAME = "codec_name"
        private const val PREF_FORMAT_PARAM_PREFIX = "codec_param_"
        private const val PREF_FORMAT_SAMPLE_RATE_PREFIX = "codec_sample_rate_"
        private const val PREF_SAMPLE_RATE = "sample_rate"
        const val PREF_OUTPUT_RETENTION = "output_retention"

        fun isFormatKey(key: String): Boolean =
            key == PREF_FORMAT_NAME ||
                    key == PREF_SAMPLE_RATE ||
                    key.startsWith(PREF_FORMAT_PARAM_PREFIX) ||
                    key.startsWith(PREF_FORMAT_SAMPLE_RATE_PREFIX)
    }

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * Get a unsigned integer preference value.
     *
     * @return Will never be [UInt.MAX_VALUE]
     */
    private fun getOptionalUint(key: String): UInt? {
        // Use a sentinel value because doing contains + getInt results in TOCTOU issues
        val value = prefs.getInt(key, -1)

        return if (value == -1) {
            null
        } else {
            value.toUInt()
        }
    }

    /**
     * Set an unsigned integer preference to [value].
     *
     * @param value Must not be [UInt.MAX_VALUE]
     *
     * @throws IllegalArgumentException if [value] is [UInt.MAX_VALUE]
     */
    private fun setOptionalUint(key: String, value: UInt?) {
        // -1 (when casted to int) is used as a sentinel value
        if (value == UInt.MAX_VALUE) {
            throw IllegalArgumentException("$key value cannot be ${UInt.MAX_VALUE}")
        }

        prefs.edit {
            if (value == null) {
                remove(key)
            } else {
                putInt(key, value.toInt())
            }
        }
    }

    var isDebugMode: Boolean
        get() = BuildConfig.FORCE_DEBUG_MODE || prefs.getBoolean(PREF_DEBUG_MODE, false)
        set(enabled) = prefs.edit { putBoolean(PREF_DEBUG_MODE, enabled) }

    /**
     * Get the default output directory. The directory should always be writable and is suitable for
     * use as a fallback.
     */
    val defaultOutputDir: File = context.getExternalFilesDir(null)!!

    /**
     * The user-specified output directory.
     *
     * The URI, it not null, refers to a write-persisted URI provided by SAF. When a new URI is set,
     * persisted URI permissions for the old URI will be revoked and persisted write permissions
     * for the new URI will be requested. If the old and new URI are the same, nothing is done. If
     * persisting permissions for the new URI fails, the saved output directory is not changed.
     */
    var outputDir: Uri?
        get() = prefs.getString(PREF_OUTPUT_DIR, null)?.let { it.toUri() }
        set(uri) {
            val oldUri = outputDir
            if (oldUri == uri) {
                // URI is the same as before or both are null
                return
            }

            prefs.edit {
                if (uri != null) {
                    // Persist permissions for the new URI first
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    putString(PREF_OUTPUT_DIR, uri.toString())
                } else {
                    remove(PREF_OUTPUT_DIR)
                }
            }

            // Release persisted permissions on the old directory only after the new URI is set to
            // guarantee atomicity
            if (oldUri != null) {
                // It's not documented, but this can throw an exception when trying to release a
                // previously persisted URI that's associated with an app that's no longer installed
                try {
                    context.contentResolver.releasePersistableUriPermission(
                        oldUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error when releasing persisted URI permission for: $oldUri", e)
                }
            }

            // Clear all alert notifications. Having them disappear is a better user experience than
            // having the open/share actions use a no-longer-valid URI.
            Notifications(context).dismissAll()
        }

    /**
     * Get the user-specified output directory or the default if none was set. This method does not
     * perform any filesystem operations to check if the user-specified directory is still valid.
     */
    val outputDirOrDefault: Uri
        get() = outputDir ?: Uri.fromFile(defaultOutputDir)

    /**
     * The saved file retention (in days).
     *
     * Must not be [UInt.MAX_VALUE].
     */
    var outputRetention: Retention?
        get() = getOptionalUint(PREF_OUTPUT_RETENTION)?.let { Retention.fromRawPreferenceValue(it) }
        set(retention) = setOptionalUint(PREF_OUTPUT_RETENTION, retention?.toRawPreferenceValue())

    /**
     * Whether the recording should initially start in the paused state.
     */
    var initiallyPaused: Boolean
        get() = prefs.getBoolean(PREF_INITIALLY_PAUSED, false)
        set(enabled) = prefs.edit { putBoolean(PREF_INITIALLY_PAUSED, enabled) }

    /**
     * The saved output format.
     *
     * Use [getFormatParam]/[setFormatParam] and [getFormatSampleRate]/[setFormatSampleRate] to
     * get/set format-specific values.
     */
    var format: Format?
        get() = prefs.getString(PREF_FORMAT_NAME, null)?.let { Format.getByName(it) }
        set(format) = prefs.edit {
            if (format == null) {
                remove(PREF_FORMAT_NAME)
            } else {
                putString(PREF_FORMAT_NAME, format.name)
            }
        }

    /**
     * Get the format-specific parameter for [format].
     */
    fun getFormatParam(format: Format): UInt? =
        getOptionalUint(PREF_FORMAT_PARAM_PREFIX + format.name)

    /**
     * Set the format-specific parameter for [format].
     *
     * @param param Must not be [UInt.MAX_VALUE]
     *
     * @throws IllegalArgumentException if [param] is [UInt.MAX_VALUE]
     */
    fun setFormatParam(format: Format, param: UInt?) =
        setOptionalUint(PREF_FORMAT_PARAM_PREFIX + format.name, param)

    /**
     * Get the format-specific sample rate for [format].
     */
    fun getFormatSampleRate(format: Format): UInt? =
        getOptionalUint(PREF_FORMAT_SAMPLE_RATE_PREFIX + format.name)

    /**
     * Set the format-specific sample rate for [format].
     *
     * @param sampleRate Must not contain [UInt.MAX_VALUE]
     *
     * @throws IllegalArgumentException if [sampleRate] contains [UInt.MAX_VALUE]
     */
    fun setFormatSampleRate(format: Format, sampleRate: SampleRate?) =
        setOptionalUint(PREF_FORMAT_SAMPLE_RATE_PREFIX + format.name, sampleRate?.value)

    /**
     * Remove the default format preference and the parameters for all formats.
     */
    fun resetAllFormats() {
        val keys = prefs.all.keys.filter(::isFormatKey)
        prefs.edit {
            for (key in keys) {
                remove(key)
            }
        }
    }

    /**
     * Move the old app-wide sample rate preference to the currently selected format.
     */
    fun migrateLegacySampleRate() {
        val legacySampleRate = getOptionalUint(PREF_SAMPLE_RATE)?.let { SampleRate(it) } ?: return
        val (format, _, _) = Format.fromPreferences(this)

        if (getFormatSampleRate(format) == null && legacySampleRate in format.sampleRates) {
            setFormatSampleRate(format, legacySampleRate)
        }

        setOptionalUint(PREF_SAMPLE_RATE, null)
    }
}
