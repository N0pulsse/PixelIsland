package com.example.pixelisland

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import com.example.pixelisland.model.CallEvent
import com.example.pixelisland.model.MediaEvent
import com.example.pixelisland.model.NavigationEvent
import com.example.pixelisland.model.NotificationEvent
import com.example.pixelisland.model.TimerEvent
import com.example.pixelisland.model.ProgressEvent

/**
 * Feeds the island: media sessions, calls, Android 16 Live Updates (progress-style notifications)
 * and ordinary heads-up style notifications.
 */
class IslandNotificationListener : NotificationListenerService() {

    private lateinit var sessionManager: MediaSessionManager
    private val callbacks = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> syncSessions(list.orEmpty()) }

    override fun onListenerConnected() {
        sessionManager = getSystemService(MediaSessionManager::class.java)
        val me = ComponentName(this, IslandNotificationListener::class.java)
        runCatching {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, me)
            syncSessions(sessionManager.getActiveSessions(me))
        }
        runCatching { activeNotifications?.forEach(::handle) }
        startIslandIfNeeded()
    }

    override fun onListenerDisconnected() {
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        callbacks.values.forEach { (c, cb) -> c.unregisterCallback(cb) }
        callbacks.clear()
        IslandRepository.removeWhere { it is MediaEvent }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        IslandRepository.remove("n:${sbn.key}")
    }

    // ---------------------------------------------------------------- media

    private fun syncSessions(controllers: List<MediaController>) {
        val alive = controllers.map { it.sessionToken }.toSet()
        callbacks.keys.filter { it !in alive }.forEach { token ->
            callbacks.remove(token)?.let { (c, cb) ->
                c.unregisterCallback(cb)
                IslandRepository.remove("media:${c.packageName}")
            }
        }
        controllers.forEach { c ->
            if (c.sessionToken !in callbacks) {
                val cb = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia(c)
                    override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia(c)
                    override fun onSessionDestroyed() = IslandRepository.remove("media:${c.packageName}")
                }
                c.registerCallback(cb)
                callbacks[c.sessionToken] = c to cb
            }
            publishMedia(c)
        }
    }

    private fun publishMedia(c: MediaController) {
        val key = "media:${c.packageName}"
        val state = c.playbackState?.state
        val meta = c.metadata
        if (meta == null || (state != PlaybackState.STATE_PLAYING && state != PlaybackState.STATE_BUFFERING)) {
            IslandRepository.remove(key)
            return
        }
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        IslandRepository.post(
            MediaEvent(
                key = key,
                title = meta.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
                art = art,
                isPlaying = true,
                durationMs = meta.getLong(MediaMetadata.METADATA_KEY_DURATION),
                controller = c,
            )
        )
    }

    // -------------------------------------------------------- notifications

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = n.extras
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val key = "n:${sbn.key}"
        val appName = appLabel(sbn.packageName)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty()
        val icon = bitmap(n.getLargeIcon()) ?: bitmap(n.smallIcon)
        val ongoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0

        when {
            // Calls (CallStyle)
            n.category == Notification.CATEGORY_CALL || template.endsWith("CallStyle") -> {
                val type = extras.getInt(Notification.EXTRA_CALL_TYPE, Notification.CallStyle.CALL_TYPE_ONGOING)
                val incoming = type == Notification.CallStyle.CALL_TYPE_INCOMING
                val actions = n.actions.orEmpty()
                IslandRepository.post(
                    CallEvent(
                        key = key,
                        caller = title.ifBlank { appName },
                        ongoing = !incoming,
                        startedAtMillis = if (n.`when` > 0) n.`when` else System.currentTimeMillis(),
                        icon = icon,
                        primaryAction = actions.getOrNull(0)?.actionIntent,
                        answerAction = if (incoming) actions.getOrNull(1)?.actionIntent else null,
                    )
                )
            }

            // Media is handled through MediaSessionManager
            template.endsWith("MediaStyle") -> Unit

            // Turn-by-turn navigation
            n.category == Notification.CATEGORY_NAVIGATION -> {
                IslandRepository.post(
                    NavigationEvent(
                        key = key,
                        appName = appName,
                        distance = title,
                        instruction = text,
                        icon = icon,
                        contentIntent = n.contentIntent,
                    )
                )
            }

            // Timers / stopwatches (ongoing notification with a chronometer)
            ongoing && extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) &&
                !template.endsWith("ProgressStyle") -> {
                IslandRepository.post(
                    TimerEvent(
                        key = key,
                        appName = appName,
                        title = title.ifBlank { appName },
                        whenMillis = n.`when`,
                        countDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false),
                        icon = icon,
                        contentIntent = n.contentIntent,
                    )
                )
            }

            // Android 16 Live Updates / any determinate ongoing progress
            isProgressLike(template, extras, ongoing) -> {
                val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
                val cur = extras.getInt(Notification.EXTRA_PROGRESS, 0)
                val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
                val fraction = when {
                    indeterminate -> null
                    max > 0 -> (cur.toFloat() / max).coerceIn(0f, 1f)
                    else -> (cur / 100f).coerceIn(0f, 1f)   // ProgressStyle: fall back to percent
                }
                // Key used by Android 16 for the status-bar chip text (see Notification.Builder#setShortCriticalText)
                val chip = extras.getCharSequence("android.shortCriticalText")?.toString()
                IslandRepository.post(
                    ProgressEvent(
                        key = key,
                        appName = appName,
                        title = title.ifBlank { appName },
                        text = text,
                        progress = fraction,
                        chipText = chip,
                        icon = icon,
                        contentIntent = n.contentIntent,
                    )
                )
            }

            // Other ongoing things (VPN, charging, recording…) are not island material
            ongoing -> Unit

            // Regular notification: short-lived "heads-up" style card
            title.isNotBlank() || text.isNotBlank() -> {
                IslandRepository.post(
                    NotificationEvent(
                        key = key,
                        appName = appName,
                        title = title.ifBlank { appName },
                        text = text,
                        icon = icon,
                        contentIntent = n.contentIntent,
                        expiresAtMillis = System.currentTimeMillis() + 6_000,
                    )
                )
            }
        }
    }

    private fun isProgressLike(template: String, extras: android.os.Bundle, ongoing: Boolean): Boolean {
        if (template.endsWith("ProgressStyle")) return true
        if (!ongoing) return false
        return extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
    }

    // ---------------------------------------------------------------- utils

    private fun bitmap(icon: Icon?): Bitmap? =
        runCatching { icon?.loadDrawable(this)?.toBitmap(96, 96) }.getOrNull()

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun startIslandIfNeeded() {
        if (IslandPrefs.settings.value.enabled && Settings.canDrawOverlays(this as Context)) {
            runCatching { IslandService.start(this) }
        }
    }
}
