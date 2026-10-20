package com.rmms.printspooler

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PrintReceiverActivity : Activity() {
    companion object {
        private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val data: Uri? = intent?.data
        val appContext = applicationContext

        if (data != null && data.scheme == "rmmsprintspooler" && data.host == "print") {
            val rawPayload = data.getQueryParameter("payload")
            val requestedProfile = data.getQueryParameter("profile")
            val requestedTemplate = data.getQueryParameter("template")

            if (!rawPayload.isNullOrEmpty()) {
                executeSilentPrint(appContext, rawPayload, requestedProfile, requestedTemplate)
            } else {
                Toast.makeText(appContext, "Data cetak dari Power Apps kosong!", Toast.LENGTH_LONG).show()
            }
        }

        finish()
        overridePendingTransition(0, 0)
    }

    private fun executeSilentPrint(
        appContext: android.content.Context,
        jsonPayload: String,
        requestedProfileName: String?,
        requestedTemplateName: String?
    ) {
        val configManager = PrinterConfigManager(appContext)
        val printEngine = ThermalPrintEngine(appContext)

        bgScope.launch {
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

                Toast.makeText(appContext, "Mencetak (${profile.printerName})...", Toast.LENGTH_SHORT).show()

                val result = printEngine.printDynamicData(profile, template, payloadMap)
                result.onSuccess { msg ->
                    Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(appContext, err.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(appContext, "Gagal memproses JSON: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}