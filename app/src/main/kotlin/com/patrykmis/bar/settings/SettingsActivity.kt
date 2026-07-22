/*
 * SPDX-FileCopyrightText: 2022-2026 Andrew Gunnerson
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.settings

import com.patrykmis.bar.R

class SettingsActivity : SettingsBaseActivity() {
    override val titleRes = R.string.app_name_full
    override val showUpButton = false

    override fun createFragment() = SettingsFragment()
}
