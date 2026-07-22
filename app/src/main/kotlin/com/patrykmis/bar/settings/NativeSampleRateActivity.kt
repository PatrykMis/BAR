/*
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.settings

import com.patrykmis.bar.R

class NativeSampleRateActivity : SettingsBaseActivity() {
    override val titleRes = R.string.pref_native_sample_rate_name

    override fun createFragment() = NativeSampleRateFragment()
}
