package com.rmms.printspooler

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

const val PATENT_APP_TITLE = "RMMS Print Spooler"
const val PATENT_CREATOR_NAME = "General Administration Raw Material Warehouse"

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
                    AppRootWithSplash(configManager, printEngine)
                }
            }
        }
    }
}

@Composable
fun AppRootWithSplash(configManager: PrinterConfigManager, printEngine: ThermalPrintEngine) {
    val context = LocalContext.current
    val state by configManager.stateFlow.collectAsState(initial = AppStateData())
    var showSplash by rememberSaveable { mutableStateOf(true) }

    val installedVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.3.1"
        } catch (e: Exception) {
            "1.3.1"
        }
    }

    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(3000)
            showSplash = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!showSplash) {
            MainScreen(
                state = state,
                installedVersion = installedVersion,
                configManager = configManager,
                printEngine = printEngine
            )
        }

        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(animationSpec = tween(400)),
            exit = fadeOut(animationSpec = tween(500)),
        ) {
            SplashScreenView(versionName = installedVersion)
        }
    }
}

@Composable
fun SplashScreenView(versionName: String) {
    val context = LocalContext.current
    val appIconBitmap = remember {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            val width = drawable.intrinsicWidth.coerceAtLeast(160)
            val height = drawable.intrinsicHeight.coerceAtLeast(160)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(28.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(104.dp),
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = "App Icon",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(text = "🖨️", fontSize = 42.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = PATENT_APP_TITLE,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "Versi $versionName",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Dikembangkan oleh:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = PATENT_CREATOR_NAME,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun MainScreen(
    state: AppStateData,
    installedVersion: String,
    configManager: PrinterConfigManager,
    printEngine: ThermalPrintEngine
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val isId = state.language == "id"

    // HEADER
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PATENT_APP_TITLE,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "v$installedVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
                text = { Text(if (isId) "Profil Cetak" else "Print Profile") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(if (isId) "Ukuran Kertas" else "Paper Size") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(if (isId) "Template Cetak" else "Print Template") }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ProfileConfigTab(state, configManager, printEngine, isId)
                1 -> PaperSizeProfileTab(state, configManager, isId)
                2 -> TemplateBuilderTab(state,configManager, isId)
            }
        }
    }
}

// TAB 1: PROFIL CETAK
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

    val safeProfiles = state.profiles.ifEmpty { AppStateData().profiles }
    val safePaperProfiles = state.paperProfiles.ifEmpty { AppStateData().paperProfiles }
    val safeTemplates = state.templates.ifEmpty { AppStateData().templates }

    val activeProfile = safeProfiles.find { it.id == state.activeProfileId } ?: safeProfiles.first()

    var profileName by remember(activeProfile) { mutableStateOf(activeProfile.profileName) }
    var printerName by remember(activeProfile) { mutableStateOf(activeProfile.printerName) }
    var printerMac by remember(activeProfile) { mutableStateOf(activeProfile.printerMac) }
    var printMode by remember(activeProfile) { mutableStateOf(activeProfile.printMode) }
    var selectedPaperProfileId by remember(activeProfile) { mutableStateOf(activeProfile.activePaperProfileId) }
    var selectedTemplateId by remember(activeProfile) { mutableStateOf(activeProfile.activeTemplateId) }

    val selectedPaperProfile = safePaperProfiles.find { it.id == selectedPaperProfileId }
        ?: safePaperProfiles.first()

    val autoMaxCharsNormal = ThermalPrintEngine.calculateAutoMaxChars(
        widthMm = selectedPaperProfile.widthMm,
        marginMm = selectedPaperProfile.marginMm,
        printMode = printMode,
        isBold = false,
        customOverride = selectedPaperProfile.customMaxChars
    )

    var showProfileDropdown by remember { mutableStateOf(false) }
    var showPaperDropdown by remember { mutableStateOf(false) }
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
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
                    safeProfiles.forEach { prof ->
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
                val firstPaper = safePaperProfiles.first()
                val newProf = PrinterProfile(
                    id = UUID.randomUUID().toString(),
                    profileName = "Profil Baru ${safeProfiles.size + 1}",
                    activePaperProfileId = firstPaper.id,
                    paperWidthMm = firstPaper.widthMm,
                    labelHeightMm = firstPaper.heightMm,
                    labelGapMm = firstPaper.gapMm,
                    marginMm = firstPaper.marginMm,
                    customMaxChars = firstPaper.customMaxChars
                )
                scope.launch {
                    configManager.saveState(
                        state.copy(
                            activeProfileId = newProf.id,
                            profiles = safeProfiles + newProf
                        )
                    )
                }
            }) {
                Text("+ Baru")
            }

            if (safeProfiles.size > 1) {
                TextButton(onClick = {
                    val remaining = safeProfiles.filterNot { it.id == activeProfile.id }
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

        // 1. PERANGKAT PRINTER
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

        // 2. MODE MEKANISME KERTAS
        Text(
            text = if (isId) "2. Mode Mekanisme Kertas & Bahasa Cetak" else "2. Paper Mechanism & Command Mode",
            fontWeight = FontWeight.SemiBold
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        text = if (isId) "Kertas Thermal Continuous" else "Continuous Thermal Paper",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isId) "Mencetak dengan bahasa ESC/POS" else "Printing with ESC/POS language",
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
                        text = if (isId) "Kertas Thermal dengan Gap" else "Thermal Paper with Gap",
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isId) "Mencetak dengan bahasa TSPL. Pastikan printer mempunyai sensor gap" else "Printing with TSPL language. Ensure the printer has a gap sensor",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // 3. UKURAN KERTAS
        Text(
            text = if (isId) "3. Ukuran Kertas" else "3. Paper Size",
            fontWeight = FontWeight.SemiBold
        )
        ExposedDropdownMenuBox(
            expanded = showPaperDropdown,
            onExpandedChange = { showPaperDropdown = !showPaperDropdown }
        ) {
            OutlinedTextField(
                value = "${selectedPaperProfile.name} (${selectedPaperProfile.widthMm}x${selectedPaperProfile.heightMm}mm)",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPaperDropdown) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = showPaperDropdown,
                onDismissRequest = { showPaperDropdown = false }
            ) {
                safePaperProfiles.forEach { paper ->
                    DropdownMenuItem(
                        text = { Text("${paper.name} (${paper.widthMm}x${paper.heightMm}mm, Gap ${paper.gapMm}mm)") },
                        onClick = {
                            selectedPaperProfileId = paper.id
                            showPaperDropdown = false
                        }
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isId)
                        "Maksimal karakter dalam satu baris: $autoMaxCharsNormal"
                    else
                        "Maximum character in one line : $autoMaxCharsNormal",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (isId)
                        "Daftar ukuran kertas dapat ditambah atau diubah di tab 'Ukuran Kertas'."
                    else
                        "Paper presets can be added or modified on the 'Paper Sizes' tab.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 4. TEMPLATE CETAK
        Text(
            text = if (isId) "4. Template Cetak" else "4. Print Template",
            fontWeight = FontWeight.SemiBold
        )

        val currentTemplate = safeTemplates.find { it.id == selectedTemplateId } ?: safeTemplates.first()
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
                safeTemplates.forEach { tpl ->
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

        Spacer(modifier = Modifier.height(6.dp))

        // SIMPAN & TES CETAK
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
                        activePaperProfileId = selectedPaperProfile.id,
                        paperWidthMm = selectedPaperProfile.widthMm,
                        labelHeightMm = selectedPaperProfile.heightMm,
                        labelGapMm = selectedPaperProfile.gapMm,
                        marginMm = selectedPaperProfile.marginMm,
                        customMaxChars = selectedPaperProfile.customMaxChars,
                        activeTemplateId = selectedTemplateId
                    )
                    val updatedList = safeProfiles.map {
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
                        activePaperProfileId = selectedPaperProfile.id,
                        paperWidthMm = selectedPaperProfile.widthMm,
                        labelHeightMm = selectedPaperProfile.heightMm,
                        labelGapMm = selectedPaperProfile.gapMm,
                        marginMm = selectedPaperProfile.marginMm,
                        customMaxChars = selectedPaperProfile.customMaxChars,
                        activeTemplateId = selectedTemplateId
                    )
                    val sampleData = mapOf(
                        "lorem" to "lorem ipsum dolor sit amet"
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
                        Text("Tidak ada perangkat Bluetooth yang di-pair di perangkat ini.")
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
                TextButton(onClick = { showDeviceDialog = false }) { Text("Tutup") }
            }
        )
    }
}

// TAB 2: UKURAN KERTAS
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperSizeProfileTab(
    state: AppStateData,
    configManager: PrinterConfigManager,
    isId: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val safePaperProfiles = state.paperProfiles.ifEmpty { AppStateData().paperProfiles }
    var selectedPaperId by remember { mutableStateOf(safePaperProfiles.first().id) }
    val currentPaper = safePaperProfiles.find { it.id == selectedPaperId } ?: safePaperProfiles.first()

    var paperName by remember(currentPaper) { mutableStateOf(currentPaper.name) }
    var widthStr by remember(currentPaper) { mutableStateOf(currentPaper.widthMm.toString()) }
    var heightStr by remember(currentPaper) { mutableStateOf(currentPaper.heightMm.toString()) }
    var gapStr by remember(currentPaper) { mutableStateOf(currentPaper.gapMm.toString()) }
    var marginStr by remember(currentPaper) { mutableStateOf(currentPaper.marginMm.toString()) }
    var customCharsStr by remember(currentPaper) {
        mutableStateOf(if (currentPaper.customMaxChars > 0) currentPaper.customMaxChars.toString() else "")
    }
    var showDropdown by remember { mutableStateOf(false) }

    val wMm = widthStr.toIntOrNull() ?: 70
    val mMm = marginStr.toIntOrNull() ?: 3
    val overrideChars = customCharsStr.toIntOrNull() ?: 0

    val tsplNormalChars = ThermalPrintEngine.calculateAutoMaxChars(wMm, mMm, "TSPL'LABEL", false, overrideChars)
    val tsplBoldChars = ThermalPrintEngine.calculateAutoMaxChars(wMm, mMm, "TSPL'LABEL", true, overrideChars)
    val escPosChars = ThermalPrintEngine.calculateAutoMaxChars(wMm, mMm, "ESCPOS_LABEL", false, overrideChars)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (isId) "Kelola Profil Ukuran Kertas" else "Manage Paper Size Profiles",
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
                    value = currentPaper.name,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false }
                ) {
                    state.paperProfiles.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.name} (${p.widthMm}x${p.heightMm}mm") },
                            onClick = {
                                selectedPaperId = p.id
                                showDropdown = false
                            }
                        )
                    }
                }
            }

            FilledTonalButton(onClick = {
                val newPaper = PaperSizeProfile(
                    id = UUID.randomUUID().toString(),
                    name = "Kertas Baru ${safePaperProfiles.size + 1}",
                    widthMm = 60,
                    heightMm = 40,
                    gapMm = 2,
                    marginMm = 3
                )
                scope.launch {
                    configManager.saveState(state.copy(paperProfiles = safePaperProfiles + newPaper))
                    selectedPaperId = newPaper.id
                }
            }) {
                Text("+ Baru")
            }

            if (safePaperProfiles.size > 1) {
                TextButton(onClick = {
                    val remaining = safePaperProfiles.filterNot { it.id == currentPaper.id }
                    scope.launch {
                        configManager.saveState(state.copy(paperProfiles = remaining))
                        selectedPaperId = remaining.first().id
                    }
                }) {
                    Text(if (isId) "Hapus" else "Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        OutlinedTextField(
            value = paperName,
            onValueChange = { paperName = it },
            label = { Text(if (isId) "Nama Profil Kertas" else "Paper Profile Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = widthStr,
                onValueChange = { widthStr = it },
                label = { Text(if (isId) "Lebar (mm)" else "Width (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = heightStr,
                onValueChange = { heightStr = it },
                label = { Text(if (isId) "Tinggi (mm)" else "Height (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = gapStr,
                onValueChange = { gapStr = it },
                label = { Text(if (isId) "Gap (mm)" else "Gap (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = marginStr,
                onValueChange = { marginStr = it },
                label = { Text(if (isId) "Margin Kiri/Kanan (mm)" else "Side Margin (mm)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = customCharsStr,
                onValueChange = { customCharsStr = it },
                label = { Text(if (isId) "Batas Karakter (Kosong = Auto)" else "Max Chars (Blank = Auto)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (isId) "Hasil Kalkulasi Kapasitas Karakter Kertas:" else "Calculated Character Capacity:",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "• TSPL Huruf Normal: $tsplNormalChars karakter / baris",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• TSPL Huruf Tebal: $tsplBoldChars karakter / baris",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• ESC/POS: $escPosChars karakter / baris",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Button(
            onClick = {
                val updatedPaper = currentPaper.copy(
                    name = paperName,
                    widthMm = widthStr.toIntOrNull() ?: 70,
                    heightMm = heightStr.toIntOrNull() ?: 50,
                    gapMm = gapStr.toIntOrNull() ?: 2,
                    marginMm = marginStr.toIntOrNull() ?: 3,
                    customMaxChars = customCharsStr.toIntOrNull() ?: 0
                )
                val updatedPaperList = safePaperProfiles.map {
                    if (it.id == updatedPaper.id) updatedPaper else it
                }
                val syncedPrinterProfiles = state.profiles.map { prof ->
                    if (prof.activePaperProfileId == updatedPaper.id) {
                        prof.copy(
                            paperWidthMm = updatedPaper.widthMm,
                            labelHeightMm = updatedPaper.heightMm,
                            labelGapMm = updatedPaper.gapMm,
                            marginMm = updatedPaper.marginMm,
                            customMaxChars = updatedPaper.customMaxChars
                        )
                    } else prof
                }
                scope.launch {
                    configManager.saveState(
                        state.copy(
                            paperProfiles = updatedPaperList,
                            profiles = syncedPrinterProfiles
                        )
                    )
                    Toast.makeText(context, "Profil kertas '${paperName}' berhasil disimpan!", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isId) "Simpan Profil Ukuran Kertas" else "Save Paper Size Profile")
        }
    }
}

// TAB 3: TEMPLATE CETAK
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

    val safeTemplates = state.templates.ifEmpty { AppStateData().templates }
    var selectedTplId by remember { mutableStateOf(safeTemplates.first().id) }
    val currentTpl = safeTemplates.find { it.id == selectedTplId } ?: safeTemplates.first()

    var tplName by remember(currentTpl) { mutableStateOf(currentTpl.name) }
    var tplBody by remember(currentTpl) { mutableStateOf(currentTpl.bodyPattern) }
    var showDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (isId) "Kelola Template Kertas" else "Manage Paper Templates",
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
                    bodyPattern = ""
                )
                scope.launch {
                    configManager.saveState(state.copy(templates = safeTemplates + newTpl))
                    selectedTplId = newTpl.id
                }
            }) {
                Text("+ Baru")
            }

            if (safeTemplates.size > 1) {
                TextButton(onClick = {
                    val remaining = safeTemplates.filterNot { it.id == currentTpl.id }
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
                Text("• Baris yang memakai titik dua otomatis disejajarkan", style = MaterialTheme.typography.bodySmall)
                Text("• [C] di awal baris adalah rata tengah", style = MaterialTheme.typography.bodySmall)
                Text("• [B] di awal baris adalah huruf tebal", style = MaterialTheme.typography.bodySmall)
                Text("• [LINE] adalah garis pemisah horizontal", style = MaterialTheme.typography.bodySmall)
                Text("• [BARCODE]{kode} adalah untuk generate Barcode Batang", style = MaterialTheme.typography.bodySmall)
                Text("• [QRCODE]{kode} adalah untuk generate QR Code", style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AssistChip(onClick = { tplBody += "\n[C][B]{judul}" }, label = { Text("+ Judul") })
            AssistChip(onClick = { tplBody += "\n[LINE]" }, label = { Text("+ Garis") })
            AssistChip(onClick = { tplBody += "\n[C][BARCODE]{kode}" }, label = { Text("+ Barcode") })
            AssistChip(onClick = { tplBody += "\n[C][QRCODE]{kode}" }, label = { Text("+ QR Code") })
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
                val updatedList = safeTemplates.map { if (it.id == updatedTpl.id) updatedTpl else it }
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