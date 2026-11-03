package com.example.pixelisland

import android.app.Application

class PixelIslandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        IslandPrefs.init(this)
    }
}
