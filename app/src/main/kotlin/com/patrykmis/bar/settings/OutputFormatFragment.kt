package com.patrykmis.bar.settings

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.R
import com.patrykmis.bar.RecorderService
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

    override fun onResume() {
        super.onResume()

        refreshScreen()
    }

    private fun refreshScreen() {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        val isRecording = RecorderService.isRecording
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
                if (blockChangeIfRecording()) {
                    return@setOnPreferenceChangeListener false
                }

                val audioSource = AudioInputSource.getByPreferenceValue(newValue as String)!!
                prefs.audioInputSource = audioSource
                refreshScreen()

                if (audioSource.needsUnsupportedWarning(context) &&
                    !prefs.hideUnsupportedUnprocessedWarning
                ) {
                    showUnsupportedUnprocessedDialog()
                }

                false
            }
            disableIfRecording(isRecording)
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
                if (blockChangeIfRecording()) {
                    return@setOnPreferenceChangeListener false
                }

                prefs.audioChannels =
                    AudioChannels.getByPreferenceValue(newValue as String)
                refreshScreen()
                false
            }
            disableIfRecording(isRecording)
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
                if (blockChangeIfRecording()) {
                    return@setOnPreferenceChangeListener false
                }

                prefs.format = Format.getByName(newValue as String)!!
                refreshScreen()
                false
            }
            disableIfRecording(isRecording)
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
                if (blockChangeIfRecording()) {
                    return@setOnPreferenceChangeListener false
                }

                val sampleFormat = AudioSampleFormat.getByPreferenceValue(newValue as String)
                prefs.setFormatSampleFormat(
                    currentFormat,
                    sampleFormat,
                )
                refreshScreen()

                if (sampleFormat?.needsHigherBitDepthWarning == true &&
                    !prefs.hideHigherBitDepthWarning
                ) {
                    showHigherBitDepthDialog()
                }

                false
            }
            disableIfRecording(isRecording)
        })

        when (val info = currentParamInfo) {
            is RangedParamInfo -> addParamPreference(
                screen,
                currentFormat,
                currentSampleRate,
                info,
                currentParam,
                currentAudioChannels,
                isRecording,
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
                if (blockChangeIfRecording()) {
                    return@setOnPreferenceChangeListener false
                }

                prefs.setFormatSampleRate(currentFormat, SampleRate((newValue as String).toUInt()))
                refreshScreen()
                false
            }
            disableIfRecording(isRecording)
        })

        screen.addPreference(Preference(context).apply {
            isIconSpaceReserved = false
            setTitle(R.string.bottom_sheet_reset)
            setOnPreferenceClickListener {
                if (blockChangeIfRecording()) {
                    return@setOnPreferenceClickListener true
                }

                prefs.resetRecordingSettings()
                refreshScreen()
                true
            }
            disableIfRecording(isRecording)
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
        isRecording: Boolean,
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
                if (blockChangeIfRecording()) {
                    return@setOnPreferenceChangeListener false
                }

                val param = (newValue as String).toUInt()
                prefs.setFormatParam(format, param)
                preference.summary = info.format(param)
                true
            }
            disableIfRecording(isRecording)
        })
    }

    private fun Preference.disableIfRecording(isRecording: Boolean) {
        if (!isRecording) {
            return
        }

        val currentSummary = summary?.toString()?.takeIf { it.isNotBlank() }
        val recordingSummary = getString(R.string.pref_output_format_recording_disabled_desc)

        isEnabled = false
        summary = listOfNotNull(currentSummary, recordingSummary).joinToString("\n\n")
    }

    private fun blockChangeIfRecording(): Boolean {
        if (!RecorderService.isRecording) {
            return false
        }

        refreshScreen()
        return true
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
        showWarningDialog(
            R.string.audio_source_unsupported_unprocessed_title,
            R.string.audio_source_unsupported_unprocessed_message,
        ) {
            prefs.hideUnsupportedUnprocessedWarning = true
        }
    }

    private fun showHigherBitDepthDialog() {
        showWarningDialog(
            R.string.bit_depth_warning_title,
            R.string.bit_depth_warning_message,
        ) {
            prefs.hideHigherBitDepthWarning = true
        }
    }

    private fun showWarningDialog(
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
        onHideFutureMessages: () -> Unit,
    ) {
        val doNotShowAgain = MaterialCheckBox(requireContext()).apply {
            text = getString(R.string.dialog_do_not_show_again)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setView(doNotShowAgain)
            .setNeutralButton(android.R.string.ok) { _, _ ->
                if (doNotShowAgain.isChecked) {
                    onHideFutureMessages()
                }
            }
            .show()
    }
}
