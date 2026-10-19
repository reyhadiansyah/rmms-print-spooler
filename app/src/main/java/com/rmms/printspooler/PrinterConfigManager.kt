package com.rmms.printspooler

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "printer_settings")

data class PrinterConfig(
    val printerName: String = "Belum dipilih",
    val printerMac: String = "",
    val paperType: String = "LABEL_TSPL",
    val paperWidthMm: Int = 72,
    val labelHeightMm: Int = 40,
    val labelGapMm: Int = 2,
    val appLanguage: String = "id"
)

class PrinterConfigManager(private val context: Context) {
    companion object {
        val PRINTER_NAME = stringPreferencesKey("printer_name")
        val PRINTER_MAC = stringPreferencesKey("printer_mac")
        val PAPER_TYPE = stringPreferencesKey("paper_type")
        val PAPER_WIDTH = intPreferencesKey("paper_width")
        val LABEL_HEIGHT = intPreferencesKey("label_height")
        val LABEL_GAP = intPreferencesKey("label_gap")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
    }

    val configFlow: Flow<PrinterConfig> = context.dataStore.data.map { prefs ->
        PrinterConfig(
            printerName = prefs[PRINTER_NAME] ?: "Belum dipilih",
            printerMac = prefs[PRINTER_MAC] ?: "",
            paperType = prefs[PAPER_TYPE] ?: "LABEL_TSPL",
            paperWidthMm = prefs[PAPER_WIDTH] ?: 72,
            labelHeightMm = prefs[LABEL_HEIGHT] ?: 40,
            labelGapMm = prefs[LABEL_GAP] ?: 2,
            appLanguage = prefs[APP_LANGUAGE] ?: "id"
        )
    }

    suspend fun saveConfig(config: PrinterConfig) {
        context.dataStore.edit { prefs ->
            prefs[PRINTER_NAME] = config.printerName
            prefs[PRINTER_MAC] = config.printerMac
            prefs[PAPER_TYPE] = config.paperType
            prefs[PAPER_WIDTH] = config.paperWidthMm
            prefs[LABEL_HEIGHT] = config.labelHeightMm
            prefs[LABEL_GAP] = config.labelGapMm
            prefs[APP_LANGUAGE] = config.appLanguage
        }
    }
}