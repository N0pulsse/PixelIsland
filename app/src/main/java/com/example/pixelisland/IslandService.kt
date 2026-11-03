package com.example.pixelisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.pixelisland.ui.MainActivity
import com.example.pixelisland.ui.island.CutoutInfo
import com.example.pixelisland.ui.island.IslandHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Foreground service that owns the TYPE_APPLICATION_OVERLAY window with the Compose island. */
class IslandService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var overlay: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        addOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        scope.launch {
            IslandPrefs.settings.map { it.enabled }.distinctUntilChanged().collect { enabled ->
                if (!enabled) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        overlay?.let { runCatching { windowManager?.removeView(it) } }
        overlay = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- overlay

    private fun addOverlay() {
        // A window context gives correct display metrics and cutout insets for overlay windows.
        val windowContext = createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        val wm = windowContext.getSystemService(WindowManager::class.java)
        windowManager = wm
        val cutout = readCutout(wm, windowContext.resources.displayMetrics.density)

        val view = ComposeView(windowContext).apply {
            setViewTreeLifecycleOwner(this@IslandService)
            setViewTreeSavedStateRegistryOwner(this@IslandService)
            setContent { IslandHost(cutout) }
            // Tap outside while expanded -> collapse
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) IslandRepository.requestCollapse()
                false
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = "PixelIsland"
        }

        wm.addView(view, params)
        overlay = view
    }

    /**
     * Reads the real punch-hole bounds from the window insets. Falls back to a Pixel-10-like
     * geometry when the system reports no top cutout (the offset slider in the app fine-tunes it).
     */
    private fun readCutout(wm: WindowManager, density: Float): CutoutInfo {
        val rect = wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRectTop
        return if (rect != null && !rect.isEmpty) {
            CutoutInfo(
                width = (rect.width() / density).dp.coerceAtLeast(24.dp),
                height = (rect.height() / density).dp.coerceAtLeast(24.dp),
                top = (rect.top / density).dp,
            )
        } else {
            CutoutInfo(width = 28.dp, height = 28.dp, top = 12.dp)
        }
    }

    // ----------------------------------------------------------- notification

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Pixel Island", NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Pixel Island работает")
            .setContentText("Остров активен")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "island_service"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, IslandService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, IslandService::class.java))
        }
    }
}
