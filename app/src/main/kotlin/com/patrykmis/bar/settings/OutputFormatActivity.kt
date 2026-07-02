package com.patrykmis.bar.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.patrykmis.bar.R

class OutputFormatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

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
