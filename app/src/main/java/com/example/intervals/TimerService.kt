package com.example.intervalrunner

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var toneGenerator: ToneGenerator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        toneGenerator = runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        }.getOrNull()
        tts = TextToSpeech(applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.US
        }
        TimerEngine.setAudio(
            beep = {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            },
            speak = { label ->
                if (ttsReady) tts?.speak(label, TextToSpeech.QUEUE_FLUSH, null, null)
            },
        )
        TimerEngine.onFinished = {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground immediately — required before returning from onStartCommand
        // for services started with startForegroundService().
        startForeground(NOTIFICATION_ID, buildNotification(TimerEngine.state.value))

        when (intent?.action) {
            ACTION_PAUSE_RESUME, ACTION_START -> TimerEngine.pauseResume()
            ACTION_SKIP -> TimerEngine.skipBlock()
            ACTION_STOP -> TimerEngine.stop()
        }

        if (collectJob == null) {
            collectJob = scope.launch {
                TimerEngine.state.collectLatest { snap ->
                    if (snap.finished) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val nm = getSystemService(NotificationManager::class.java)
                        nm?.notify(NOTIFICATION_ID, buildNotification(snap))
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        collectJob?.cancel()
        scope.cancel()
        TimerEngine.setAudio(null, null)
        TimerEngine.onFinished = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        toneGenerator?.release()
        toneGenerator = null
    }

    private fun buildNotification(snap: TimerSnapshot): Notification {
        val label = snap.currentInterval?.label ?: getString(R.string.app_name)
        val running = snap.running
        val endRealtimeMs = snap.endRealtimeMs

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(label)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent())

        if (running && endRealtimeMs != null) {
            val whenMs = System.currentTimeMillis() +
                    (endRealtimeMs - SystemClock.elapsedRealtime())
            builder
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(whenMs)
                .setContentText(progressText(snap))
        } else {
            builder
                .setUsesChronometer(false)
                .setContentText("Paused — ${formatRemaining(snap.secondsRemaining)} left")
        }

        builder.addAction(
            0,
            if (running) "Pause" else "Resume",
            servicePendingIntent(ACTION_PAUSE_RESUME, 1),
        )
        builder.addAction(0, "Skip block", servicePendingIntent(ACTION_SKIP, 2))
        builder.addAction(0, "Stop", servicePendingIntent(ACTION_STOP, 3))

        return builder.build()
    }

    private fun progressText(snap: TimerSnapshot): String {
        val plan = snap.plan ?: return ""
        val block = snap.currentBlock ?: return ""
        val parts = buildList {
            if (block.intervals.size > 1) {
                add("Interval ${snap.intervalIndex + 1}/${block.intervals.size}")
            }
            if (plan.blocks.size > 1) {
                add("Block ${snap.blockIndex + 1}/${plan.blocks.size}")
            }
        }
        return parts.joinToString("  ·  ")
    }

    private fun formatRemaining(secs: Int): String {
        if (secs >= 60) {
            val m = secs / 60
            val s = secs % 60
            return "${m}m ${s}s"
        }
        return "${secs}s"
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TimerService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getService(this, requestCode, intent, flags)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Interval timer",
            // DEFAULT (not LOW) so the notification surfaces on the lock screen —
            // Android 12+ folds silent notifications away there by default.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Shows the current interval and time remaining."
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        // Bump this string whenever the channel definition changes — Android caches
        // channels by ID and ignores edits to existing ones.
        private const val CHANNEL_ID = "com.example.intervalrunner.timer.v2"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.intervalrunner.action.START"
        const val ACTION_PAUSE_RESUME = "com.example.intervalrunner.action.PAUSE_RESUME"
        const val ACTION_SKIP = "com.example.intervalrunner.action.SKIP"
        const val ACTION_STOP = "com.example.intervalrunner.action.STOP"

        fun sendCommand(context: Context, action: String) {
            val intent = Intent(context, TimerService::class.java).setAction(action)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
