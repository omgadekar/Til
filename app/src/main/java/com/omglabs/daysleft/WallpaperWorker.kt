package com.omglabs.daysleft

import android.app.WallpaperManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class WallpaperWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = try {
            apply(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
        // Always line up the next run, even if this one failed.
        schedule(applicationContext)
        return result
    }

    companion object {
        const val WORK_NAME = "daily_wallpaper"

        /** Paints today's number and sets it on home + lock screen. */
        fun apply(context: Context) {
            val (w, h) = WallpaperPainter.wallpaperSize(context)
            val settings = Prefs.settings(context)
            val bitmap = WallpaperPainter.render(
                context,
                w,
                h,
                WallpaperPainter.daysLeft(settings.targetDate),
                settings
            )
            val wm = WallpaperManager.getInstance(context)
            wm.setBitmap(
                bitmap,
                null,
                true,
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            )
            bitmap.recycle()
        }

        /**
         * One-shot work just after the next midnight. The worker re-arms itself,
         * which is far more reliable than a 24h periodic request that drifts.
         */
        fun schedule(context: Context) {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val nextRun = LocalDate.now(zone)
                .plusDays(1)
                .atTime(LocalTime.of(0, 1))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()

            val request = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInitialDelay(nextRun - now, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
