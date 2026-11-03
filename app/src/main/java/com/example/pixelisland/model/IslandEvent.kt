package com.example.pixelisland.model

import android.app.PendingIntent
import android.graphics.Bitmap
import android.media.session.MediaController

/** Everything the island can show. Higher [priority] wins the compact slot. */
sealed interface IslandEvent {
    val key: String
    val priority: Int

    /** Wall-clock time after which the event is removed automatically; null = stays until removed. */
    val expiresAtMillis: Long? get() = null
}

data class MediaEvent(
    override val key: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val durationMs: Long,
    val controller: MediaController?,
) : IslandEvent {
    override val priority get() = 20
}

data class CallEvent(
    override val key: String,
    val caller: String,
    val ongoing: Boolean,
    val startedAtMillis: Long,
    val icon: Bitmap?,
    val primaryAction: PendingIntent?,   // decline (incoming) / hang up (ongoing)
    val answerAction: PendingIntent?,    // answer (incoming only)
) : IslandEvent {
    override val priority get() = if (ongoing) 60 else 100
}

/** Live Update / progress-style notification, timers, deliveries, rides… */
data class ProgressEvent(
    override val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val progress: Float?,       // 0..1, null = indeterminate
    val chipText: String?,      // short text for the compact trailing slot
    val icon: Bitmap?,
    val contentIntent: PendingIntent?,
) : IslandEvent {
    override val priority get() = 40
}

/** Turn-by-turn navigation (CATEGORY_NAVIGATION): distance chip + instruction. */
data class NavigationEvent(
    override val key: String,
    val appName: String,
    val distance: String,       // usually the notification title, e.g. "250 m"
    val instruction: String,    // e.g. "Turn left onto Main St"
    val icon: Bitmap?,
    val contentIntent: PendingIntent?,
) : IslandEvent {
    override val priority get() = 45
}

/** Chronometer-based ongoing notification: timers (count down) and stopwatches (count up). */
data class TimerEvent(
    override val key: String,
    val appName: String,
    val title: String,
    val whenMillis: Long,       // end time when [countDown], start time otherwise
    val countDown: Boolean,
    val icon: Bitmap?,
    val contentIntent: PendingIntent?,
) : IslandEvent {
    override val priority get() = 42
}

data class NotificationEvent(
    override val key: String,
    val appName: String,
    val title: String,
    val text: String,
    val icon: Bitmap?,
    val contentIntent: PendingIntent?,
    override val expiresAtMillis: Long?,
) : IslandEvent {
    override val priority get() = 50
}
