package com.example.pixelisland

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Restarts the island after a reboot or app update if the user left it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        IslandPrefs.init(context)
        if (IslandPrefs.settings.value.enabled && Settings.canDrawOverlays(context)) {
            runCatching { IslandService.start(context) }
        }
    }
}
