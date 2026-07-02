package com.patrykmis.bar.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.patrykmis.bar.OpenPersistentDocumentTree
import com.patrykmis.bar.Preferences
import com.patrykmis.bar.R
import com.patrykmis.bar.databinding.OutputDirectoryBottomSheetBinding
import com.patrykmis.bar.extension.formattedString
import com.patrykmis.bar.output.Retention

class OutputDirectoryBottomSheetFragment : BottomSheetDialogFragment(), Slider.OnChangeListener {
    private var _binding: OutputDirectoryBottomSheetBinding? = null
    private val binding
        get() = _binding!!

    private lateinit var prefs: Preferences

    private val requestSafOutputDir =
        registerForActivityResult(OpenPersistentDocumentTree()) { uri ->
            prefs.outputDir = uri
            refreshOutputDir()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = OutputDirectoryBottomSheetBinding.inflate(inflater, container, false)

        val context = requireContext()

        prefs = Preferences(context)

        binding.selectNewDir.setOnClickListener {
            requestSafOutputDir.launch(null)
        }

        binding.retentionSlider.valueFrom = 0f
        binding.retentionSlider.valueTo = (Retention.all.size - 1).toFloat()
        binding.retentionSlider.stepSize = 1f
        binding.retentionSlider.contentDescription =
            getString(R.string.output_dir_bottom_sheet_file_retention)
        binding.retentionSlider.setLabelFormatter {
            Retention.all[it.toInt()].toFormattedString(context)
        }
        binding.retentionSlider.addOnChangeListener(this)

        binding.reset.setOnClickListener {
            prefs.outputDir = null
            refreshOutputDir()
            prefs.outputRetention = null
            refreshOutputRetention()
        }

        refreshOutputDir()
        refreshOutputRetention()

        return binding.root
    }

    private fun refreshOutputDir() {
        val outputDirUri = prefs.outputDirOrDefault
        binding.outputDir.text = outputDirUri.formattedString
    }

    private fun refreshOutputRetention() {
        val retention = Retention.fromPreferences(prefs)
        binding.retentionSlider.value = Retention.all.indexOf(retention).toFloat()
        updateRetentionStateDescription(retention)
    }

    override fun onValueChange(slider: Slider, value: Float, fromUser: Boolean) {
        when (slider) {
            binding.retentionSlider -> {
                val retention = Retention.all[value.toInt()]
                updateRetentionStateDescription(retention)

                if (fromUser) {
                    prefs.outputRetention = retention
                }
            }
        }
    }

    private fun updateRetentionStateDescription(retention: Retention) {
        ViewCompat.setStateDescription(
            binding.retentionSlider,
            retention.toFormattedString(requireContext())
        )
    }

    companion object {
        val TAG: String = OutputDirectoryBottomSheetFragment::class.java.simpleName
    }
}
