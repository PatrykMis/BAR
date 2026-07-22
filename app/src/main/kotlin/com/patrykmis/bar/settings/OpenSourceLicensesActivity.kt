/*
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.settings

import com.patrykmis.bar.R

class OpenSourceLicensesActivity : SettingsBaseActivity() {
    override val titleRes = R.string.pref_open_source_licenses_name

    override fun createFragment() = OpenSourceLicensesFragment()
}
