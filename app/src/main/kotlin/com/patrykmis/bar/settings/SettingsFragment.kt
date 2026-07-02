package com.patrykmis.bar.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.patrykmis.bar.BuildConfig
import com.patrykmis.bar.Permissions
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.R
import com.patrykmis.bar.extension.formattedString
import com.patrykmis.bar.format.Format
import com.patrykmis.bar.format.NoParamInfo
import com.patrykmis.bar.format.RangedParamInfo
import com.patrykmis.bar.format.SampleRate
import com.patrykmis.bar.output.Retention
import com.patrykmis.bar.view.LongClickablePreference

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener,
    Preference.OnPreferenceClickListener, LongClickablePreference.OnPreferenceLongClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: Preferences
    private lateinit var prefPermissions: Preference
    private lateinit var prefOutputDir: Preference
    private lateinit var prefOutputFormat: Preference
    private lateinit var prefInhibitBatteryOpt: SwitchPreferenceCompat
    private lateinit var prefVersion: LongClickablePreference

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

        prefVersion = findPreference(Preferences.PREF_VERSION)!!
        prefVersion.onPreferenceClickListener = this
        prefVersion.onPreferenceLongClickListener = this
        refreshVersion()
    }

    override fun onStart() {
        super.onStart()

        preferenceScreen.sharedPreferences!!.registerOnSharedPreferenceChangeListener(this)

        // Changing permissions or battery state does not cause a reload of the activity.
        refreshPermissionsState()
        refreshInhibitBatteryOptState()
    }

    override fun onStop() {
        super.onStop()

        preferenceScreen.sharedPreferences!!.unregisterOnSharedPreferenceChangeListener(this)
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
        val (format, formatParamSaved) = Format.fromPreferences(prefs)
        val formatParam = formatParamSaved ?: format.paramInfo.default
        val summary = getString(R.string.pref_output_format_desc)
        val prefix = when (val info = format.paramInfo) {
            is RangedParamInfo -> "${info.format(formatParam)}, "
            NoParamInfo -> ""
        }
        val sampleRate = SampleRate.fromPreferences(prefs)

        prefOutputFormat.summary = "${summary}\n\n${format.name} (${prefix}${sampleRate})"
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

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        if (newValue == false) {
            return true
        }

        when (preference) {
            // This is only reachable if battery optimization is not already inhibited
            prefInhibitBatteryOpt -> requestInhibitBatteryOpt.launch(
                Permissions.getInhibitBatteryOptIntent(requireContext())
            )
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
                OutputFormatBottomSheetFragment().show(
                    childFragmentManager, OutputFormatBottomSheetFragment.TAG
                )
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

    override fun onPreferenceLongClick(preference: Preference): Boolean {
        when (preference) {
            prefVersion -> {
                prefs.isDebugMode = !prefs.isDebugMode
                refreshVersion()
                return true
            }
        }

        return false
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        when {
            key == null -> return
            // Update the output directory state when it's changed by the bottom sheet
            key == Preferences.PREF_OUTPUT_DIR || key == Preferences.PREF_OUTPUT_RETENTION -> {
                refreshOutputDir()
            }
            // Update the output format state when it's changed by the bottom sheet
            Preferences.isFormatKey(key) || key == Preferences.PREF_SAMPLE_RATE -> {
                refreshOutputFormat()
            }
        }
    }
}
