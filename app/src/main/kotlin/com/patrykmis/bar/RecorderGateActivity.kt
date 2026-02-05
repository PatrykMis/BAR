package com.patrykmis.bar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

/**
 * Invisible trampoline Activity used to enter a foreground-eligible state
 * before starting the recording foreground service (microphone).
 *
 * Required for Android 14+ when starting mic FGS from Quick Settings tile.
 */
class RecorderGateActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the recorder service from a foreground-eligible context
        ContextCompat.startForegroundService(
            this,
            Intent(this, RecorderService::class.java).setAction(RecorderService.ACTION_TOGGLE)
        )

        // Immediately finish - no UI needed
        finish()
    }
}
