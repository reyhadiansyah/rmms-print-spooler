package com.rmms.printspooler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class PrintForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val CHANNEL_ID = "rmms_print_spooler_channel"
    private val NOTIFICATION_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Memproses perintah cetak dari Power Apps...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val rawPayload = intent?.getStringExtra("payload")
        val requestedProfile = intent?.getStringExtra("profile")
        val requestedTemplate = intent?.getStringExtra("template")

        if (!rawPayload.isNullOrEmpty()) {
            serviceScope.launch {
                processPrint(rawPayload, requestedProfile, requestedTemplate)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        } else {
            Toast.makeText(applicationContext, "Data cetak kosong!", Toast.LENGTH_LONG).show()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private suspend fun processPrint(
        jsonPayload: String,
        requestedProfileName: String?,
        requestedTemplateName: String?
    ) {
        val configManager = PrinterConfigManager(applicationContext)
        val printEngine = ThermalPrintEngine(applicationContext)

        try {
            val state = configManager.stateFlow.first()

            val profile = if (!requestedProfileName.isNullOrEmpty()) {
                state.profiles.find { it.profileName.equals(requestedProfileName, ignoreCase = true) }
                    ?: state.profiles.find { it.id == state.activeProfileId }
                    ?: state.profiles.first()
            } else {
                state.profiles.find { it.id == state.activeProfileId } ?: state.profiles.first()
            }

            val template = if (!requestedTemplateName.isNullOrEmpty()) {
                state.templates.find { it.name.equals(requestedTemplateName, ignoreCase = true) }
                    ?: state.templates.find { it.id == profile.activeTemplateId }
                    ?: state.templates.first()
            } else {
                state.templates.find { it.id == profile.activeTemplateId } ?: state.templates.first()
            }

            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val payloadMap: Map<String, Any> = Gson().fromJson(jsonPayload, mapType)

            Toast.makeText(
                applicationContext,
                "Mencetak ke ${profile.printerName}...",
                Toast.LENGTH_SHORT
            ).show()

            val result = printEngine.printDynamicData(profile, template, payloadMap)
            result.onSuccess { msg ->
                Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(applicationContext, err.message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                applicationContext,
                "Gagal memproses data cetak: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Layanan RMMS Print Spooler",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga koneksi Bluetooth printer tetap stabil saat mencetak"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RMMS Print Spooler")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}