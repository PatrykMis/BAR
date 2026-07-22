/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.settings

import com.patrykmis.bar.R

class OutputFormatActivity : SettingsBaseActivity() {
    override val titleRes = R.string.pref_output_format_name

    override fun createFragment() = OutputFormatFragment()
}
