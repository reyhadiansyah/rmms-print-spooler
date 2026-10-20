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
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val configManager = PrinterConfigManager(this)
        val printEngine = ThermalPrintEngine(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .imePadding()
                ) {
                    MainScreen(configManager, printEngine)
                }
            }
        }
    }
}

@Composable
fun MainScreen(configManager: PrinterConfigManager, printEngine: ThermalPrintEngine) {
    val state by configManager.stateFlow.collectAsState(initial = AppStateData())
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val isId = state.language == "id"

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isId) "Konfigurasi Printer Thermal" else "Thermal Printer Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        configManager.saveState(state.copy(language = if (isId) "en" else "id"))
                    }
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(if (isId) "EN" else "ID")
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(if (isId) "Profil & Koneksi" else "Profiles & Printer") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(if (isId) "Template Label" else "Label Templates") }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                ProfileConfigTab(state, configManager, printEngine, isId)
            } else {
                TemplateBuilderTab(state, configManager, isId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun ProfileConfigTab(
    state: AppStateData,
    configManager: PrinterConfigManager,
    printEngine: ThermalPrintEngine,
    isId: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val activeProfile = state.profiles.find { it.id == state.activeProfileId } ?: state.profiles.first()

    var profileName by remember(activeProfile) { mutableStateOf(activeProfile.profileName) }
    var printerName by remember(activeProfile) { mutableStateOf(activeProfile.printerName) }
    var printerMac by remember(activeProfile) { mutableStateOf(activeProfile.printerMac) }
    var printMode by remember(activeProfile) { mutableStateOf(activeProfile.printMode) }
    var paperWidth by remember(activeProfile) { mutableStateOf(activeProfile.paperWidthMm.toString()) }
    var labelHeight by remember(activeProfile) { mutableStateOf(activeProfile.labelHeightMm.toString()) }
    var labelGap by remember(activeProfile) { mutableStateOf(activeProfile.labelGapMm.toString()) }
    var selectedTemplateId by remember(activeProfile) { mutableStateOf(activeProfile.activeTemplateId) }

    var showProfileDropdown by remember { mutableStateOf(false) }
    var showTemplateDropdown by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            perms[Manifest.permission.BLUETOOTH] == true
        }
        if (btGranted) {
            pairedDevices = printEngine.getPairedPrinters()
            showDeviceDialog = true
        } else {
            Toast.makeText(context, "Izin Bluetooth diperlukan!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Text(
            text = if (isId) "Pilih Profil Konfigurasi Aktif" else "Select Active Configuration Profile",
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = showProfileDropdown,
                onExpandedChange = { showProfileDropdown = !showProfileDropdown },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = activeProfile.profileName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProfileDropdown) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = showProfileDropdown,
                    onDismissRequest = { showProfileDropdown = false }
                ) {
                    state.profiles.forEach { prof ->
                        DropdownMenuItem(
                            text = { Text(prof.profileName) },
                            onClick = {
                                showProfileDropdown = false
                                scope.launch {
                                    configManager.saveState(state.copy(activeProfileId = prof.id))
                                }
                            }
                        )
                    }
                }
            }

            FilledTonalButton(onClick = {
                val newProf = PrinterProfile(
                    id = UUID.randomUUID().toString(),
                    profileName = "Profil Baru ${state.profiles.size + 1}"
                )
                scope.launch {
                    configManager.saveState(
                        state.copy(
                            activeProfileId = newProf.id,
                            profiles = state.profiles + newProf
                        )
                    )
                }
            }) {
                Text("+ Baru")
            }

            if (state.profiles.size > 1) {
                TextButton(onClick =  {
                    val remaining = state.profiles.filterNot { it.id == activeProfile.id }
                    scope.launch {
                        configManager.saveState(
                            state.copy(
                                activeProfileId = remaining.first().id,
                                profiles = remaining
                            )
                        )
                    }
                }) {
                    Text(if (isId) "Hapus" else "Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        OutlinedTextField(
            value = profileName,
            onValueChange = { profileName = it },
            label = { Text(if (isId) "Nama Profil" else "Profile Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

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
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = printerName, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (printerMac.isEmpty()) "MAC: - " else "MAC: $printerMac",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = {
                    val perms = mutableListOf<String>()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                        perms.add(Manifest.permission.BLUETOOTH_SCAN)
                    } else {
                        perms.add(Manifest.permission.BLUETOOTH)
                        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    permissionLauncher.launch(perms.toTypedArray())
                }) {
                    Text(if (isId) "Pilih Printer" else "Select Printer")
                }
            }
        }

        // Opsi Kedua
        Text(
            text = if (isId) "2. Mode Bahasa & Jenis Kertas" else "2. Printer Language & Paper Mode",
            fontWeight = FontWeight.SemiBold
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { printMode = "ESCPOS_LABEL" }
            ) {
                RadioButton(
                    selected = printMode == "ESCPOS_LABEL",
                    onClick = { printMode = "ESCPOS_LABEL" }
                )
                Column {
                    Text(
                        text = if (isId) "Kertas Thermal Universal" else "Universal Thermal Paper",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isId) "Untuk printer dengan bahasa ESC/POS" else "For printer with ESC/POS language",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { printMode = "TSPL_LABEL" }
            ) {
                RadioButton(
                    selected = printMode == "TSPL_LABEL",
                    onClick = { printMode = "TSPL_LABEL" }
                )
                Column {
                    Text(
                        text = if (isId) "Kertas Thermal Label dengan Gap" else "Label Thermal Paper with Gap",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isId) "Untuk printer dengan bahasa TSPL" else "For printer with TSPL language",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Opsi Ketiga
        Text(
            text = if (isId) "3. Dimensi Kertas (mm)" else "3. Paper Dimensions (mm)",
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = paperWidth,
                onValueChange = { paperWidth = it },
                label = { Text(if (isId) "Lebar (mm)" else "Width (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            if (printMode == "TSPL_LABEL") {
                OutlinedTextField(
                    value = labelHeight,
                    onValueChange = { labelHeight = it },
                    label = { Text(if (isId) "Tinggi (mm)" else "Height (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = labelGap,
                    onValueChange = { labelGap = it },
                    label = { Text(if (isId) "Gap (mm)" else "Gap (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Opsi Keempat
        Text(
            text = if (isId) "4. Template Label Aktif" else "4. Active Label Template",
            fontWeight = FontWeight.SemiBold
        )

        val currentTemplate = state.templates.find { it.id == selectedTemplateId } ?: state.templates.first()
        ExposedDropdownMenuBox(
            expanded = showTemplateDropdown,
            onExpandedChange = { showTemplateDropdown = !showTemplateDropdown }
        ) {
            OutlinedTextField(
                value = currentTemplate.name,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showTemplateDropdown) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = showTemplateDropdown,
                onDismissRequest = { showTemplateDropdown = false }
            ) {
                state.templates.forEach { tpl ->
                    DropdownMenuItem(
                        text = { Text(tpl.name) },
                        onClick = {
                            selectedTemplateId = tpl.id
                            showTemplateDropdown = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Simpan & Test Print
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    val updatedProfile = activeProfile.copy(
                        profileName = profileName,
                        printerName = printerName,
                        printerMac = printerMac,
                        printMode = printMode,
                        paperWidthMm = paperWidth.toIntOrNull() ?: 58,
                        labelHeightMm = labelHeight.toIntOrNull() ?: 40,
                        labelGapMm = labelGap.toIntOrNull() ?: 2,
                        activeTemplateId = selectedTemplateId
                    )
                    val updatedList = state.profiles.map {
                        if (it.id == updatedProfile.id) updatedProfile else it
                    }
                    scope.launch {
                        configManager.saveState(state.copy(profiles = updatedList))
                        Toast.makeText(context, "Profil '${profileName}' disimpan!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isId) "Simpan Profil" else "Save Profile")
            }

            OutlinedButton(
                onClick = {
                    val testProfile = activeProfile.copy(
                        profileName = profileName,
                        printerName = printerName,
                        printerMac = printerMac,
                        printMode = printMode,
                        paperWidthMm = paperWidth.toIntOrNull() ?: 58,
                        labelHeightMm = labelHeight.toIntOrNull() ?: 40,
                        labelGapMm = labelGap.toIntOrNull() ?: 2,
                        activeTemplateId = selectedTemplateId
                    )
                    val sampleData = mapOf(
                        "title" to "SELF TEST",
                        "code" to "ABC-123",
                        "description" to "LOREM IPSUM",
                        "qty" to "100",
                        "lokasi" to "XY-0101",
                        "nama_barang" to "DOLOR SIT AMET",
                        "batch" to "Z-001",
                        "kode_barcode" to "123456789",
                    )
                    scope.launch {
                        val res = printEngine.printDynamicData(testProfile, currentTemplate, sampleData)
                        res.onSuccess { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isId) "Uji Cetak" else "Test Print")
            }
        }
    }

    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text(if (isId) "Pilih Printer Bluetooth" else "Select Bluetooth Printer") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (pairedDevices.isEmpty()) {
                        Text("Tidak ada perangkat Bluetooth yang di-pair di device ini.")
                    } else {
                        pairedDevices.forEach { device ->
                            ListItem(
                                headlineContent = { Text(device.name ?: "Unknown") },
                                supportingContent = { Text(device.address) },
                                modifier = Modifier.clickable {
                                    printerName = device.name ?: "Printer"
                                    printerMac = device.address
                                    showDeviceDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDeviceDialog = false }) { Text("Tututp") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateBuilderTab(
    state: AppStateData,
    configManager: PrinterConfigManager,
    isId: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var selectedTplId by remember { mutableStateOf(state.templates.first().id) }
    val currentTpl = state.templates.find { it.id == selectedTplId } ?: state.templates.first()

    var tplName by remember(currentTpl) { mutableStateOf(currentTpl.name) }
    var tplBody by remember(currentTpl) { mutableStateOf(currentTpl.bodyPattern) }
    var showDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isId) "Kelola Template Kertas" else "Manage Paper Template",
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = showDropdown,
                onExpandedChange = { showDropdown = !showDropdown },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = currentTpl.name,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false }
                ) {
                    state.templates.forEach { tpl ->
                        DropdownMenuItem(
                            text = { Text(tpl.name) },
                            onClick = {
                                selectedTplId = tpl.id
                                showDropdown = false
                            }
                        )
                    }
                }
            }

            FilledTonalButton(onClick = {
                val newTpl = LabelTemplate(
                    id = UUID.randomUUID().toString(),
                    name = "Template Baru ${state.templates.size + 1}",
                    bodyPattern = "[C][B]{judul}\\n[LINE]\\nItem: {nama}\\n[C][BARCODE]{kode}"
                )
                scope.launch {
                    configManager.saveState(state.copy(templates = state.templates + newTpl))
                    selectedTplId = newTpl.id
                }
            }) {
                Text("+ Baru")
            }

            if (state.templates.size > 1) {
                TextButton(onClick = {
                    val remaining = state.templates.filterNot { it.id == currentTpl.id }
                    scope.launch {
                        configManager.saveState(state.copy(templates = remaining))
                        selectedTplId = remaining.first().id
                    }
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        OutlinedTextField(
            value = tplName,
            onValueChange = { tplName = it },
            label = { Text(if (isId) "Nama Template" else "Template Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (isId) "Panduan Pembuatan Template:" else "Template Creation Guide:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("• Gunakan {nama_kolom} sesuai nama field JSON dari Power Apps", style = MaterialTheme.typography.bodySmall)
                Text("• [C] di awal baris adalah rata tengah", style = MaterialTheme.typography.bodySmall)
                Text("• [B] di awal baris adalah huruf tebal", style = MaterialTheme.typography.bodySmall)
                Text("• [LINE] adalah garis pemisah horizontal", style = MaterialTheme.typography.bodySmall)
                Text("• [BARCODE]{kode} adalah untuk mencetak gambar Barcode 128", style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { tplBody += "\n[C][B]{judul}" }, label = { Text("+ Judul") })
            AssistChip(onClick = { tplBody += "\n[LINE]" }, label = { Text("+ Garis") })
            AssistChip(onClick = { tplBody += "\n[C][BARCODE]{kode}" }, label = { Text("+ Barcode") })
        }

        OutlinedTextField(
            value = tplBody,
            onValueChange = { tplBody = it },
            label = { Text(if (isId) "Susunan Isi Label (Per Baris)" else "Label Layout Pattern") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 320.dp)
        )

        Button(
            onClick = {
                val updatedTpl = currentTpl.copy(name = tplName, bodyPattern = tplBody)
                val updatedList = state.templates.map { if (it.id == updatedTpl.id) updatedTpl else it }
                scope.launch {
                    configManager.saveState(state.copy(templates = updatedList))
                    Toast.makeText(context, "Template '${tplName}' berhasil disimpan!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isId) "Simpan Desain Template" else "Save Template Design")
        }
    }
}