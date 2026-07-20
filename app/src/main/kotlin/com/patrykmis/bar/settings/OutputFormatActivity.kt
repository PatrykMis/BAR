/*
 * SPDX-FileCopyrightText: 2026 Andrew Gunnerson
 * SPDX-FileCopyrightText: 2026 Patryk Miś <foss@patrykmis.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.patrykmis.bar.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.patrykmis.bar.R

class OutputFormatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSettingsContentView()

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, OutputFormatFragment())
                .commit()
        }

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setTitle(R.string.pref_output_format_name)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
