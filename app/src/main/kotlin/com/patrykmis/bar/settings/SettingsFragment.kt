package com.patrykmis.bar.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.patrykmis.bar.BuildConfig
import com.patrykmis.bar.Permissions
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.R
import com.patrykmis.bar.audio.AudioChannels
import com.patrykmis.bar.audio.AudioInputSource
import com.patrykmis.bar.extension.formattedString
import com.patrykmis.bar.format.Format
import com.patrykmis.bar.format.NoParamInfo
import com.patrykmis.bar.format.RangedParamInfo
import com.patrykmis.bar.output.Retention

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener,
    Preference.OnPreferenceClickListener, Preferences.OnPreferenceChangeListener {
    private lateinit var prefs: Preferences
    private lateinit var prefPermissions: Preference
    private lateinit var prefOutputDir: Preference
    private lateinit var prefOutputFormat: Preference
    private lateinit var prefInhibitBatteryOpt: SwitchPreferenceCompat
    private lateinit var prefNativeSampleRate: Preference
    private lateinit var prefDebugMode: SwitchPreferenceCompat
    private lateinit var prefVersion: Preference

    private val requestPermissionRequired =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.any { it.key in Permissions.REQUIRED && !it.value }) {
                startActivity(Permissions.getAppInfoIntent(requireContext()))
            }
            refreshPermissionsState()
        }
    private val requestInhibitBatteryOpt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshInhibitBatteryOptState()
        }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val context = requireContext()

        prefs = Preferences(context)

        prefPermissions = findPreference(Preferences.PREF_PERMISSIONS)!!
        prefPermissions.onPreferenceClickListener = this
        refreshPermissionsState()

        prefOutputDir = findPreference(Preferences.PREF_OUTPUT_DIR)!!
        prefOutputDir.onPreferenceClickListener = this
        refreshOutputDir()

        prefOutputFormat = findPreference(Preferences.PREF_OUTPUT_FORMAT)!!
        prefOutputFormat.onPreferenceClickListener = this
        refreshOutputFormat()

        prefInhibitBatteryOpt = findPreference(Preferences.PREF_INHIBIT_BATT_OPT)!!
        prefInhibitBatteryOpt.onPreferenceChangeListener = this

        prefNativeSampleRate = findPreference(Preferences.PREF_NATIVE_SAMPLE_RATE)!!
        prefNativeSampleRate.onPreferenceClickListener = this

        prefDebugMode = findPreference(Preferences.PREF_DEBUG_MODE)!!
        prefDebugMode.onPreferenceChangeListener = this
        refreshDebugMode()

        prefVersion = findPreference(Preferences.PREF_VERSION)!!
        prefVersion.onPreferenceClickListener = this
        refreshVersion()
    }

    override fun onStart() {
        super.onStart()

        prefs.registerOnPreferenceChangeListener(this)

        // Changing permissions or battery state does not cause a reload of the activity.
        refreshPermissionsState()
        refreshOutputDir()
        refreshOutputFormat()
        refreshInhibitBatteryOptState()
        refreshDebugMode()
    }

    override fun onStop() {
        super.onStop()

        prefs.unregisterOnPreferenceChangeListener(this)
    }

    private fun refreshOutputDir() {
        val context = requireContext()
        val outputDirUri = prefs.outputDirOrDefault
        val outputRetention = Retention.fromPreferences(prefs).toFormattedString(context)

        val summary = getString(R.string.pref_output_dir_desc)
        prefOutputDir.summary =
            "${summary}\n\n${outputDirUri.formattedString} (${outputRetention})"
    }

    private fun refreshOutputFormat() {
        val context = requireContext()
        val (format, formatParamSaved, sampleRateSaved) = Format.fromPreferences(prefs)
        val sampleRate = sampleRateSaved ?: format.defaultSampleRate
        val audioSource = AudioInputSource.fromPreferences(context, prefs)
        val audioChannels = AudioChannels.fromPreferences(prefs, sampleRate)
        val formatParam = formatParamSaved ?: format.defaultParam(sampleRate, audioChannels)
        val summary = getString(R.string.pref_output_format_desc)
        val prefix = when (val info = format.paramInfo(sampleRate, audioChannels)) {
            is RangedParamInfo -> "${info.format(formatParam)}, "
            NoParamInfo -> ""
        }

        prefOutputFormat.summary =
            "${summary}\n\n" +
                    "${audioSource.displayName(context)}, ${audioChannels.displayName(context)}\n" +
                    "${format.name} (${prefix}${sampleRate})"
    }

    private fun refreshVersion() {
        val suffix = if (prefs.isDebugMode) {
            "+debugmode"
        } else {
            ""
        }
        prefVersion.summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE}${suffix})"
    }

    private fun refreshPermissionsState() {
        prefPermissions.summary = getString(
            if (Permissions.haveRequired(requireContext())) {
                R.string.pref_permissions_granted_desc
            } else {
                R.string.pref_permissions_missing_desc
            }
        )
    }

    private fun refreshInhibitBatteryOptState() {
        val inhibiting = Permissions.isInhibitingBatteryOpt(requireContext())
        prefInhibitBatteryOpt.isChecked = inhibiting
        prefInhibitBatteryOpt.isEnabled = !inhibiting
    }

    private fun refreshDebugMode() {
        prefDebugMode.isChecked = prefs.isDebugMode
        prefDebugMode.isEnabled = !BuildConfig.FORCE_DEBUG_MODE
        prefDebugMode.summary = getString(
            if (BuildConfig.FORCE_DEBUG_MODE) {
                R.string.pref_debug_mode_forced_desc
            } else {
                R.string.pref_debug_mode_desc
            }
        )
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        when (preference) {
            // This is only reachable if battery optimization is not already inhibited
            prefInhibitBatteryOpt -> {
                if (newValue == true) {
                    requestInhibitBatteryOpt.launch(
                        Permissions.getInhibitBatteryOptIntent(requireContext())
                    )
                    return false
                }

                return true
            }

            prefDebugMode -> {
                prefs.isDebugMode = newValue as Boolean
                refreshDebugMode()
                refreshVersion()
                return false
            }
        }

        return false
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        when (preference) {
            prefPermissions -> {
                if (!Permissions.haveRequired(requireContext())) {
                    requestPermissionRequired.launch(Permissions.REQUIRED)
                }
                return true
            }

            prefOutputDir -> {
                OutputDirectoryBottomSheetFragment().show(
                    childFragmentManager, OutputDirectoryBottomSheetFragment.TAG
                )
                return true
            }

            prefOutputFormat -> {
                startActivity(Intent(requireContext(), OutputFormatActivity::class.java))
                return true
            }

            prefNativeSampleRate -> {
                startActivity(Intent(requireContext(), NativeSampleRateActivity::class.java))
                return true
            }

            prefVersion -> {
                // val uri = Uri.parse(BuildConfig.PROJECT_URL_AT_COMMIT)
                // startActivity(Intent(Intent.ACTION_VIEW, uri))
                return true
            }
        }

        return false
    }

    override fun onPreferenceChanged(key: String) {
        activity?.runOnUiThread {
            if (!isAdded) {
                return@runOnUiThread
            }

            refreshForPreferenceChange(key)
        }
    }

    private fun refreshForPreferenceChange(key: String) {
        when {
            // Update the output directory state when it's changed by the bottom sheet
            key == Preferences.PREF_OUTPUT_DIR || key == Preferences.PREF_OUTPUT_RETENTION -> {
                refreshOutputDir()
            }
            // Update the output format state when it's changed by the bottom sheet
            Preferences.isRecordingSettingsKey(key) -> {
                refreshOutputFormat()
            }

            key == Preferences.PREF_DEBUG_MODE -> {
                refreshDebugMode()
                refreshVersion()
            }
        }
    }
}
