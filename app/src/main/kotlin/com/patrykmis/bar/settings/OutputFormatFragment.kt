package com.patrykmis.bar.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.R
import com.patrykmis.bar.audio.AudioChannels
import com.patrykmis.bar.audio.AudioInputSource
import com.patrykmis.bar.audio.AudioSampleFormat
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
        val recordingSettings = Format.fromPreferences(prefs)
        val currentFormat = recordingSettings.format
        val currentParam = recordingSettings.formatParam
        val currentSampleRate = recordingSettings.sampleRate
        val currentAudioSource = AudioInputSource.fromPreferences(context, prefs)
        val currentAudioChannels = recordingSettings.audioChannels
        val currentSampleFormat = recordingSettings.sampleFormat
        val currentParamInfo = currentFormat.paramInfo(currentSampleRate, currentAudioChannels)

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

        val audioChannels =
            AudioChannels.available(currentFormat, currentSampleRate, currentSampleFormat)
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

        val sampleFormats = AudioSampleFormat.available(
            currentFormat,
            currentSampleRate,
            currentAudioChannels,
        )
        screen.addPreference(ListPreference(context).apply {
            isIconSpaceReserved = false
            isPersistent = false
            key = "audio_sample_format"
            setTitle(R.string.output_format_bit_depth)
            entries = sampleFormats.map { it.displayName(context) }.toTypedArray()
            entryValues = sampleFormats.map { it.preferenceValue }.toTypedArray()
            value = currentSampleFormat.preferenceValue
            summary = currentSampleFormat.displayName(context)
            setOnPreferenceChangeListener { _, newValue ->
                prefs.setFormatSampleFormat(
                    currentFormat,
                    AudioSampleFormat.getByPreferenceValue(newValue as String),
                )
                refreshScreen()
                false
            }
        })

        when (val info = currentParamInfo) {
            is RangedParamInfo -> addParamPreference(
                screen,
                currentFormat,
                currentSampleRate,
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
            val sampleRates = currentFormat.sampleRates
                .filter {
                    currentFormat.isSampleFormatSupported(
                        it,
                        currentAudioChannels,
                        currentSampleFormat,
                    )
                }
                .ifEmpty { listOf(currentSampleRate) }
            entries = sampleRates.map { it.toString() }.toTypedArray()
            entryValues = sampleRates.map { it.value.toString() }.toTypedArray()
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
        sampleRate: SampleRate,
        info: RangedParamInfo,
        currentParam: UInt?,
        audioChannels: AudioChannels,
    ) {
        val context = preferenceManager.context
        val currentValue = info.toNearest(
            currentParam ?: format.defaultParam(sampleRate, audioChannels)
        )
        val values = info.values

        val title = when (info.type) {
            RangedParamType.CompressionLevel -> R.string.output_format_bottom_sheet_compression_level
            RangedParamType.Bitrate -> R.string.output_format_bottom_sheet_bitrate
        }

        screen.addPreference(ListPreference(context).apply {
            isIconSpaceReserved = false
            isPersistent = false
            key = "format_param_${format.name}"
            setTitle(title)
            entries = values.map { info.format(it) }.toTypedArray()
            entryValues = values.map { it.toString() }.toTypedArray()
            value = currentValue.toString()
            summary = info.format(currentValue)

            setOnPreferenceChangeListener { preference, newValue ->
                val param = (newValue as String).toUInt()
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
