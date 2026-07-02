package com.patrykmis.bar.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
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
        val (currentFormat, currentParam, currentSampleRateSaved) = Format.fromPreferences(prefs)
        val currentSampleRate = currentSampleRateSaved ?: currentFormat.defaultSampleRate

        screen.addPreference(ListPreference(context).apply {
            isIconSpaceReserved = false
            isPersistent = false
            key = "output_format_format"
            setTitle(R.string.output_format_format)
            entries = Format.all.filter { it.supported }
                .map { it.name }
                .toTypedArray()
            entryValues = entries
            value = currentFormat.name
            summary = currentFormat.name
            setOnPreferenceChangeListener { _, newValue ->
                prefs.format = Format.getByName(newValue as String)!!
                refreshScreen()
                false
            }
        })

        when (val info = currentFormat.paramInfo) {
            is RangedParamInfo -> addParamPreference(screen, currentFormat, info, currentParam)
            NoParamInfo -> {}
        }

        screen.addPreference(ListPreference(context).apply {
            isIconSpaceReserved = false
            isPersistent = false
            key = "output_format_sample_rate"
            setTitle(R.string.output_format_bottom_sheet_sample_rate)
            entries = currentFormat.sampleRates.map { it.toString() }.toTypedArray()
            entryValues = currentFormat.sampleRates.map { it.value.toString() }.toTypedArray()
            value = currentSampleRate.value.toString()
            summary = currentSampleRate.toString()
            setOnPreferenceChangeListener { _, newValue ->
                prefs.setFormatSampleRate(currentFormat, SampleRate((newValue as String).toUInt()))
                refreshScreen()
                false
            }
        })

        screen.addPreference(Preference(context).apply {
            isIconSpaceReserved = false
            setTitle(R.string.bottom_sheet_reset)
            setOnPreferenceClickListener {
                prefs.resetAllFormats()
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
}
