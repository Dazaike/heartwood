package dev.easonhuang.heartwood.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WidgetUpdateScheduler {
    private const val WORK_NAME = "heartwood_widget_refresh"

    fun schedule(context: Context, intervalMinutes: Long) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
