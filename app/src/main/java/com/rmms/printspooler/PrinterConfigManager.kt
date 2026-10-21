package com.rmms.printspooler

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

val Context.dataStore by preferencesDataStore(name = "printer_settings")

data class PaperSizeProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Kertas Baru",
    val widthMm: Int = 70,
    val heightMm: Int = 50,
    val gapMm: Int = 2,
    val marginMm: Int = 3,
    val customMaxChars: Int = 0
)

data class PrinterProfile(
    val id: String = UUID.randomUUID().toString(),
    val profileName: String = "Profil Baru",
    val printerName: String = "Belum dipilih",
    val printerMac: String = "",
    val printMode: String = "TSPL_LABEL",
    val activePaperProfileId: String = "paper_70x50",
    val paperWidthMm: Int = 70,
    val labelHeightMm: Int = 50,
    val labelGapMm: Int = 2,
    val marginMm: Int = 3,
    val customMaxChars: Int = 0,
    val activeTemplateId: String = ""
)

data class LabelTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Template Baru",
    val bodyPattern: String = ""
)

data class AppStateData(
    val language: String = "id",
    val appTitle: String = "RMMS Print Spooler",
    val creatorDivision: String = "reyhadiansyah",
    val activeProfileId: String = "default_prof",
    val paperProfiles: List<PaperSizeProfile> = listOf(
        PaperSizeProfile(
            id = "paper_70x50",
            name = "Label 70x50mm (Gap 2mm)",
            widthMm = 70,
            heightMm = 50,
            gapMm = 2,
            marginMm = 3
        ),
        PaperSizeProfile(
            id = "paper_50x30",
            name = "Label 50x30mm (Gap 2mm)",
            widthMm = 50,
            heightMm = 30,
            gapMm = 2,
            marginMm = 2
        ),
        PaperSizeProfile(
            id = "paper_58_roll",
            name = "Continuous 58mm",
            widthMm = 58,
            heightMm = 40,
            gapMm = 0,
            marginMm = 3
        ),
        PaperSizeProfile(
            id = "paper_80_roll",
            name = "Continuous 80mm",
            widthMm = 80,
            heightMm = 50,
            gapMm = 0,
            marginMm = 4
        )
    ),
    val profiles: List<PrinterProfile> = listOf(
        PrinterProfile(
            id = "default_prof",
            profileName = "Profil Bawaan",
            activePaperProfileId = "paper_70x50",
            paperWidthMm = 70,
            labelHeightMm = 50,
            labelGapMm = 2
        )
    ),
    val templates: List<LabelTemplate> = listOf(
        LabelTemplate(
            id = "default_tpl",
            name = "Template Bawaan",
            bodyPattern = "Kota: {pwkl}\nMaterial: {material}\nDesc: {material_desc}\nQty: {quantity}"
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
                val parsed = gson.fromJson(json, AppStateData::class.java)
                if (parsed.paperProfiles.isNullOrEmpty()) {
                    parsed.copy(paperProfiles = AppStateData().paperProfiles)
                } else {
                    parsed
                }
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