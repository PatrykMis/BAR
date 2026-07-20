/*
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.settings

import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.patrykmis.bar.R

internal fun AppCompatActivity.setSettingsContentView() {
    enableEdgeToEdge()
    setContentView(R.layout.settings_activity)

    val root = findViewById<View>(R.id.settings_root)
    val toolbar = findViewById<View>(R.id.toolbar)
    val settings = findViewById<View>(R.id.settings)
    val toolbarHeight = toolbar.layoutParams.height
    val toolbarPadding = toolbar.padding()
    val settingsPadding = settings.padding()

    ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
        )

        toolbar.updateLayoutParams<ViewGroup.LayoutParams> {
            height = toolbarHeight + insets.top
        }
        toolbar.updatePadding(
            left = toolbarPadding.left + insets.left,
            top = toolbarPadding.top + insets.top,
            right = toolbarPadding.right + insets.right,
            bottom = toolbarPadding.bottom,
        )
        settings.updatePadding(
            left = settingsPadding.left + insets.left,
            top = settingsPadding.top,
            right = settingsPadding.right + insets.right,
            bottom = settingsPadding.bottom + insets.bottom,
        )

        windowInsets
    }
    ViewCompat.requestApplyInsets(root)
}

private fun View.padding(): ViewPadding =
    ViewPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)

private data class ViewPadding(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)
