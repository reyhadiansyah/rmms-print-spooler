package com.rmms.printspooler

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrintReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data: Uri? = intent?.data
        if (data != null && data.scheme == "rmmsprintspooler" && data.host == "print") {
            val rawPayload = data.getQueryParameter("payload")
            if (rawPayload != null) {
                processPrintJob(rawPayload)
            } else {
                Toast.makeText(this, "Data cetak (payload) kosong!", Toast.LENGTH_LONG).show()
                finish()
            }
        } else {
            finish()
        }
    }

    private fun processPrintJob(jsonPayload: String) {
        val configManager = PrinterConfigManager(this)
        val printEngine = ThermalPrintEngine(this)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val config = configManager.configFlow.first()
                val payload = Gson().fromJson(jsonPayload, PrintPayload::class.java)

                Toast.makeText(
                    this@PrintReceiverActivity,
                    "Mencetak ke ${config.printerName}...",
                    Toast.LENGTH_SHORT
                ).show()

                val result = printEngine.printData(config, payload)
                result.onSuccess { msg ->
                    Toast.makeText(this@PrintReceiverActivity, msg, Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(this@PrintReceiverActivity, err.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@PrintReceiverActivity,
                    "Format JSON tidak valid: {e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finish()
            }
        }
    }
}