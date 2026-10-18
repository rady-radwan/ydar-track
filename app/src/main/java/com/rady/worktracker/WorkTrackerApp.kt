package com.rady.worktracker

import android.app.Application
import com.rady.worktracker.data.AppSettings
import com.rady.worktracker.data.Repo
import com.rady.worktracker.timer.TimerSync

class WorkTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.rady.worktracker.data.AndroidInit.init(this)
        TimerSync.ensureChannels(this)
    }
}
