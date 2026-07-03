package com.patrykmis.bar

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.accessibility.AccessibilityChecks
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.patrykmis.bar.settings.SettingsActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAccessibilityTest {
    @Test
    fun settingsScreenPassesAccessibilityChecks() {
        val scenario = ActivityScenario.launch(SettingsActivity::class.java)

        try {
            onView(isRoot()).check(AccessibilityChecks.accessibilityAssertion())
        } finally {
            scenario.close()
        }
    }
}
