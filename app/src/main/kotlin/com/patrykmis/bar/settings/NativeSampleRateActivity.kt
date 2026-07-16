/*
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.patrykmis.bar.R

class NativeSampleRateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, NativeSampleRateFragment())
                .commit()
        }

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setTitle(R.string.pref_native_sample_rate_name)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
