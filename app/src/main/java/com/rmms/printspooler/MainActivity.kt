package com.rmms.printspooler

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configManager = PrinterConfigManager(this)
        val printEngine = ThermalPrintEngine(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConfigScreen(configManager, printEngine)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ConfigScreen(configManager: PrinterConfigManager, printEngine: ThermalPrintEngine) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedConfig by configManager.configFlow.collectAsState(initial = PrinterConfig())

    var selectedName by remember(savedConfig) { mutableStateOf(savedConfig.printerName) }
    var selectedMac by remember(savedConfig) { mutableStateOf(savedConfig.printerMac) }
    var paperType by remember(savedConfig) { mutableStateOf(savedConfig.paperType) }
    var paperWidth by remember(savedConfig) { mutableStateOf(savedConfig.paperWidthMm.toString()) }
    var labelHeight by remember(savedConfig) { mutableStateOf(savedConfig.labelHeightMm.toString()) }
    var labelGap by remember(savedConfig) { mutableStateOf(savedConfig.labelGapMm.toString()) }
    var lang by remember(savedConfig) { mutableStateOf(savedConfig.appLanguage) }

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var showDeviceDialog by remember { mutableStateOf(false) }

    val isId = lang == "id"

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            pairedDevices = printEngine.getPairedPrinters()
            showDeviceDialog = true
        } else {
            Toast.makeText(context, "Izin Bluetooth diperlukan!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isId) "Konfigurasi Printer Thermal" else "Thermal Printer Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            TextButton(onClick = { lang = if (isId) "en" else "id" }) {
                Text(if (isId) "Ganti ke Inggris" else "Change to Bahasa Indonesia")
            }
        }

        HorizontalDivider()

        // Opsi Pertama
        Text(
            text = if (isId) "1. Perangkat Printer (Bluetooth)" else "1. Printer Device (Bluetooth)",
            fontWeight = FontWeight.SemiBold
        )

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = selectedName, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (selectedMac.isEmpty()) "MAC: - " else "MAC: $selectedMac",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                    } else {
                        arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.ACCESS_FINE_LOCATION)
                    }

                    permissionLauncher.launch(perms)
                }) {
                    Text(if (isId) "Pilih Printer" else "Select Printer")
                }
            }
        }

        // Opsi Kedua
        Text(
            text = if (isId) "2. Jenis Kertas & Mode Cetak" else "2. Paper Type & Print Mode",
            fontWeight = FontWeight.SemiBold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = paperType == "LABEL_TSPL",
                onClick = { paperType = "LABEL_TSPL" }
            )
            Text(if (isId) "Kertas Label Stiker (TSPL)" else "Sticker Label Paper (TSPL)")
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(
                selected = paperType == "RECEIPT_ESCPOS",
                onClick = { paperType = "RECEIPT_ESCPOS" }
            )
            Text(if (isId) "Kertas Struk Biasa (ESC/POS)" else "Continuous Receipt (ESC/POS)")
        }

        // Opsi Ketiga
        Text(
            text = if (isId) "3. Dimensi Kertas (Milimeter)" else "3. Paper Dimensions (mm)",
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = paperWidth,
                onValueChange = { paperWidth = it },
                label = { Text(if (isId) "Lebar Cetak (mm)" else "Print Width (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            if (paperType == "LABEL_TSPL") {
                OutlinedTextField(
                    value = labelHeight,
                    onValueChange = { labelHeight = it },
                    label = { Text(if (isId) "Tinggi Label (mm)" else "Label Height (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = labelGap,
                    onValueChange = { labelGap = it },
                    label = { Text(if (isId) "Jarak Gap (mm)" else "Gap Size (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Simpan & Test Print
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val newConfig = PrinterConfig(
                        printerName = selectedName,
                        printerMac = selectedMac,
                        paperType = paperType,
                        paperWidthMm = paperWidth.toIntOrNull() ?: 72,
                        labelHeightMm = labelHeight.toIntOrNull() ?: 40,
                        labelGapMm = labelGap.toIntOrNull() ?: 2,
                        appLanguage = lang
                    )
                    scope.launch {
                        configManager.saveConfig(newConfig)
                        Toast.makeText(
                            context,
                            if (isId) "Konfigurasi berhasil disimpan!" else "Settings saved!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isId) "Simpan Konfigurasi" else "Save Settings")
            }

            OutlinedButton(
                onClick = {
                    val testConfig = PrinterConfig(
                        printerName = selectedName,
                        printerMac = selectedMac,
                        paperType = paperType,
                        paperWidthMm = paperWidth.toIntOrNull() ?: 72,
                        labelHeightMm = labelHeight.toIntOrNull() ?: 40,
                        labelGapMm = labelGap.toIntOrNull() ?: 2,
                        appLanguage = lang
                    )
                    val samplePayload = PrintPayload(
                        title = "TES CETAK",
                        code = "RMMS-14022",
                        description = "Status: Terhubung",
                        qty = "100"
                    )
                    scope.launch {
                        val res = printEngine.printData(testConfig, samplePayload)
                        res.onSuccess {
                            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                        }.onFailure {
                            Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isId) "Uji Cetak (Test Print)" else "Test Print")
            }
        }
    }

    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text(if (isId) "Pilih Printer Bluetooth" else "Select Bluetooth Printer") },
            text = {
                Column {
                    if (pairedDevices.isEmpty()) {
                        Text(
                            if (isId) "Tidak ada perangkat Bluetooth yang terhubung. Silakan pair printer di menu Bluetooth Tablet terlebih dahulu."
                            else "No paired Bluetooth devices found. Please pair your printer in Android Bluetooth settings first."
                        )
                    } else {
                        pairedDevices.forEach { device ->
                            ListItem(
                                headlineContent = { Text(device.name ?: "Unknown Device") },
                                supportingContent = { Text(device.address) },
                                modifier = Modifier.clickable {
                                    selectedName = device.name ?: "Generic Printer"
                                    selectedMac = device.address
                                    showDeviceDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceDialog = false }) {
                    Text(if (isId) "Tutup" else "Close" )
                }
            }
        )
    }
}