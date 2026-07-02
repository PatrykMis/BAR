package com.patrykmis.bar.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.patrykmis.bar.R
import com.patrykmis.bar.audio.NativeSampleRateDetector

class NativeSampleRateFragment : PreferenceFragmentCompat() {
    private lateinit var prefResult: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        screen.addPreference(Preference(context).apply {
            isIconSpaceReserved = false
            isSelectable = false
            setTitle(R.string.native_sample_rate_about_title)
            setSummary(R.string.native_sample_rate_about_desc)
        })

        prefResult = Preference(context).apply {
            isIconSpaceReserved = false
            isSelectable = false
            setTitle(R.string.native_sample_rate_result_title)
            setSummary(R.string.native_sample_rate_result_not_tested)
        }
        screen.addPreference(prefResult)

        screen.addPreference(Preference(context).apply {
            isIconSpaceReserved = false
            setTitle(R.string.native_sample_rate_run_test)
            setSummary(R.string.native_sample_rate_run_test_desc)
            setOnPreferenceClickListener {
                runTest()
                true
            }
        })

        preferenceScreen = screen
    }

    private fun runTest() {
        val result = NativeSampleRateDetector.detect()
        val logcatTag = NativeSampleRateDetector::class.java.simpleName

        prefResult.summary = if (result.fallbackUsed) {
            getString(
                R.string.native_sample_rate_result_fallback,
                result.detectedSampleRate,
                logcatTag,
            )
        } else {
            getString(
                R.string.native_sample_rate_result_detected,
                result.detectedSampleRate,
                checkNotNull(result.selectedChannelName),
                logcatTag,
            )
        }
    }
}
