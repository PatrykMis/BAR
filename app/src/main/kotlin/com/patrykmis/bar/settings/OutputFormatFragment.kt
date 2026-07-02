package com.patrykmis.bar.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.R
import com.patrykmis.bar.audio.AudioChannels
import com.patrykmis.bar.audio.AudioInputSource
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
        val currentAudioSource = AudioInputSource.fromPreferences(context, prefs)
        val currentAudioChannels = AudioChannels.fromPreferences(prefs, currentSampleRate)

        val audioSources = AudioInputSource.available()
        screen.addPreference(ListPreference(context).apply {
            isIconSpaceReserved = false
            isPersistent = false
            key = "audio_source"
            setTitle(R.string.output_format_audio_source)
            entries = audioSources.map { it.displayName(context) }.toTypedArray()
            entryValues = audioSources.map { it.preferenceValue }.toTypedArray()
            value = currentAudioSource.preferenceValue
            summary = getAudioSourceSummary(currentAudioSource)
            setOnPreferenceChangeListener { _, newValue ->
                val audioSource = AudioInputSource.getByPreferenceValue(newValue as String)!!
                prefs.audioInputSource = audioSource
                refreshScreen()

                if (audioSource.needsUnsupportedWarning(context)) {
                    showUnsupportedUnprocessedDialog()
                }

                false
            }
        })

        val audioChannels = AudioChannels.available(currentSampleRate)
        screen.addPreference(ListPreference(context).apply {
            isIconSpaceReserved = false
            isPersistent = false
            key = "audio_channels"
            setTitle(R.string.output_format_channels)
            entries = audioChannels.map { it.displayName(context) }.toTypedArray()
            entryValues = audioChannels.map { it.preferenceValue }.toTypedArray()
            value = currentAudioChannels.preferenceValue
            summary = currentAudioChannels.displayName(context)
            setOnPreferenceChangeListener { _, newValue ->
                prefs.audioChannels =
                    AudioChannels.getByPreferenceValue(newValue as String)
                refreshScreen()
                false
            }
        })

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
            is RangedParamInfo -> addParamPreference(
                screen,
                currentFormat,
                info,
                currentParam,
                currentAudioChannels,
            )

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
                prefs.resetRecordingSettings()
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
        audioChannels: AudioChannels,
    ) {
        val context = preferenceManager.context
        val currentValue = currentParam ?: format.defaultParam(audioChannels)

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

    private fun getAudioSourceSummary(audioSource: AudioInputSource): String =
        if (audioSource.needsUnsupportedWarning(requireContext())) {
            getString(
                R.string.audio_source_unsupported_unprocessed_summary,
                audioSource.displayName(requireContext())
            )
        } else {
            audioSource.displayName(requireContext())
        }

    private fun showUnsupportedUnprocessedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.audio_source_unsupported_unprocessed_title)
            .setMessage(R.string.audio_source_unsupported_unprocessed_message)
            .setNeutralButton(android.R.string.ok, null)
            .show()
    }
}
