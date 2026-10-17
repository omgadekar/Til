package com.omglabs.til

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Re-arms the daily job after a reboot or app update, and repaints immediately
 * when the date or timezone shifts under us.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.isEnabled(context)) return

        val request = OneTimeWorkRequestBuilder<WallpaperWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "wallpaper_now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
