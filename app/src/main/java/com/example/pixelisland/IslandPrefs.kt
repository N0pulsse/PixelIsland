package com.example.pixelisland

import android.content.Context
import android.content.SharedPreferences
import com.example.pixelisland.model.CallEvent
import com.example.pixelisland.model.IslandEvent
import com.example.pixelisland.model.MediaEvent
import com.example.pixelisland.model.NavigationEvent
import com.example.pixelisland.model.NotificationEvent
import com.example.pixelisland.model.TimerEvent
import com.example.pixelisland.model.ProgressEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class IslandSettings(
    val enabled: Boolean = false,
    val showMedia: Boolean = true,
    val showCalls: Boolean = true,
    val showProgress: Boolean = true,
    val showNotifications: Boolean = true,
    val showNavigation: Boolean = true,
    val showTimers: Boolean = true,
    val autoExpand: Boolean = true,
    val scale: Float = 1f,
    /** Vertical fine-tune in dp, to line the pill up with the punch-hole exactly. */
    val topOffset: Float = 0f,
) {
    fun allows(event: IslandEvent): Boolean = when (event) {
        is MediaEvent -> showMedia
        is CallEvent -> showCalls
        is ProgressEvent -> showProgress
        is NavigationEvent -> showNavigation
        is TimerEvent -> showTimers
        is NotificationEvent -> showNotifications
    }
}

object IslandPrefs {
    private var sp: SharedPreferences? = null
    private val _settings = MutableStateFlow(IslandSettings())
    val settings: StateFlow<IslandSettings> = _settings.asStateFlow()

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("island", Context.MODE_PRIVATE)
        _settings.value = load()
    }

    fun update(block: (IslandSettings) -> IslandSettings) {
        _settings.update(block)
        save(_settings.value)
    }

    private fun load(): IslandSettings {
        val p = sp ?: return IslandSettings()
        val d = IslandSettings()
        return IslandSettings(
            enabled = p.getBoolean("enabled", d.enabled),
            showMedia = p.getBoolean("showMedia", d.showMedia),
            showCalls = p.getBoolean("showCalls", d.showCalls),
            showProgress = p.getBoolean("showProgress", d.showProgress),
            showNotifications = p.getBoolean("showNotifications", d.showNotifications),
            showNavigation = p.getBoolean("showNavigation", d.showNavigation),
            showTimers = p.getBoolean("showTimers", d.showTimers),
            autoExpand = p.getBoolean("autoExpand", d.autoExpand),
            scale = p.getFloat("scale", d.scale),
            topOffset = p.getFloat("topOffset", d.topOffset),
        )
    }

    private fun save(s: IslandSettings) {
        sp?.edit()
            ?.putBoolean("enabled", s.enabled)
            ?.putBoolean("showMedia", s.showMedia)
            ?.putBoolean("showCalls", s.showCalls)
            ?.putBoolean("showProgress", s.showProgress)
            ?.putBoolean("showNotifications", s.showNotifications)
            ?.putBoolean("showNavigation", s.showNavigation)
            ?.putBoolean("showTimers", s.showTimers)
            ?.putBoolean("autoExpand", s.autoExpand)
            ?.putFloat("scale", s.scale)
            ?.putFloat("topOffset", s.topOffset)
            ?.apply()
    }
}
