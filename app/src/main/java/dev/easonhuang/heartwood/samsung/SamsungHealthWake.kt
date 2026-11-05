package dev.easonhuang.heartwood.samsung

import android.content.Context
import android.os.SystemClock
import com.samsung.android.sdk.health.data.HealthDataService
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SamsungHealthWake(private val context: Context) {
    private val mutex = Mutex()
    private var lastLaunchElapsed = 0L

    suspend fun wakeAndSettle() {
        mutex.withLock {
            val app = context.applicationContext
            val installed = runCatching {
                app.packageManager.getLaunchIntentForPackage(SHEALTH_PACKAGE)
            }.getOrNull()
            if (installed == null) return

            val sinceLast = SystemClock.elapsedRealtime() - lastLaunchElapsed
            if (lastLaunchElapsed != 0L && sinceLast < SETTLE_MS) {
                delay(SETTLE_MS - sinceLast)
                return
            }

            runCatching { HealthDataService.getStore(app) }
            lastLaunchElapsed = SystemClock.elapsedRealtime()
            delay(SETTLE_MS)
        }
    }

    companion object {
        const val SHEALTH_PACKAGE = "com.sec.android.app.shealth"
        const val SETTLE_MS = 5_000L
    }
}
