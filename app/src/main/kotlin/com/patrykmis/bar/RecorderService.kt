package com.patrykmis.bar

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.patrykmis.bar.extension.threadIdCompat
import com.patrykmis.bar.output.OutputFile

class RecorderService : Service(), RecorderThread.OnRecordingCompletedListener {

    companion object {
        private val TAG = RecorderService::class.java.simpleName

        val ACTION_TOGGLE = "${RecorderService::class.java.canonicalName}.TOGGLE"
        private val ACTION_PAUSE = "${RecorderService::class.java.canonicalName}.PAUSE"
        private val ACTION_RESUME = "${RecorderService::class.java.canonicalName}.RESUME"

        @Volatile
        var isRecording: Boolean = false
            private set
    }

    private lateinit var notifications: Notifications
    private val handler = Handler(Looper.getMainLooper())

    private var recorder: RecorderThread? = null

    override fun onCreate() {
        super.onCreate()

        notifications = Notifications(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Handle recorder control intents (toggle, pause, resume). */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> {
                if (recorder == null) startRecording() else requestStopRecording()
            }

            ACTION_PAUSE, ACTION_RESUME -> {
                recorder?.isPaused = intent.action == ACTION_PAUSE
                updateForegroundState()
            }

            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    private fun createPauseIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            1,
            Intent(this, RecorderService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE
        )

    private fun createResumeIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            2,
            Intent(this, RecorderService::class.java).setAction(ACTION_RESUME),
            PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * Start the [RecorderThread].
     *
     * If the required permissions aren't granted, then the service will stop.
     *
     * This function is idempotent.
     */
    private fun startRecording() {
        if (recorder != null) return

        recorder = try {
            RecorderThread(this, this)
        } catch (e: Exception) {
            notifyFailure(e.message, null)
            stopSelf()
            return
        }

        // Foreground MUST be started quickly after service start
        updateForegroundState()
        isRecording = true
        recorder!!.start()
    }

    /**
     * Request the cancellation of the [RecorderThread].
     *
     * The foreground notification stays alive until the [RecorderThread] exits and reports its
     * status. The thread may exit before this function is called if an error occurs during
     * recording.
     *
     * This function is idempotent.
     */
    private fun requestStopRecording() {
        recorder?.cancel()
    }

    private fun updateForegroundState() {
        if (recorder == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            if (recorder!!.isPaused) {
                startForeground(
                    1, notifications.createPersistentNotification(
                        R.string.notification_recording_mic_paused,
                        R.drawable.ic_launcher_quick_settings,
                        R.string.notification_action_resume,
                        createResumeIntent()
                    )
                )
            } else {
                startForeground(
                    1, notifications.createPersistentNotification(
                        R.string.notification_recording_mic_in_progress,
                        R.drawable.ic_launcher_quick_settings,
                        R.string.notification_action_pause,
                        createPauseIntent()
                    )
                )
            }
        }
    }

    private fun onThreadExited() {
        recorder = null
        isRecording = false

        // Recording finished - clean up foreground state and stop service
        updateForegroundState()
        stopSelf()
    }

    override fun onRecordingCompleted(thread: RecorderThread, file: OutputFile?) {
        Log.i(TAG, "Recording completed: ${thread.threadIdCompat}: ${file?.redacted}")
        handler.post {
            onThreadExited()

            // If the recording was initially paused and the user never resumed it, there's no
            // output file, so nothing needs to be shown.
            if (file != null) {
                notifySuccess(file)
            }
        }
    }

    override fun onRecordingFailed(thread: RecorderThread, errorMsg: String?, file: OutputFile?) {
        Log.w(TAG, "Recording failed: ${thread.threadIdCompat}: ${file?.redacted}")
        handler.post {
            onThreadExited()

            notifyFailure(errorMsg, file)
        }
    }

    private fun notifySuccess(file: OutputFile) {
        notifications.notifySuccess(
            R.string.notification_recording_mic_succeeded,
            R.drawable.ic_launcher_quick_settings,
            file,
        )
    }

    private fun notifyFailure(errorMsg: String?, file: OutputFile?) {
        notifications.notifyFailure(
            R.string.notification_recording_mic_failed,
            R.drawable.ic_launcher_quick_settings,
            errorMsg,
            file
        )
    }
}
