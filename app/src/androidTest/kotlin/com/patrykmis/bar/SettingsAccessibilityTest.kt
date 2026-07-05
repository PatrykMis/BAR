package com.patrykmis.bar

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.patrykmis.bar.settings.NativeSampleRateActivity
import com.patrykmis.bar.settings.OutputFormatActivity
import com.patrykmis.bar.settings.SettingsActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAccessibilityTest {
    @Test
    fun settingsScreenPassesAccessibilityChecks() {
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)

        try {
            checkCurrentRootAccessibility()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun outputFormatScreenPassesAccessibilityChecks() {
        val scenario = ActivityScenario.launch(OutputFormatActivity::class.java)

        try {
            checkCurrentRootAccessibility()
        } finally {
            scenario.close()
        }
    }

    @Test
    fun audioSourceDialogPassesAccessibilityChecks() {
        checkOutputFormatDialogAccessibility(R.string.output_format_audio_source)
    }

    @Test
    fun audioChannelsDialogPassesAccessibilityChecks() {
        checkOutputFormatDialogAccessibility(R.string.output_format_channels)
    }

    @Test
    fun formatDialogPassesAccessibilityChecks() {
        checkOutputFormatDialogAccessibility(R.string.output_format_format)
    }

    @Test
    fun bitDepthDialogPassesAccessibilityChecks() {
        checkOutputFormatDialogAccessibility(R.string.output_format_bit_depth)
    }

    @Test
    fun formatParameterDialogPassesAccessibilityChecks() {
        checkOutputFormatDialogAccessibility(R.string.output_format_bottom_sheet_bitrate)
    }

    @Test
    fun sampleRateDialogPassesAccessibilityChecks() {
        checkOutputFormatDialogAccessibility(R.string.output_format_bottom_sheet_sample_rate)
    }

    @Test
    fun nativeSampleRateTestScreenPassesAccessibilityChecksAfterRunningTest() {
        val scenario = ActivityScenario.launch(NativeSampleRateActivity::class.java)

        try {
            onView(withText(R.string.native_sample_rate_run_test)).perform(click())
            checkCurrentRootAccessibility()
        } finally {
            scenario.close()
        }
    }

    private fun checkOutputFormatDialogAccessibility(preferenceTitleId: Int) {
        val scenario = ActivityScenario.launch(OutputFormatActivity::class.java)

        try {
            onView(withText(preferenceTitleId)).perform(click())
            checkCurrentDialogAccessibility()
        } finally {
            scenario.close()
        }
    }

    private fun checkCurrentRootAccessibility() {
        onView(isRoot()).check(AccessibilityChecks.accessibilityAssertion())
    }

    private fun checkCurrentDialogAccessibility() {
        onView(isRoot()).inRoot(isDialog()).check(AccessibilityChecks.accessibilityAssertion())
    }
}
