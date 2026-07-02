package com.patrykmis.bar

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences as DataStorePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.patrykmis.bar.audio.AudioChannels
import com.patrykmis.bar.audio.AudioInputSource
import com.patrykmis.bar.format.Format
import com.patrykmis.bar.format.SampleRate
import com.patrykmis.bar.output.Retention
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class Preferences(private val context: Context) {
    fun interface OnPreferenceChangeListener {
        fun onPreferenceChanged(key: String)
    }

    companion object {
        private val TAG = Preferences::class.java.simpleName

        const val PREF_PERMISSIONS = "permissions"
        const val PREF_INITIALLY_PAUSED = "initially_paused"
        const val PREF_OUTPUT_DIR = "output_dir"
        const val PREF_OUTPUT_FORMAT = "output_format"
        const val PREF_INHIBIT_BATT_OPT = "inhibit_batt_opt"
        const val PREF_NATIVE_SAMPLE_RATE = "native_sample_rate"
        const val PREF_DEBUG_MODE = "debug_mode"
        const val PREF_VERSION = "version"

        // Not associated with a UI preference
        private const val PREF_AUDIO_SOURCE = "audio_source"
        private const val PREF_AUDIO_CHANNELS = "audio_channels"
        private const val PREF_FORMAT_NAME = "codec_name"
        private const val PREF_FORMAT_PARAM_PREFIX = "codec_param_"
        private const val PREF_FORMAT_SAMPLE_RATE_PREFIX = "codec_sample_rate_"
        const val PREF_OUTPUT_RETENTION = "output_retention"

        private val listeners = CopyOnWriteArraySet<OnPreferenceChangeListener>()

        fun isFormatKey(key: String): Boolean =
            key == PREF_FORMAT_NAME ||
                    key.startsWith(PREF_FORMAT_PARAM_PREFIX) ||
                    key.startsWith(PREF_FORMAT_SAMPLE_RATE_PREFIX)

        fun isRecordingSettingsKey(key: String): Boolean =
            isFormatKey(key) || key == PREF_AUDIO_SOURCE || key == PREF_AUDIO_CHANNELS
    }

    private val dataStore = context.applicationContext.settingsDataStore

    fun registerOnPreferenceChangeListener(listener: OnPreferenceChangeListener) {
        listeners.add(listener)
    }

    fun unregisterOnPreferenceChangeListener(listener: OnPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyPreferenceChanged(key: String) {
        listeners.forEach { it.onPreferenceChanged(key) }
    }

    private fun currentPreferences(): DataStorePreferences =
        runBlocking { dataStore.data.first() }

    private fun updatePreference(key: String, block: MutablePreferences.() -> Unit) {
        updatePreferences(listOf(key), block)
    }

    private fun updatePreferences(keys: Iterable<String>, block: MutablePreferences.() -> Unit) {
        runBlocking {
            dataStore.edit { preferences ->
                preferences.block()
            }
        }

        keys.forEach(::notifyPreferenceChanged)
    }

    private fun getString(key: String): String? =
        currentPreferences()[stringPreferencesKey(key)]

    private fun setString(key: String, value: String?) {
        val prefKey = stringPreferencesKey(key)
        updatePreference(key) {
            if (value == null) {
                remove(prefKey)
            } else {
                this[prefKey] = value
            }
        }
    }

    private fun getBoolean(key: String, default: Boolean): Boolean =
        currentPreferences()[booleanPreferencesKey(key)] ?: default

    private fun setBoolean(key: String, value: Boolean) {
        val prefKey = booleanPreferencesKey(key)
        updatePreference(key) {
            this[prefKey] = value
        }
    }

    /**
     * Get a unsigned integer preference value.
     *
     * @return Will never be [UInt.MAX_VALUE]
     */
    private fun getOptionalUint(key: String): UInt? {
        val value = currentPreferences()[intPreferencesKey(key)] ?: return null

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

        val prefKey = intPreferencesKey(key)
        updatePreference(key) {
            if (value == null) {
                remove(prefKey)
            } else {
                this[prefKey] = value.toInt()
            }
        }
    }

    var isDebugMode: Boolean
        get() = BuildConfig.FORCE_DEBUG_MODE || getBoolean(PREF_DEBUG_MODE, false)
        set(enabled) = setBoolean(PREF_DEBUG_MODE, enabled)

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
        get() = getString(PREF_OUTPUT_DIR)?.let { it.toUri() }
        set(uri) {
            val oldUri = outputDir
            if (oldUri == uri) {
                // URI is the same as before or both are null
                return
            }

            if (uri != null) {
                // Persist permissions for the new URI first
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            setString(PREF_OUTPUT_DIR, uri?.toString())

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
        get() = getBoolean(PREF_INITIALLY_PAUSED, false)
        set(enabled) = setBoolean(PREF_INITIALLY_PAUSED, enabled)

    /**
     * The saved audio source for recording.
     */
    var audioInputSource: AudioInputSource?
        get() = getString(PREF_AUDIO_SOURCE)?.let { AudioInputSource.getByPreferenceValue(it) }
        set(source) = setString(PREF_AUDIO_SOURCE, source?.preferenceValue)

    /**
     * The saved channel mode for recording.
     */
    var audioChannels: AudioChannels?
        get() = getString(PREF_AUDIO_CHANNELS)?.let { AudioChannels.getByPreferenceValue(it) }
        set(channels) = setString(PREF_AUDIO_CHANNELS, channels?.preferenceValue)

    /**
     * The saved output format.
     *
     * Use [getFormatParam]/[setFormatParam] and [getFormatSampleRate]/[setFormatSampleRate] to
     * get/set format-specific values.
     */
    var format: Format?
        get() = getString(PREF_FORMAT_NAME)?.let { Format.getByName(it) }
        set(format) = setString(PREF_FORMAT_NAME, format?.name)

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
     * Remove the default recording settings and the parameters for all formats.
     */
    fun resetRecordingSettings() {
        val keys = currentPreferences().asMap().keys
            .map { it.name }
            .filter(::isRecordingSettingsKey)

        if (keys.isEmpty()) {
            return
        }

        updatePreferences(keys) {
            for (key in keys) {
                removeRecordingSettingKey(key)
            }
        }
    }

    private fun MutablePreferences.removeRecordingSettingKey(key: String) {
        when {
            key == PREF_AUDIO_SOURCE ||
                    key == PREF_AUDIO_CHANNELS ||
                    key == PREF_FORMAT_NAME -> {
                remove(stringPreferencesKey(key))
            }

            key.startsWith(PREF_FORMAT_PARAM_PREFIX) ||
                    key.startsWith(PREF_FORMAT_SAMPLE_RATE_PREFIX) -> {
                remove(intPreferencesKey(key))
            }
        }
    }
}
