package com.patrykmis.bar.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.R
import com.patrykmis.bar.format.Format
import com.patrykmis.bar.format.NoParamInfo
import com.patrykmis.bar.format.RangedParamInfo
import com.patrykmis.bar.format.RangedParamType
import com.patrykmis.bar.format.SampleRate

class OutputFormatFragment : PreferenceFragmentCompat() {
    private lateinit var prefs: Preferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        prefs = Preferences(requireContext())
        refreshScreen()
    }

    private fun refreshScreen() {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        val (currentFormat, currentParam) = Format.fromPreferences(prefs)
        val currentSampleRate = SampleRate.fromPreferences(prefs)

        val formatCategory = PreferenceCategory(context).apply {
            isIconSpaceReserved = false
            setTitle(R.string.output_format_bottom_sheet_output_format)
        }
        screen.addPreference(formatCategory)

        for (format in Format.all) {
            if (!format.supported) {
                continue
            }

            formatCategory.addPreference(Preference(context).apply {
                isIconSpaceReserved = false
                title = format.name
                summary = if (format == currentFormat) {
                    getString(R.string.output_format_selected)
                } else {
                    null
                }
                setOnPreferenceClickListener {
                    prefs.format = format
                    checkSampleRateAndShowDialogIfNeeded(prefs.sampleRate)
                    refreshScreen()
                    true
                }
            })
        }

        when (val info = currentFormat.paramInfo) {
            is RangedParamInfo -> addParamPreference(screen, currentFormat, info, currentParam)
            NoParamInfo -> {}
        }

        val sampleRateCategory = PreferenceCategory(context).apply {
            isIconSpaceReserved = false
            setTitle(R.string.output_format_bottom_sheet_sample_rate)
        }
        screen.addPreference(sampleRateCategory)

        for (sampleRate in SampleRate.all) {
            sampleRateCategory.addPreference(Preference(context).apply {
                isIconSpaceReserved = false
                title = sampleRate.toString()
                summary = if (sampleRate == currentSampleRate) {
                    getString(R.string.output_format_selected)
                } else {
                    null
                }
                setOnPreferenceClickListener {
                    checkSampleRateAndShowDialogIfNeeded(sampleRate)
                    refreshScreen()
                    true
                }
            })
        }

        screen.addPreference(Preference(context).apply {
            isIconSpaceReserved = false
            setTitle(R.string.bottom_sheet_reset)
            setOnPreferenceClickListener {
                prefs.resetAllFormats()
                prefs.sampleRate = null
                refreshScreen()
                true
            }
        })

        preferenceScreen = screen
    }

    private fun addParamPreference(
        screen: androidx.preference.PreferenceScreen,
        format: Format,
        info: RangedParamInfo,
        currentParam: UInt?,
    ) {
        val context = preferenceManager.context
        val currentValue = currentParam ?: info.default

        val title = when (info.type) {
            RangedParamType.CompressionLevel -> R.string.output_format_bottom_sheet_compression_level
            RangedParamType.Bitrate -> R.string.output_format_bottom_sheet_bitrate
        }

        screen.addPreference(SeekBarPreference(context).apply {
            isIconSpaceReserved = false
            isPersistent = false
            key = "format_param_${format.name}"
            setTitle(title)
            min = info.range.first.toInt()
            max = info.range.last.toInt()
            seekBarIncrement = info.stepSize.toInt()
            value = currentValue.toInt()
            summary = info.format(currentValue)

            setOnPreferenceChangeListener { preference, newValue ->
                val param = (newValue as Int).toUInt()
                prefs.setFormatParam(format, param)
                preference.summary = info.format(param)
                true
            }
        })
    }

    private fun checkSampleRateAndShowDialogIfNeeded(sampleRate: SampleRate?) {
        val format = Format.fromPreferences(prefs).first

        if (format.name == "OGG/Opus" && sampleRate?.value == 44_100u) {
            prefs.sampleRate = SampleRate(48_000u)
            showUnsupportedSampleRateDialog()
        } else {
            prefs.sampleRate = sampleRate
        }
    }

    private fun showUnsupportedSampleRateDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.unsupported_sample_rate))
            .setNeutralButton(getString(android.R.string.ok)) { _, _ ->
                refreshScreen()
            }
            .show()
    }
}
