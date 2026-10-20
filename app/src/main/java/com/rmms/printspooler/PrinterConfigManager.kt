package com.rmms.printspooler

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.dataStore by preferencesDataStore(name = "printer_settings")

data class PrinterProfile(
    val id: String = UUID.randomUUID().toString(),
    val profileName: String = "Default",
    val printerName: String = "Belum dipilih",
    val printerMac: String = "",
    val printMode: String = "ESCPOS_RECEIPT",
    val paperWidthMm: Int = 58,
    val labelHeightMm: Int = 40,
    val labelGapMm: Int = 2,
    val activeTemplateId: String = "default_tpl"
)

data class LabelTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Template Standar",
    val bodyPattern: String = "[C][B]{title}\\n[LINE]\\nKode : {code}\\nKet  : {description}\\nQty  : {qty}\\n[C][BARCODE]{code}"
)

data class AppStateData(
    val language: String = "id",
    val activeProfileId: String = "default_prof",
    val profiles: List<PrinterProfile> = listOf(
        PrinterProfile(id = "default_prof", profileName = "Profil Standar")
    ),
    val templates: List<LabelTemplate> = listOf(
        LabelTemplate(
            id = "default_tpl",
            name = "Label Barang Standar",
            bodyPattern = "[C][B]{title}\\n[LINE]\\nKode : {code}\\nKet  : {description}\\nQty  : {qty}\\n[C][BARCODE]{code}"
        ),
        LabelTemplate(
            id = "gudang_tpl",
            name = "Label Lokasi Rak",
            bodyPattern = "[B]LOKASI: {lokasi}\\n[LINE]\\nItem : {nama_barang}\\nBatch: {batch}\\n[C][BARCODE]{kode_barcode}"
        )
    )
)

class PrinterConfigManager(private val context: Context) {
    private val gson = Gson()

    companion object {
        val APP_STATE_JSON = stringPreferencesKey("app_state_json")
    }

    val stateFlow: Flow<AppStateData> = context.dataStore.data.map { prefs ->
        val json = prefs[APP_STATE_JSON]
        if (json.isNullOrEmpty()) {
            AppStateData()
        } else {
            try {
                gson.fromJson(json, AppStateData::class.java)
            } catch (e: Exception) {
                AppStateData()
            }
        }
    }

    suspend fun saveState(state: AppStateData) {
        context.dataStore.edit { prefs ->
            prefs[APP_STATE_JSON] = gson.toJson(state)
        }
    }
}