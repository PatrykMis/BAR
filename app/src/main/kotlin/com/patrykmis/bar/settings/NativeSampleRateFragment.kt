/*
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceViewHolder
import com.patrykmis.bar.R
import com.patrykmis.bar.RecorderService
import com.patrykmis.bar.audio.NativeSampleRateDetector

class NativeSampleRateFragment : PreferenceFragmentCompat() {
    private lateinit var prefResult: ResultPreference
    private lateinit var prefRunTest: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        screen.addPreference(Preference(context).apply {
            isIconSpaceReserved = false
            isSelectable = false
            setTitle(R.string.native_sample_rate_about_title)
            setSummary(R.string.native_sample_rate_about_desc)
        })

        prefResult = ResultPreference(context).apply {
            isIconSpaceReserved = false
            isSelectable = false
            setTitle(R.string.native_sample_rate_result_title)
            setSummary(R.string.native_sample_rate_result_not_tested)
        }
        screen.addPreference(prefResult)

        prefRunTest = Preference(context).apply {
            isIconSpaceReserved = false
            setTitle(R.string.native_sample_rate_run_test)
            setOnPreferenceClickListener {
                if (RecorderService.isRecording) {
                    refreshRecordingState()
                    return@setOnPreferenceClickListener true
                }

                runTest()
                true
            }
        }
        screen.addPreference(prefRunTest)

        preferenceScreen = screen
        refreshRecordingState()
    }

    override fun onResume() {
        super.onResume()

        refreshRecordingState()
    }

    private fun runTest() {
        val result = NativeSampleRateDetector.detect()
        val logcatTag = NativeSampleRateDetector::class.java.simpleName

        val summary = if (result.fallbackUsed) {
            getString(
                R.string.native_sample_rate_result_fallback,
                result.detectedSampleRate,
                logcatTag,
                result.durationMs,
            )
        } else {
            getString(
                R.string.native_sample_rate_result_detected,
                result.detectedSampleRate,
                checkNotNull(result.selectedChannelName),
                logcatTag,
                result.durationMs,
            )
        }
        prefResult.setAnnouncedSummary(summary)
    }

    private fun refreshRecordingState() {
        if (!this::prefRunTest.isInitialized) {
            return
        }

        val isRecording = RecorderService.isRecording

        prefRunTest.isEnabled = !isRecording
        prefRunTest.setSummary(
            if (isRecording) {
                R.string.pref_native_sample_rate_recording_disabled_desc
            } else {
                R.string.native_sample_rate_run_test_desc
            }
        )
    }

    private class ResultPreference(context: Context) : Preference(context) {
        private var announceSummaryChanges = false

        fun setAnnouncedSummary(summary: CharSequence) {
            announceSummaryChanges = true
            this.summary = summary
        }

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            val summaryView = holder.findViewById(android.R.id.summary) as? TextView
            summaryView?.accessibilityLiveRegion =
                if (announceSummaryChanges) {
                    View.ACCESSIBILITY_LIVE_REGION_POLITE
                } else {
                    View.ACCESSIBILITY_LIVE_REGION_NONE
                }

            super.onBindViewHolder(holder)
        }
    }
}
