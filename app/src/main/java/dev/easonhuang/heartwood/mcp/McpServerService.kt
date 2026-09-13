package dev.easonhuang.heartwood.mcp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.easonhuang.heartwood.HeartwoodApp
import dev.easonhuang.heartwood.MainActivity
import dev.easonhuang.heartwood.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Foreground service hosting the opt-in, loopback-only local MCP server while enabled. */
class McpServerService : Service() {

    private var httpServer: McpJsonRpcServer? = null

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as HeartwoodApp
        CoroutineScope(Dispatchers.Default).launch {
            if (!app.mcpPrefs.enabled.first()) {
                stopSelf()
                return@launch
            }
            if (httpServer == null) {
                httpServer = McpJsonRpcServer(PORT, app.mcpPrefs.tokenOrCreate(), app::activeDataSource, app.goals)
                    .also { it.start() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        httpServer?.stop()
        httpServer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, "AI access", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Heartwood AI access is on")
            .setContentText("Local MCP server on 127.0.0.1:$PORT")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val PORT = 8787
        const val CHANNEL_ID = "mcp_server"
        private const val NOTIFICATION_ID = 1
    }
}
