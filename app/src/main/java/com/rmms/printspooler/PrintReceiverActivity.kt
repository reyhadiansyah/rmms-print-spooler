package com.rmms.printspooler

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat

class PrintReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data: Uri? = intent?.data
        if (data != null && data.scheme == "rmmsprintspooler" && data.host == "print") {
            val rawPayload = data.getQueryParameter("payload")
            val requestedProfile = data.getQueryParameter("profile")
            val requestedTemplate = data.getQueryParameter("template")
            val serviceIntent = Intent(this, PrintForegroundService::class.java).apply {
                putExtra("payload", rawPayload)
                putExtra("profile", requestedProfile)
                putExtra("template", requestedTemplate)
            }

            ContextCompat.startForegroundService(this, serviceIntent)
        }

        finish()
        overridePendingTransition(0, 0)
    }
}