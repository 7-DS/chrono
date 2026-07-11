package com.chrono.ssh.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrono.ssh.core.data.BackupImportReport
import com.chrono.ssh.core.data.sanitizeColorHex
import com.chrono.ssh.core.model.ConnectionEvent
import com.chrono.ssh.core.model.ConnectionEventLevel
import com.chrono.ssh.core.model.CrashLogEntry
import com.chrono.ssh.core.model.AppSettings
import com.chrono.ssh.core.model.ServerCardDiskMode
import com.chrono.ssh.core.model.ServerCardNetworkMode
import com.chrono.ssh.core.model.ServerDetailCard
import com.chrono.ssh.core.model.ServerMetricColorPreset
import com.chrono.ssh.core.model.TerminalCursorStyle
import com.chrono.ssh.core.service.BackupEncryptionCodec
import com.chrono.ssh.core.service.HostShareQrCodec
import com.chrono.ssh.core.service.PinLockPolicy
import com.chrono.ssh.core.service.ServerStatusRefreshPolicy
import com.chrono.ssh.core.service.SftpSortMode
import com.chrono.ssh.core.service.TerminalAccessoryKeyPolicy
import com.chrono.ssh.ui.appLockPinUsable
import com.chrono.ssh.ui.design.DeckCard
import com.chrono.ssh.ui.design.DeckColors
import com.chrono.ssh.ui.design.DeckThemeCatalog
import com.chrono.ssh.ui.design.DeckThemeFamily
import com.chrono.ssh.ui.design.DeckThemeMode
import com.chrono.ssh.ui.design.HeadingFontTarget
import com.chrono.ssh.ui.design.LargeScreenTitle
import com.chrono.ssh.ui.design.SegmentedPillControl
import com.chrono.ssh.ui.design.ServerMetricColorOverrides
import com.chrono.ssh.ui.design.metricColorHex
import com.chrono.ssh.ui.design.metricColorOverridesFrom
import com.chrono.ssh.ui.design.metricColorsFor
import com.chrono.ssh.ui.terminal.TerminalCatalog
import java.io.File

@Composable
fun SettingsScreen(
    themeMode: DeckThemeMode,
    themeFamilyId: String,
    settings: AppSettings,
    selectionPage: SettingsSelectionPage?,
    diagnostics: List<ConnectionEvent>,
    crashLogs: List<CrashLogEntry>,
    backupContent: String,
    biometricAvailable: Boolean = false,
    onSelectionPageChange: (SettingsSelectionPage?) -> Unit,
    onThemeModeChange: (DeckThemeMode) -> Unit,
    onThemeFamilyChange: (String) -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onLockNow: () -> Unit = {},
    backgroundUsageAllowed: Boolean = false,
    onRequestBackgroundUsage: () -> Unit = {},
    onInspectBackupImport: (String) -> BackupImportReport,
    onImportBackupMetadata: (String) -> BackupImportReport,
    onImportHostShareLink: (String) -> String,
    onImportOpenSshConfig: (String) -> String,
    onClearCrashLogs: () -> Unit
) {
    val context = LocalContext.current
    var activeSection by remember { mutableStateOf<SettingsSection?>(null) }
    var diagnosticsOpen by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }
    var importWillMerge by remember { mutableStateOf(false) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }
    var encryptExportOpen by remember { mutableStateOf(false) }
    var pendingEncryptedImport by remember { mutableStateOf<String?>(null) }
    var hostShareImportOpen by remember { mutableStateOf(false) }
    var pinDialogMode by remember { mutableStateOf<PinDialogMode?>(null) }
    var securityStatus by remember { mutableStateOf<String?>(null) }
    var headingFontStatus by remember { mutableStateOf<String?>(null) }
    var pendingHeadingFontTarget by remember { mutableStateOf<HeadingFontTarget?>(null) }
    val headingFontLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingHeadingFontTarget
        pendingHeadingFontTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        runCatching {
            importHeadingFont(context, uri, target)
        }.onSuccess { path ->
            onSettingsChange(settings.withHeadingFontPath(target, path))
            headingFontStatus = "${target.label} heading font imported."
        }.onFailure {
            headingFontStatus = "Font import failed: ${it.message ?: it::class.java.simpleName}"
        }
    }
    fun backupReportMessage(report: BackupImportReport): String {
        return if (report.valid) {
            val summary = report.sections.entries.joinToString(", ") { "${it.key}: ${it.value}" }.ifBlank { "no records" }
            val outcomes = buildList {
                if (report.insertedRows > 0) add("${report.insertedRows} inserted")
                if (report.updatedRows > 0) add("${report.updatedRows} updated")
                if (report.malformedRows > 0) add("${report.malformedRows} malformed")
                if (report.skippedRows > 0) add("${report.skippedRows} skipped")
                if (report.credentialMetadataRows > 0) add("${report.credentialMetadataRows} credentials need secrets")
            }.joinToString(", ").ifBlank { "0 skipped" }
            "${report.message} $summary. $outcomes."
        } else {
            report.message
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write((pendingExportText ?: backupContent).toByteArray(Charsets.UTF_8))
            } ?: error("Could not create backup file.")
        }.onSuccess {
            backupStatus = "Backup exported."
        }.onFailure {
            backupStatus = "Export failed: ${it.message ?: it::class.java.simpleName}"
        }.also {
            pendingExportText = null
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.map { text ->
            if (BackupEncryptionCodec.isEncrypted(text)) {
                pendingEncryptedImport = text
                null
            } else {
                if (importWillMerge) onImportBackupMetadata(text) else onInspectBackupImport(text)
            }
        }
            .onSuccess { report ->
                backupStatus = report?.let(::backupReportMessage) ?: "Encrypted backup selected. Enter passphrase."
                if (report != null) importWillMerge = false
            }
            .onFailure {
                backupStatus = "Import check failed: ${it.message ?: it::class.java.simpleName}"
            }
    }
    val hostQrImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bitmap = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                ?: error("Could not decode QR image.")
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            HostShareQrCodec.decodePixels(bitmap.width, bitmap.height, pixels)
                ?: error("No ChronoSSH QR code found.")
        }.onSuccess { payload ->
            backupStatus = onImportHostShareLink(payload)
        }.onFailure {
            backupStatus = "QR import failed: ${it.message ?: it::class.java.simpleName}"
        }
    }
    val sshConfigImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.onSuccess { text ->
            backupStatus = onImportOpenSshConfig(text)
        }.onFailure {
            backupStatus = "SSH config import failed: ${it.message ?: it::class.java.simpleName}"
        }
    }
    pinDialogMode?.let { mode ->
        PinLockDialog(
            mode = mode,
            hasExistingPin = appLockPinUsable(settings),
            onDismiss = { pinDialogMode = null },
            onConfirm = { currentPin, nextPin ->
                when (mode) {
                    PinDialogMode.Set -> {
                        PinLockPolicy.validatePin(nextPin)?.let { error ->
                            securityStatus = error
                        } ?: run {
                            val result = PinLockPolicy.hashPin(nextPin)
                            val nextSettings = settings.copy(appLockPinHash = result.hash, appLockPinSalt = result.salt)
                            onSettingsChange(nextSettings)
                            securityStatus = "App lock is on. Use Lock Now to test it, or leave and return to the app."
                            pinDialogMode = null
                        }
                    }
                    PinDialogMode.Change -> {
                        if (!PinLockPolicy.verify(currentPin, settings.appLockPinHash, settings.appLockPinSalt)) {
                            securityStatus = "Current PIN is incorrect."
                        } else {
                            PinLockPolicy.validatePin(nextPin)?.let { error ->
                                securityStatus = error
                            } ?: run {
                                val result = PinLockPolicy.hashPin(nextPin)
                                val nextSettings = settings.copy(appLockPinHash = result.hash, appLockPinSalt = result.salt)
                                onSettingsChange(nextSettings)
                                securityStatus = "PIN changed."
                                pinDialogMode = null
                            }
                        }
                    }
                    PinDialogMode.Disable -> {
                        if (PinLockPolicy.verify(currentPin, settings.appLockPinHash, settings.appLockPinSalt)) {
                            val nextSettings = settings.copy(appLockPinHash = null, appLockPinSalt = null, appLockBiometricEnabled = false)
                            onSettingsChange(nextSettings)
                            securityStatus = "App lock disabled."
                            pinDialogMode = null
                        } else {
                            securityStatus = "Current PIN is incorrect."
                        }
                    }
                }
            }
        )
    }
    selectionPage?.let { page ->
        SettingsSelectionScreen(
            page = page,
            themeMode = themeMode,
            themeFamilyId = themeFamilyId,
            settings = settings,
            onBack = { onSelectionPageChange(null) },
            onThemeFamilyChange = {
                onThemeFamilyChange(it)
                onSelectionPageChange(null)
            },
            onSettingsChange = {
                onSettingsChange(it)
                onSelectionPageChange(null)
            }
        )
        return
    }
    activeSection?.let { section ->
        BackHandler { activeSection = null }
        SettingsSectionScreen(
            title = section.title,
            onBack = { activeSection = null }
        ) {
            when (section) {
                SettingsSection.Appearance -> AppearanceSettings(
                    themeMode = themeMode,
                    themeFamilyId = themeFamilyId,
                    settings = settings,
                    fontStatus = headingFontStatus,
                    onThemeModeChange = onThemeModeChange,
                    onSettingsChange = onSettingsChange,
                    onSelectionPageChange = onSelectionPageChange,
                    onImportHeadingFont = { target ->
                        pendingHeadingFontTarget = target
                        headingFontLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream"))
                    },
                    onClearHeadingFont = { target ->
                        onSettingsChange(settings.withHeadingFontPath(target, null))
                        headingFontStatus = "${target.label} heading font reset."
                    }
                )
                SettingsSection.Terminal -> TerminalSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onSelectionPageChange = onSelectionPageChange
                )
                SettingsSection.Monitoring -> MonitoringSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    onSelectionPageChange = onSelectionPageChange
                )
                SettingsSection.Files -> FilesSettings(
                    settings = settings,
                    onSettingsChange = onSettingsChange
                )
                SettingsSection.Security -> SecuritySettings(
                    settings = settings,
                    status = securityStatus,
                    biometricAvailable = biometricAvailable,
                    onSetPin = { pinDialogMode = PinDialogMode.Set },
                    onChangePin = { pinDialogMode = PinDialogMode.Change },
                    onToggleBiometric = {
                        if (biometricToggleEnabled(settings.appLockBiometricEnabled, biometricAvailable)) {
                            val nextSettings = settings.copy(appLockBiometricEnabled = !settings.appLockBiometricEnabled)
                            onSettingsChange(nextSettings)
                        }
                    },
                    onDisablePin = { pinDialogMode = PinDialogMode.Disable },
                    onLockNow = onLockNow,
                    backgroundUsageAllowed = backgroundUsageAllowed,
                    onRequestBackgroundUsage = onRequestBackgroundUsage
                )
                SettingsSection.Backups -> BackupSettings(
                    backupStatus = backupStatus,
                    onExport = {
                        pendingExportText = backupContent
                        exportLauncher.launch("ChronoSSH-${System.currentTimeMillis()}.chronossh")
                    },
                    onEncryptedExport = { encryptExportOpen = true },
                    onCheckImport = {
                        importWillMerge = false
                        importLauncher.launch(arrayOf("*/*"))
                    },
                    onImportHostLink = { hostShareImportOpen = true },
                    onImportHostQr = { hostQrImportLauncher.launch("image/*") },
                    onImportSshConfig = { sshConfigImportLauncher.launch(arrayOf("*/*")) },
                    onImport = {
                        importWillMerge = true
                        importLauncher.launch(arrayOf("*/*"))
                    }
                )
                SettingsSection.AboutDiagnostics -> AboutDiagnosticsSettings(
                    diagnostics = diagnostics,
                    crashLogs = crashLogs,
                    diagnosticsOpen = diagnosticsOpen,
                    onDiagnosticsOpenChange = { diagnosticsOpen = it },
                    onClearCrashLogs = onClearCrashLogs
                )
            }
        }
        return
    }
    if (encryptExportOpen) {
        BackupPassphraseDialog(
            title = "Encrypt backup",
            action = "Export",
            requireConfirmation = true,
            onDismiss = { encryptExportOpen = false },
            onConfirm = { passphrase ->
                runCatching {
                    pendingExportText = BackupEncryptionCodec.encryptToString(backupContent, passphrase.toCharArray())
                    encryptExportOpen = false
                    exportLauncher.launch("ChronoSSH-${System.currentTimeMillis()}.chronossh.enc")
                }.onFailure {
                    backupStatus = "Encryption failed: ${it.message ?: it::class.java.simpleName}"
                }
            }
        )
    }
    if (hostShareImportOpen) {
        HostShareLinkImportDialog(
            onDismiss = { hostShareImportOpen = false },
            onImport = { payload ->
                backupStatus = onImportHostShareLink(payload)
                hostShareImportOpen = false
            }
        )
    }
    pendingEncryptedImport?.let { encryptedText ->
        BackupPassphraseDialog(
            title = "Decrypt backup",
            action = if (importWillMerge) "Import" else "Check",
            requireConfirmation = false,
            onDismiss = {
                pendingEncryptedImport = null
                importWillMerge = false
            },
            onConfirm = { passphrase ->
                runCatching {
                    val plainText = BackupEncryptionCodec.decryptToString(encryptedText, passphrase.toCharArray())
                    if (importWillMerge) onImportBackupMetadata(plainText) else onInspectBackupImport(plainText)
                }.onSuccess { report ->
                    backupStatus = backupReportMessage(report)
                    pendingEncryptedImport = null
                    importWillMerge = false
                }.onFailure {
                    backupStatus = "Decrypt failed: ${it.message ?: it::class.java.simpleName}"
                }
            }
        )
    }
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        LargeScreenTitle("Settings", headingTarget = HeadingFontTarget.Settings)
        Text("App, terminal, monitoring, backups", color = DeckColors.SecondaryText, fontSize = 17.sp)
        Spacer(Modifier.height(22.dp))
        SettingsSection.entries.forEach { section ->
            val value = when (section) {
                SettingsSection.Appearance -> DeckThemeCatalog.families.firstOrNull { it.id == themeFamilyId }?.name.orEmpty()
                SettingsSection.Terminal -> "${settings.terminalFontSizeSp} sp · ${settings.terminalThemeName}"
                SettingsSection.Monitoring -> monitoringSettingsSummary(settings)
                SettingsSection.Files -> filesSettingsSummary(settings)
                SettingsSection.Security -> if (appLockPinUsable(settings)) "PIN enabled" else "Off"
                SettingsSection.Backups -> backupStatus?.take(24) ?: "Export and import"
                SettingsSection.AboutDiagnostics -> "${diagnostics.size} events · ${crashLogs.size} crashes"
            }
            SettingsSectionRow(section.title, section.detail, value) { activeSection = section }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(90.dp))
    }
}

private enum class SettingsSection(val title: String, val detail: String) {
    Appearance("Appearance", "Theme, palette and accent"),
    Terminal("Terminal", "Typeface, colors, cursor, input"),
    Monitoring("Monitoring", "Refresh and server-card displays"),
    Files("Files", "SFTP sort and browser defaults"),
    Security("Security", "App lock"),
    Backups("Backups", "Export, inspect, merge"),
    AboutDiagnostics("About / Diagnostics", "App details and connection events")
}

internal fun monitoringSettingsSummary(settings: AppSettings): String {
    val network = when (settings.serverCardNetworkMode) {
        ServerCardNetworkMode.Totals -> "net totals"
        ServerCardNetworkMode.Rates -> "net speed"
    }
    val disk = when (settings.serverCardDiskMode) {
        ServerCardDiskMode.Usage -> "disk usage"
        ServerCardDiskMode.Rates -> "disk speed"
        ServerCardDiskMode.Totals -> "disk totals"
    }
    val background = if (settings.uptimeBackgroundMonitoringEnabled) "uptime bg on" else "uptime bg off"
    return "${settings.autoRefreshSeconds}s · $background · $network · $disk · ${settings.serverMetricColorPreset.label()}"
}

internal fun filesSettingsSummary(settings: AppSettings): String {
    val direction = if (settings.sftpDefaultSortDescending) "desc" else "asc"
    val hidden = if (settings.sftpShowHiddenByDefault) "hidden on" else "hidden off"
    return "${settings.sftpDefaultSortModeName.lowercase()} $direction · $hidden"
}

internal fun biometricToggleEnabled(currentlyEnabled: Boolean, biometricAvailable: Boolean): Boolean {
    return biometricAvailable || currentlyEnabled
}

internal fun showBiometricUnavailableMessage(appLockEnabled: Boolean, biometricEnabled: Boolean, biometricAvailable: Boolean): Boolean {
    return appLockEnabled && biometricEnabled && !biometricAvailable
}

internal fun settingsSecurityStateCanStayVisible(pending: AppSettings, committed: AppSettings): Boolean {
    return pending.appLockPinHash != committed.appLockPinHash ||
        pending.appLockPinSalt != committed.appLockPinSalt ||
        pending.appLockBiometricEnabled != committed.appLockBiometricEnabled
}

@Composable
private fun SettingsSectionScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectorIconButton("<", onBack)
            Text(
                title,
                color = DeckColors.PrimaryText,
                fontSize = 24.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
            Spacer(Modifier.size(46.dp))
        }
        Spacer(Modifier.height(14.dp))
        content()
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun AppearanceSettings(
    themeMode: DeckThemeMode,
    themeFamilyId: String,
    settings: AppSettings,
    fontStatus: String?,
    onThemeModeChange: (DeckThemeMode) -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onSelectionPageChange: (SettingsSelectionPage?) -> Unit,
    onImportHeadingFont: (HeadingFontTarget) -> Unit,
    onClearHeadingFont: (HeadingFontTarget) -> Unit
) {
    var headingFontsOpen by remember { mutableStateOf(false) }
    SettingsCard("Appearance") {
        SegmentedPillControl(
            items = DeckThemeMode.entries.map { it.label },
            selectedIndex = DeckThemeMode.entries.indexOf(themeMode),
            modifier = Modifier.fillMaxWidth(),
            onSelected = { onThemeModeChange(DeckThemeMode.entries[it]) }
        )
        Spacer(Modifier.height(14.dp))
        val activeFamily = DeckThemeCatalog.families.firstOrNull { it.id == themeFamilyId }
            ?: DeckThemeCatalog.families.first()
        SettingsChoiceRow(
            title = "App theme",
            detail = "Palette",
            value = activeFamily.name,
            onClick = { onSelectionPageChange(SettingsSelectionPage.AppTheme) }
        )
        Spacer(Modifier.height(12.dp))
        SettingsChoiceRow(
            title = "Metrics Page",
            detail = "Show, hide, and reorder server detail cards",
            value = "",
            onClick = { onSelectionPageChange(SettingsSelectionPage.MetricsPage) }
        )
        Spacer(Modifier.height(12.dp))
        SettingsChoiceRow(
            title = "Heading fonts",
            detail = "Custom .ttf/.otf per heading",
            value = if (headingFontsOpen) "Hide" else "Show",
            onClick = { headingFontsOpen = !headingFontsOpen }
        )
        AnimatedVisibility(visible = headingFontsOpen) {
            Column {
                Spacer(Modifier.height(8.dp))
                HeadingFontTarget.entries.forEach { target ->
                    HeadingFontRow(
                        target = target,
                        path = settings.headingFontPath(target),
                        onImport = { onImportHeadingFont(target) },
                        onClear = { onClearHeadingFont(target) }
                    )
                }
            }
        }
        fontStatus?.let { SettingsStatusMessage(it) }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun TerminalSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onSelectionPageChange: (SettingsSelectionPage?) -> Unit
) {
    var customKeysOpen by remember { mutableStateOf(false) }
    var customKeysText by remember(settings.terminalAccessoryKeys) { mutableStateOf(TerminalAccessoryKeyPolicy.normalizeCsv(settings.terminalAccessoryKeys)) }
    SettingsCard("Terminal") {
        SettingsChoiceRow(
            title = "Terminal theme",
            detail = "Color scheme",
            value = settings.terminalThemeName,
            onClick = { onSelectionPageChange(SettingsSelectionPage.TerminalTheme) }
        )
        SettingsChoiceRow(
            title = "Terminal font",
            detail = "Typeface",
            value = settings.terminalFontFamily,
            onClick = { onSelectionPageChange(SettingsSelectionPage.TerminalFont) }
        )
        SettingsStepperRow(
            title = "Font size",
            detail = "Text size inside SSH sessions",
            value = "${settings.terminalFontSizeSp} sp",
            canDecrease = settings.terminalFontSizeSp > 10,
            canIncrease = settings.terminalFontSizeSp < 24,
            onDecrease = { onSettingsChange(settings.copy(terminalFontSizeSp = settings.terminalFontSizeSp - 1)) },
            onIncrease = { onSettingsChange(settings.copy(terminalFontSizeSp = settings.terminalFontSizeSp + 1)) }
        )
        SettingsStepperRow(
            title = "Scrollback",
            detail = "Lines retained per terminal",
            value = "${settings.terminalScrollbackLines}",
            canDecrease = settings.terminalScrollbackLines > 1000,
            canIncrease = settings.terminalScrollbackLines < 50000,
            onDecrease = { onSettingsChange(settings.copy(terminalScrollbackLines = (settings.terminalScrollbackLines - 1000).coerceAtLeast(1000))) },
            onIncrease = { onSettingsChange(settings.copy(terminalScrollbackLines = (settings.terminalScrollbackLines + 1000).coerceAtMost(50000))) }
        )
        SettingsStepperRow(
            title = "Left margin",
            detail = "Left terminal inset",
            value = "${settings.terminalSideMarginDp} px",
            canDecrease = settings.terminalSideMarginDp > 0,
            canIncrease = settings.terminalSideMarginDp < 8,
            onDecrease = { onSettingsChange(settings.copy(terminalSideMarginDp = (settings.terminalSideMarginDp - 1).coerceAtLeast(0))) },
            onIncrease = { onSettingsChange(settings.copy(terminalSideMarginDp = (settings.terminalSideMarginDp + 1).coerceAtMost(8))) }
        )
        SettingsStepperRow(
            title = "Right margin",
            detail = "Right terminal inset",
            value = "${settings.terminalRightMarginDp} px",
            canDecrease = settings.terminalRightMarginDp > 0,
            canIncrease = settings.terminalRightMarginDp < 8,
            onDecrease = { onSettingsChange(settings.copy(terminalRightMarginDp = (settings.terminalRightMarginDp - 1).coerceAtLeast(0))) },
            onIncrease = { onSettingsChange(settings.copy(terminalRightMarginDp = (settings.terminalRightMarginDp + 1).coerceAtMost(8))) }
        )
        SettingsChoiceRow(
            title = "Cursor",
            detail = "Tap to cycle the terminal cursor shape",
            value = settings.terminalCursorStyle.name,
            onClick = {
                val next = when (settings.terminalCursorStyle) {
                    TerminalCursorStyle.Block -> TerminalCursorStyle.Underline
                    TerminalCursorStyle.Underline -> TerminalCursorStyle.Beam
                    TerminalCursorStyle.Beam -> TerminalCursorStyle.Block
                }
                onSettingsChange(settings.copy(terminalCursorStyle = next))
            }
        )
        SettingsToggleRow(
            title = "Bracketed paste",
            checked = settings.terminalBracketedPaste,
            detail = "Send paste boundaries to shells and editors that support them",
            onCheckedChange = { onSettingsChange(settings.copy(terminalBracketedPaste = it)) }
        )
        SettingsToggleRow(
            title = "Haptics",
            checked = settings.terminalHapticFeedback,
            detail = "Vibrate on terminal helper key taps",
            onCheckedChange = { onSettingsChange(settings.copy(terminalHapticFeedback = it)) }
        )
        SettingsToggleRow(
            title = "Keep screen awake",
            checked = settings.terminalKeepScreenOn,
            detail = "Prevent the display from sleeping while a terminal is open",
            onCheckedChange = { onSettingsChange(settings.copy(terminalKeepScreenOn = it)) }
        )
        SettingsToggleRow(
            title = "Single-row accessory bar",
            checked = settings.terminalAccessorySingleRow,
            detail = "Keep helper keys in one horizontal strip",
            onCheckedChange = { onSettingsChange(settings.copy(terminalAccessorySingleRow = it)) }
        )
        SettingsToggleRow(
            title = "Accessory popups",
            checked = settings.terminalAccessoryPopups,
            detail = "Reserve compact popup groups for helper keys",
            onCheckedChange = { onSettingsChange(settings.copy(terminalAccessoryPopups = it)) }
        )
        SettingsToggleRow(
            title = "Full accessory strip",
            checked = settings.terminalAccessoryFullScroll,
            detail = "Show all helper keys in one scrollable strip",
            onCheckedChange = { onSettingsChange(settings.copy(terminalAccessoryFullScroll = it)) }
        )
        SettingsRow(
            title = "Accessory keys",
            detail = TerminalAccessoryKeyPolicy.labels(settings.terminalAccessoryKeys).joinToString(" "),
            badge = "Keys"
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            terminalAccessoryKeyPresets().forEach { preset ->
                SettingsActionButton(preset.name, onClick = {
                    onSettingsChange(settings.copy(terminalAccessoryKeys = preset.csv))
                })
            }
            SettingsActionButton("Custom", onClick = {
                customKeysText = TerminalAccessoryKeyPolicy.normalizeCsv(settings.terminalAccessoryKeys)
                customKeysOpen = true
            })
        }
    }
    if (customKeysOpen) {
        AlertDialog(
            onDismissRequest = { customKeysOpen = false },
            title = { Text("Accessory keys", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customKeysText,
                        onValueChange = { customKeysText = it },
                        singleLine = false,
                        minLines = 2,
                        label = { Text("Comma-separated keys") },
                        placeholder = { Text(TerminalAccessoryKeyPolicy.DefaultCsv) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    Text(
                        TerminalAccessoryKeyPolicy.labels(customKeysText).joinToString(" "),
                        color = DeckColors.SecondaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSettingsChange(settings.copy(terminalAccessoryKeys = TerminalAccessoryKeyPolicy.normalizeCsv(customKeysText)))
                        customKeysOpen = false
                    }
                ) {
                    Text("Save", color = DeckColors.Cyan, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { customKeysOpen = false }) {
                    Text("Cancel", color = DeckColors.SecondaryText)
                }
            },
            containerColor = DeckColors.Surface,
            titleContentColor = DeckColors.PrimaryText,
            textContentColor = DeckColors.SecondaryText
        )
    }
}

private data class TerminalAccessoryKeyPreset(
    val name: String,
    val csv: String
)

private fun terminalAccessoryKeyPresets(): List<TerminalAccessoryKeyPreset> {
    return listOf(
        TerminalAccessoryKeyPreset("Default", TerminalAccessoryKeyPolicy.DefaultCsv),
        TerminalAccessoryKeyPreset("Compact", "Esc,Tab,Ctrl,Alt,AltGr,/,|,~,Up,Down"),
        TerminalAccessoryKeyPreset("Nav", "Esc,Tab,Ctrl,Alt,Ins,Del,Home,End,PgUp,PgDn,Up,Down,Left,Right"),
        TerminalAccessoryKeyPreset("TUI", "Esc,Tab,Ctrl,Alt,AltGr,Shift,Ins,Del,Home,End,PgUp,PgDn,Up,Down,Left,Right"),
        TerminalAccessoryKeyPreset("Shell", "Esc,Ctrl,Alt,Tab,Enter,Bksp,/,\\,-,_,|,~,.,:"),
        TerminalAccessoryKeyPreset("Control", "Esc,Ctrl-C,Ctrl-D,Ctrl-Z,Ctrl-L,Ctrl-R,Ctrl-A,Ctrl-E,Ctrl-U,Ctrl-W"),
        TerminalAccessoryKeyPreset("Fn", "Esc,Ctrl,Alt,F1,F2,F3,F4,F5,F6,F7,F8,F9,F10,F11,F12")
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MonitoringSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onSelectionPageChange: (SettingsSelectionPage?) -> Unit
) {
        SettingsCard("Monitoring") {
        SettingsToggleRow(
            title = "Background uptime monitoring",
            checked = settings.uptimeBackgroundMonitoringEnabled,
            detail = "Keep uptime checks running after the app leaves the foreground",
            onCheckedChange = { onSettingsChange(settings.copy(uptimeBackgroundMonitoringEnabled = it)) }
        )
        Spacer(Modifier.height(10.dp))
        SettingsStepperRow(
            title = "Auto refresh",
            detail = "Reachability checks and eligible SSH metrics",
            value = "${settings.autoRefreshSeconds}s",
            canDecrease = settings.autoRefreshSeconds > ServerStatusRefreshPolicy.MinEnabledSeconds,
            canIncrease = settings.autoRefreshSeconds < ServerStatusRefreshPolicy.MaxEnabledSeconds,
            onDecrease = {
                onSettingsChange(
                    settings.copy(
                        autoRefreshSeconds = (settings.autoRefreshSeconds - 1)
                            .coerceAtLeast(ServerStatusRefreshPolicy.MinEnabledSeconds)
                    )
                )
            },
            onIncrease = {
                onSettingsChange(
                    settings.copy(
                        autoRefreshSeconds = (settings.autoRefreshSeconds + 1)
                            .coerceAtMost(ServerStatusRefreshPolicy.MaxEnabledSeconds)
                    )
                )
            }
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ServerStatusRefreshPolicy.PresetSeconds.forEach { seconds ->
                SettingsActionButton(
                    text = "${seconds}s",
                    enabled = settings.autoRefreshSeconds != seconds,
                    onClick = { onSettingsChange(settings.copy(autoRefreshSeconds = seconds)) }
                )
            }
        }
        SettingsRow("Server card network display", "Uploaded/downloaded totals or live speed", "Network")
        Spacer(Modifier.height(10.dp))
        SegmentedPillControl(
            items = listOf("Uploaded", "Speed"),
            selectedIndex = if (settings.serverCardNetworkMode == ServerCardNetworkMode.Totals) 0 else 1,
            modifier = Modifier.fillMaxWidth(),
            onSelected = {
                onSettingsChange(
                    settings.copy(
                        serverCardNetworkMode = if (it == 0) ServerCardNetworkMode.Totals else ServerCardNetworkMode.Rates
                    )
                )
            }
        )
        Spacer(Modifier.height(14.dp))
        SettingsRow("Server card disk display", "Usage ring restores the RAM-style disk card", "Disk")
        Spacer(Modifier.height(10.dp))
        SegmentedPillControl(
            items = listOf("Usage ring", "Read/write speed", "Read/write totals"),
            selectedIndex = when (settings.serverCardDiskMode) {
                ServerCardDiskMode.Usage -> 0
                ServerCardDiskMode.Rates -> 1
                ServerCardDiskMode.Totals -> 2
            },
            modifier = Modifier.fillMaxWidth(),
            onSelected = {
                onSettingsChange(
                    settings.copy(
                        serverCardDiskMode = when (it) {
                            1 -> ServerCardDiskMode.Rates
                            2 -> ServerCardDiskMode.Totals
                            else -> ServerCardDiskMode.Usage
                        }
                    )
                )
            }
        )
        Spacer(Modifier.height(14.dp))
        SettingsChoiceRow(
            title = "Server card metric colors",
            detail = "Preset or custom colors for CPU, RAM, disk, network and latency",
            value = settings.serverMetricColorPreset.label(),
            onClick = { onSelectionPageChange(SettingsSelectionPage.MetricColors) }
        )
        Spacer(Modifier.height(10.dp))
        MetricColorPreview(settings.serverMetricColorPreset, metricColorOverridesFrom(settings))
        Spacer(Modifier.height(10.dp))
        SettingsChoiceRow(
            title = "Latency color",
            detail = "Used by the ping chip on server cards",
            value = metricColorHex(metricColorsFor(settings).latency),
            onClick = { onSelectionPageChange(SettingsSelectionPage.MetricColors) }
        )
    }
}

@Composable
private fun FilesSettings(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit
) {
    SettingsCard("Files") {
        SettingsRow("SFTP default sort", "Initial file-manager ordering", settings.sftpDefaultSortModeName)
        Spacer(Modifier.height(10.dp))
        SegmentedPillControl(
            items = listOf("Name", "Modified", "Size"),
            selectedIndex = when (settings.sftpDefaultSortModeName) {
                SftpSortMode.Modified.name -> 1
                SftpSortMode.Size.name -> 2
                else -> 0
            },
            modifier = Modifier.fillMaxWidth(),
            onSelected = {
                onSettingsChange(
                    settings.copy(
                        sftpDefaultSortModeName = when (it) {
                            1 -> SftpSortMode.Modified.name
                            2 -> SftpSortMode.Size.name
                            else -> SftpSortMode.Name.name
                        }
                    )
                )
            }
        )
        Spacer(Modifier.height(10.dp))
        SettingsToggleRow(
            title = "Descending by default",
            checked = settings.sftpDefaultSortDescending,
            detail = "Start new SFTP browsers with newest or largest first",
            onCheckedChange = { onSettingsChange(settings.copy(sftpDefaultSortDescending = it)) }
        )
        SettingsToggleRow(
            title = "Show hidden files",
            checked = settings.sftpShowHiddenByDefault,
            detail = "Include dotfiles when a browser opens",
            onCheckedChange = { onSettingsChange(settings.copy(sftpShowHiddenByDefault = it)) }
        )
    }
}

internal fun ServerMetricColorPreset.label(): String {
    return when (this) {
        ServerMetricColorPreset.Theme -> "Theme"
        ServerMetricColorPreset.Custom -> "Custom"
        ServerMetricColorPreset.Classic -> "Blue / Green"
        ServerMetricColorPreset.Calm -> "Steel / Sage"
        ServerMetricColorPreset.Graphite -> "Graphite"
        ServerMetricColorPreset.HighContrast -> "Cobalt / Lime"
        ServerMetricColorPreset.Ocean -> "Ocean Teal"
        ServerMetricColorPreset.Forest -> "Olive / Teal"
        ServerMetricColorPreset.Ember -> "Clay / Moss"
        ServerMetricColorPreset.Aurora -> "Aurora"
        ServerMetricColorPreset.Orchid -> "Orchid"
        ServerMetricColorPreset.Nordic -> "Nordic"
        ServerMetricColorPreset.Solar -> "Solar"
        ServerMetricColorPreset.Circuit -> "Circuit"
        ServerMetricColorPreset.Harvest -> "Harvest"
        ServerMetricColorPreset.Lagoon -> "Lagoon"
        ServerMetricColorPreset.Metro -> "Metro"
        ServerMetricColorPreset.Mono -> "Mono"
    }
}

@Composable
private fun MetricColorPreview(
    preset: ServerMetricColorPreset,
    overrides: ServerMetricColorOverrides = ServerMetricColorOverrides()
) {
    val colors = metricColorsFor(preset, overrides)
    val items = listOf(
        "CPU" to colors.cpu,
        "RAM" to colors.memory,
        "Disk" to colors.disk,
        "Net" to colors.network,
        "Ping" to colors.latency
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (label, color) ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(13.dp))
                    .background(DeckColors.SurfaceRaised)
                    .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(13.dp))
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(label, color = DeckColors.PrimaryText, fontSize = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

private enum class PinDialogMode {
    Set,
    Change,
    Disable
}

@Composable
private fun SecuritySettings(
    settings: AppSettings,
    status: String?,
    biometricAvailable: Boolean,
    onSetPin: () -> Unit,
    onChangePin: () -> Unit,
    onToggleBiometric: () -> Unit,
    onDisablePin: () -> Unit,
    onLockNow: () -> Unit,
    backgroundUsageAllowed: Boolean,
    onRequestBackgroundUsage: () -> Unit
) {
    val enabled = appLockPinUsable(settings)
    SettingsCard("Security") {
        SettingsChoiceRow(
            title = "Background usage",
            detail = "Allow unrestricted battery use so SSH sessions stay connected",
            value = if (backgroundUsageAllowed) "Allowed" else "Allow",
            onClick = onRequestBackgroundUsage
        )
        Spacer(Modifier.height(10.dp))
        SettingsChoiceRow(
            title = "App lock",
            detail = if (enabled) "Locks after backgrounding; Lock Now tests it immediately" else "Set a PIN for app resume and launch",
            value = if (enabled) "On" else "Set PIN",
            onClick = if (enabled) onChangePin else onSetPin
        )
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (enabled) {
                SettingsActionButton("Lock Now", onLockNow, modifier = Modifier.fillMaxWidth())
                SettingsActionButton("Change PIN", onChangePin, modifier = Modifier.fillMaxWidth())
                SettingsActionButton(
                    text = if (settings.appLockBiometricEnabled) "Biometric On" else "Biometric Off",
                    enabled = biometricToggleEnabled(settings.appLockBiometricEnabled, biometricAvailable),
                    onClick = onToggleBiometric,
                    modifier = Modifier.fillMaxWidth()
                )
                SettingsActionButton("Turn Off", onDisablePin, modifier = Modifier.fillMaxWidth())
            } else {
                SettingsActionButton("Set PIN", onSetPin, modifier = Modifier.fillMaxWidth())
            }
        }
        if (showBiometricUnavailableMessage(enabled, settings.appLockBiometricEnabled, biometricAvailable)) {
            SettingsStatusMessage("Biometric unlock is unavailable on this device or no biometric is enrolled.")
        }
        status?.let { SettingsStatusMessage(it) }
    }
}

private fun AppSettings.headingFontPath(target: HeadingFontTarget): String? = when (target) {
    HeadingFontTarget.Home -> homeHeadingFontPath
    HeadingFontTarget.Connections -> connectionsHeadingFontPath
    HeadingFontTarget.Files -> filesHeadingFontPath
    HeadingFontTarget.Vault -> vaultHeadingFontPath
    HeadingFontTarget.Settings -> settingsHeadingFontPath
}

private fun AppSettings.withHeadingFontPath(target: HeadingFontTarget, path: String?): AppSettings = when (target) {
    HeadingFontTarget.Home -> copy(homeHeadingFontPath = path)
    HeadingFontTarget.Connections -> copy(connectionsHeadingFontPath = path)
    HeadingFontTarget.Files -> copy(filesHeadingFontPath = path)
    HeadingFontTarget.Vault -> copy(vaultHeadingFontPath = path)
    HeadingFontTarget.Settings -> copy(settingsHeadingFontPath = path)
}

private fun importHeadingFont(context: android.content.Context, uri: Uri, target: HeadingFontTarget): String {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/').ifBlank { "${target.name.lowercase()}.ttf" }
    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    require(extension == "ttf" || extension == "otf") { "Choose a .ttf or .otf font." }
    val dir = File(context.filesDir, "heading-fonts").apply { mkdirs() }
    val output = File(dir, "${target.name.lowercase()}.$extension")
    var total = 0L
    context.contentResolver.openInputStream(uri)?.use { input ->
        output.outputStream().use { out ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= 8L * 1024L * 1024L) { "Font is larger than 8 MB." }
                out.write(buffer, 0, read)
            }
        }
    } ?: error("Could not open selected font.")
    require(output.length() > 0L) { "Selected font was empty." }
    return output.absolutePath
}

@Composable
private fun HeadingFontRow(
    target: HeadingFontTarget,
    path: String?,
    onImport: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(target.label, color = DeckColors.PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(path?.let { File(it).name } ?: "Default heading font", color = DeckColors.SecondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsActionButton("Import", onImport)
            SettingsActionButton("Reset", onClear, enabled = path != null)
        }
    }
}

@Composable
private fun PinLockDialog(
    mode: PinDialogMode,
    hasExistingPin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (currentPin: String, nextPin: String) -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var nextPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val needsCurrent = hasExistingPin && mode != PinDialogMode.Set
    val needsNext = mode != PinDialogMode.Disable
    val pinError = if (needsNext) PinLockPolicy.validatePin(nextPin) else null
    val confirmEnabled = (!needsCurrent || currentPin.isNotBlank()) &&
        (!needsNext || (pinError == null && nextPin == confirmPin))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (mode) {
                    PinDialogMode.Set -> "Set app PIN"
                    PinDialogMode.Change -> "Change app PIN"
                    PinDialogMode.Disable -> "Turn off app lock"
                },
                color = DeckColors.PrimaryText,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column {
                if (needsCurrent) {
                    PinField("Current PIN", currentPin) { currentPin = it }
                    Spacer(Modifier.height(10.dp))
                }
                if (needsNext) {
                    Text("Use at least 6 digits.", color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    PinField("New PIN", nextPin) { nextPin = it }
                    Spacer(Modifier.height(10.dp))
                    PinField("Confirm PIN", confirmPin) { confirmPin = it }
                    if (nextPin.isNotBlank() && pinError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(pinError, color = DeckColors.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else if (confirmPin.isNotBlank() && nextPin != confirmPin) {
                        Spacer(Modifier.height(8.dp))
                        Text("PINs do not match.", color = DeckColors.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = confirmEnabled, onClick = { onConfirm(currentPin, nextPin) }) {
                Text(
                    when (mode) {
                        PinDialogMode.Set -> "Enable"
                        PinDialogMode.Change -> "Save"
                        PinDialogMode.Disable -> "Turn Off"
                    },
                    color = if (confirmEnabled) DeckColors.Cyan else DeckColors.SecondaryText,
                    fontWeight = FontWeight.Black
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DeckColors.SecondaryText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun PinField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(12)) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = settingsTextFieldColors(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        label = { Text(label) }
    )
}

@Composable
private fun settingsTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DeckColors.CardStroke,
    unfocusedBorderColor = DeckColors.CardStroke.copy(alpha = 0.72f),
    focusedTextColor = DeckColors.PrimaryText,
    unfocusedTextColor = DeckColors.PrimaryText,
    cursorColor = DeckColors.BrandAlt,
    focusedContainerColor = DeckColors.SurfaceMuted,
    unfocusedContainerColor = DeckColors.SurfaceMuted,
    focusedLabelColor = DeckColors.SecondaryText,
    unfocusedLabelColor = DeckColors.SecondaryText
)

@Composable
private fun BackupSettings(
    backupStatus: String?,
    onExport: () -> Unit,
    onEncryptedExport: () -> Unit,
    onCheckImport: () -> Unit,
    onImportHostLink: () -> Unit,
    onImportHostQr: () -> Unit,
    onImportSshConfig: () -> Unit,
    onImport: () -> Unit
) {
    SettingsCard("Backups") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionButton("Export Backup", onExport, modifier = Modifier.fillMaxWidth())
            SettingsActionButton("Export Encrypted", onEncryptedExport, modifier = Modifier.fillMaxWidth())
            SettingsActionButton("Check Import", onCheckImport, modifier = Modifier.fillMaxWidth())
            SettingsActionButton("Import Link", onImportHostLink, modifier = Modifier.fillMaxWidth())
            SettingsActionButton("Import QR", onImportHostQr, modifier = Modifier.fillMaxWidth())
            SettingsActionButton("Import SSH Config", onImportSshConfig, modifier = Modifier.fillMaxWidth())
            SettingsActionButton("Import", onImport, modifier = Modifier.fillMaxWidth())
        }
        backupStatus?.let { SettingsStatusMessage(it) }
    }
}

@Composable
private fun HostShareLinkImportDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var payload by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import host link", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            OutlinedTextField(
                value = payload,
                onValueChange = { payload = it },
                singleLine = false,
                shape = RoundedCornerShape(18.dp),
                colors = settingsTextFieldColors(),
                label = { Text("chronossh://host?...") }
            )
        },
        confirmButton = {
            TextButton(enabled = payload.isNotBlank(), onClick = { onImport(payload) }) {
                Text("Import", color = if (payload.isBlank()) DeckColors.SecondaryText else DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DeckColors.SecondaryText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun BackupPassphraseDialog(
    title: String,
    action: String,
    requireConfirmation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val confirmEnabled = backupPassphraseConfirmEnabled(passphrase, confirmation, requireConfirmation)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = settingsTextFieldColors(),
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Backup passphrase") }
                )
                if (requireConfirmation) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = settingsTextFieldColors(),
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Confirm passphrase") }
                    )
                    if (confirmation.isNotBlank() && passphrase != confirmation) {
                        Spacer(Modifier.height(8.dp))
                        Text("Passphrases do not match.", color = DeckColors.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = confirmEnabled, onClick = { onConfirm(passphrase) }) {
                Text(action, color = if (confirmEnabled) DeckColors.Cyan else DeckColors.SecondaryText, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DeckColors.SecondaryText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

internal fun backupPassphraseConfirmEnabled(
    passphrase: String,
    confirmation: String,
    requireConfirmation: Boolean
): Boolean {
    if (passphrase.isBlank()) return false
    return !requireConfirmation || passphrase == confirmation
}

@Composable
private fun AboutDiagnosticsSettings(
    diagnostics: List<ConnectionEvent>,
    crashLogs: List<CrashLogEntry>,
    diagnosticsOpen: Boolean,
    onDiagnosticsOpenChange: (Boolean) -> Unit,
    onClearCrashLogs: () -> Unit
) {
    SettingsCard("About") {
        SettingsRow("chronoSSH", "Package com.chrono.ssh", "App", showBadge = true)
        SettingsRow("Diagnostics", "${diagnostics.size} recorded events", "Log", showBadge = true)
        SettingsRow("System crashes", "${crashLogs.size} captured crashes", "Crash", showBadge = true)
        Spacer(Modifier.height(12.dp))
        SettingsActionButton(if (diagnosticsOpen) "Hide Diagnostics" else "Show Diagnostics", onClick = {
            onDiagnosticsOpenChange(!diagnosticsOpen)
        })
        if (diagnosticsOpen) {
            Spacer(Modifier.height(14.dp))
            CrashLogsPanel(crashLogs, onClearCrashLogs)
            Spacer(Modifier.height(14.dp))
            DiagnosticsPanel(diagnostics)
        }
    }
}

enum class SettingsSelectionPage {
    AppTheme,
    TerminalTheme,
    TerminalFont,
    MetricColors,
    MetricsPage
}

@Composable
private fun SettingsSelectionScreen(
    page: SettingsSelectionPage,
    themeMode: DeckThemeMode,
    themeFamilyId: String,
    settings: AppSettings,
    onBack: () -> Unit,
    onThemeFamilyChange: (String) -> Unit,
    onSettingsChange: (AppSettings) -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    var stagedAppThemeId by remember(page, themeFamilyId) { mutableStateOf(themeFamilyId) }
    var stagedTerminalThemeName by remember(page, settings.terminalThemeName) { mutableStateOf(settings.terminalThemeName) }
    var stagedTerminalFontFamily by remember(page, settings.terminalFontFamily) { mutableStateOf(settings.terminalFontFamily) }
    var stagedMetricColorPreset by remember(page, settings.serverMetricColorPreset) { mutableStateOf(settings.serverMetricColorPreset) }
    var stagedMetricColorOverrides by remember(page, settings) { mutableStateOf(metricColorOverridesFrom(settings)) }
    var stagedMetricsCardOrder by remember(page, settings.serverDetailCardOrder) { mutableStateOf(ServerDetailCard.sanitizeOrderCsv(settings.serverDetailCardOrder)) }
    var stagedMetricsHiddenCards by remember(page, settings.serverDetailHiddenCards) { mutableStateOf(ServerDetailCard.sanitizeHiddenCsv(settings.serverDetailHiddenCards)) }
    fun applySelection() {
        settingsAfterSelection(
            page = page,
            settings = settings,
            appThemeId = stagedAppThemeId,
            terminalThemeName = stagedTerminalThemeName,
            terminalFontFamily = stagedTerminalFontFamily,
            metricColorPreset = stagedMetricColorPreset,
            metricColorOverrides = stagedMetricColorOverrides,
            metricsCardOrder = stagedMetricsCardOrder,
            metricsHiddenCards = stagedMetricsHiddenCards
        ).also { result ->
            result.themeFamilyId?.let(onThemeFamilyChange)
            result.settings?.let(onSettingsChange)
        }
    }
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SelectorIconButton("X", onBack)
            Text(
                text = when (page) {
                    SettingsSelectionPage.AppTheme -> "Select app theme"
                    SettingsSelectionPage.TerminalTheme -> "Select scheme"
                    SettingsSelectionPage.TerminalFont -> "Select typeface"
                    SettingsSelectionPage.MetricColors -> "Server card metric colors"
                    SettingsSelectionPage.MetricsPage -> "Metrics Page"
                },
                color = DeckColors.PrimaryText,
                fontSize = 22.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            SelectorIconButton("✓", ::applySelection)
        }
        if (page == SettingsSelectionPage.TerminalTheme || page == SettingsSelectionPage.TerminalFont) {
            Spacer(Modifier.height(22.dp))
            TerminalSelectorPreview(
                theme = TerminalCatalog.theme(stagedTerminalThemeName),
                fontName = stagedTerminalFontFamily
            )
        }
        Spacer(Modifier.height(18.dp))
        when (page) {
            SettingsSelectionPage.AppTheme -> {
                val families = DeckThemeCatalog.familiesFor(themeMode, systemDark)
                LaunchedEffect(page, themeMode, systemDark) {
                    if (families.none { it.id == stagedAppThemeId }) {
                        stagedAppThemeId = families.firstOrNull()?.id ?: DeckThemeCatalog.DEFAULT_FAMILY_ID
                    }
                }
                families.forEach { family ->
                    ThemeFamilyRow(
                        family = family,
                        selected = family.id == stagedAppThemeId,
                        onClick = { stagedAppThemeId = family.id }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            SettingsSelectionPage.TerminalTheme -> {
                TerminalCatalog.themes.forEach { theme ->
                    TerminalThemeRow(
                        theme = theme,
                        selected = theme.name == stagedTerminalThemeName,
                        onClick = { stagedTerminalThemeName = theme.name }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            SettingsSelectionPage.TerminalFont -> {
                TerminalCatalog.fonts.forEach { font ->
                    TerminalFontRow(
                        name = font.name,
                        selected = font.name == stagedTerminalFontFamily,
                        onClick = { stagedTerminalFontFamily = font.name }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            SettingsSelectionPage.MetricColors -> {
                ServerMetricColorPreset.entries.forEach { preset ->
                    MetricColorPresetRow(
                        preset = preset,
                        selected = preset == stagedMetricColorPreset,
                        overrides = stagedMetricColorOverrides,
                        onClick = {
                            stagedMetricColorPreset = preset
                            if (preset == ServerMetricColorPreset.Custom) {
                                stagedMetricColorOverrides = customMetricColorOverridesForSelection(stagedMetricColorOverrides)
                            }
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    if (preset == ServerMetricColorPreset.Custom && stagedMetricColorPreset == ServerMetricColorPreset.Custom) {
                        MetricColorOverrideEditor(
                            overrides = stagedMetricColorOverrides,
                            onChange = { stagedMetricColorOverrides = it }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (stagedMetricColorPreset == ServerMetricColorPreset.Theme) {
                    SettingsActionButton(
                        text = "Override theme colors",
                        onClick = {
                            stagedMetricColorPreset = ServerMetricColorPreset.Custom
                            stagedMetricColorOverrides = customMetricColorOverridesForSelection(stagedMetricColorOverrides)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            SettingsSelectionPage.MetricsPage -> {
                val hidden = ServerDetailCard.hiddenSet(stagedMetricsHiddenCards)
                val ordered = ServerDetailCard.ordered(stagedMetricsCardOrder)
                ordered.forEachIndexed { index, card ->
                    MetricsPageCardRow(
                        card = card,
                        visible = card !in hidden,
                        canMoveUp = index > 0,
                        canMoveDown = index < ordered.lastIndex,
                        onVisibleChange = { visible ->
                            stagedMetricsHiddenCards = settingsMetricsHiddenCardsAfterToggle(stagedMetricsHiddenCards, card, visible)
                        },
                        onMove = { delta ->
                            stagedMetricsCardOrder = settingsMetricsCardOrderAfterMove(stagedMetricsCardOrder, card, delta)
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

internal data class SettingsSelectionResult(
    val themeFamilyId: String? = null,
    val settings: AppSettings? = null
)

internal fun settingsAfterSelection(
    page: SettingsSelectionPage,
    settings: AppSettings,
    appThemeId: String,
    terminalThemeName: String,
    terminalFontFamily: String,
    metricColorPreset: ServerMetricColorPreset,
    metricColorOverrides: ServerMetricColorOverrides = ServerMetricColorOverrides(),
    metricsCardOrder: String = settings.serverDetailCardOrder,
    metricsHiddenCards: String = settings.serverDetailHiddenCards
): SettingsSelectionResult {
    return when (page) {
        SettingsSelectionPage.AppTheme -> SettingsSelectionResult(themeFamilyId = appThemeId)
        SettingsSelectionPage.TerminalTheme -> SettingsSelectionResult(settings = settings.copy(terminalThemeName = terminalThemeName))
        SettingsSelectionPage.TerminalFont -> SettingsSelectionResult(settings = settings.copy(terminalFontFamily = terminalFontFamily))
        SettingsSelectionPage.MetricColors -> {
            val savedOverrides = if (metricColorPreset == ServerMetricColorPreset.Custom) {
                customMetricColorOverridesForSelection(metricColorOverrides)
            } else {
                metricColorOverrides
            }
            SettingsSelectionResult(settings = settings.copy(
                serverMetricColorPreset = metricColorPreset,
                serverMetricCpuColorHex = savedOverrides.cpuHex,
                serverMetricMemoryColorHex = savedOverrides.memoryHex,
                serverMetricDiskColorHex = savedOverrides.diskHex,
                serverMetricNetworkColorHex = savedOverrides.networkHex,
                serverMetricLatencyColorHex = savedOverrides.latencyHex
            ))
        }
        SettingsSelectionPage.MetricsPage -> SettingsSelectionResult(settings = settings.copy(
            serverDetailCardOrder = ServerDetailCard.sanitizeOrderCsv(metricsCardOrder),
            serverDetailHiddenCards = ServerDetailCard.sanitizeHiddenCsv(metricsHiddenCards)
        ))
    }
}

internal fun customMetricColorOverridesForSelection(
    current: ServerMetricColorOverrides
): ServerMetricColorOverrides {
    val currentCpu = sanitizeColorHex(current.cpuHex)
    val currentMemory = sanitizeColorHex(current.memoryHex)
    val currentDisk = sanitizeColorHex(current.diskHex)
    val currentNetwork = sanitizeColorHex(current.networkHex)
    val currentLatency = sanitizeColorHex(current.latencyHex)
    if (currentCpu != null && currentMemory != null && currentDisk != null && currentNetwork != null && currentLatency != null) {
        return ServerMetricColorOverrides(currentCpu, currentMemory, currentDisk, currentNetwork, currentLatency)
    }
    val themeColors = metricColorsFor(ServerMetricColorPreset.Theme)
    return ServerMetricColorOverrides(
        cpuHex = currentCpu ?: metricColorHex(themeColors.cpu),
        memoryHex = currentMemory ?: metricColorHex(themeColors.memory),
        diskHex = currentDisk ?: metricColorHex(themeColors.disk),
        networkHex = currentNetwork ?: metricColorHex(themeColors.network),
        latencyHex = currentLatency ?: metricColorHex(themeColors.latency)
    )
}

@Composable
private fun MetricsPageCardRow(
    card: ServerDetailCard,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit
) {
    DeckCard(
        modifier = Modifier.fillMaxWidth(),
        radius = 22.dp,
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SettingsToggleRow(
                    title = card.label,
                    checked = visible,
                    detail = if (visible) "Shown" else "Hidden",
                    onCheckedChange = onVisibleChange
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                SettingsActionButton("^", onClick = { onMove(-1) }, enabled = canMoveUp)
                Spacer(Modifier.height(6.dp))
                SettingsActionButton("v", onClick = { onMove(1) }, enabled = canMoveDown)
            }
        }
    }
}

internal fun settingsMetricsHiddenCardsAfterToggle(csv: String, card: ServerDetailCard, visible: Boolean): String {
    val hidden = ServerDetailCard.hiddenSet(csv).toMutableSet()
    if (visible) hidden.remove(card) else hidden.add(card)
    return hidden.joinToString(",") { it.id }
}

internal fun settingsMetricsCardOrderAfterMove(csv: String, card: ServerDetailCard, delta: Int): String {
    val ordered = ServerDetailCard.ordered(csv).toMutableList()
    val from = ordered.indexOf(card)
    val to = (from + delta).coerceIn(0, ordered.lastIndex)
    if (from < 0 || from == to) return ServerDetailCard.sanitizeOrderCsv(csv)
    ordered.removeAt(from)
    ordered.add(to, card)
    return ordered.joinToString(",") { it.id }
}

@Composable
private fun MetricColorPresetRow(
    preset: ServerMetricColorPreset,
    selected: Boolean,
    overrides: ServerMetricColorOverrides = ServerMetricColorOverrides(),
    onClick: () -> Unit
) {
    DeckCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        radius = 20.dp,
        padding = PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(preset.label(), color = DeckColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(preset.roleSummary(), color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(8.dp))
                MetricColorPreview(preset, overrides)
            }
            Spacer(Modifier.width(12.dp))
            MetricPresetSelectionMark(selected)
        }
    }
}

@Composable
private fun MetricColorOverrideEditor(
    overrides: ServerMetricColorOverrides,
    onChange: (ServerMetricColorOverrides) -> Unit
) {
    val theme = metricColorsFor(ServerMetricColorPreset.Theme)
    val choices = listOf(
        metricColorHex(theme.cpu) to theme.cpu,
        metricColorHex(theme.memory) to theme.memory,
        metricColorHex(theme.disk) to theme.disk,
        metricColorHex(theme.network) to theme.network,
        metricColorHex(theme.latency) to theme.latency,
        "#111111" to Color(0xff111111),
        "#444444" to Color(0xff444444),
        "#777777" to Color(0xff777777),
        "#FFFFFF" to Color.White,
        "#2563EB" to Color(0xff2563eb),
        "#059669" to Color(0xff059669),
        "#D97706" to Color(0xffd97706),
        "#7C3AED" to Color(0xff7c3aed),
        "#DB2777" to Color(0xffdb2777),
        "#0891B2" to Color(0xff0891b2)
    ).distinctBy { it.first }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricColorOverrideRow("CPU", overrides.cpuHex ?: metricColorHex(theme.cpu), choices) { onChange(overrides.copy(cpuHex = it)) }
        MetricColorOverrideRow("RAM", overrides.memoryHex ?: metricColorHex(theme.memory), choices) { onChange(overrides.copy(memoryHex = it)) }
        MetricColorOverrideRow("Disk", overrides.diskHex ?: metricColorHex(theme.disk), choices) { onChange(overrides.copy(diskHex = it)) }
        MetricColorOverrideRow("Network", overrides.networkHex ?: metricColorHex(theme.network), choices) { onChange(overrides.copy(networkHex = it)) }
        MetricColorOverrideRow("Latency", overrides.latencyHex ?: metricColorHex(theme.latency), choices) { onChange(overrides.copy(latencyHex = it)) }
    }
}

@Composable
private fun MetricColorOverrideRow(
    label: String,
    selectedHex: String,
    choices: List<Pair<String, Color>>,
    onSelect: (String) -> Unit
) {
    var draftHex by remember(selectedHex) { mutableStateOf(selectedHex) }
    val cleanHex = sanitizeMetricColorInput(selectedHex)
    val previewColor = cleanHex?.let { metricColorsFor(ServerMetricColorPreset.Custom, ServerMetricColorOverrides(cpuHex = it)).cpu }
        ?: Color.Transparent
    val draftValid = sanitizeMetricColorInput(draftHex) != null
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 18.dp, padding = PaddingValues(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label, color = DeckColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(70.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(previewColor)
                        .border(1.dp, DeckColors.CardStroke, CircleShape)
                )
                OutlinedTextField(
                    value = draftHex,
                    onValueChange = { value ->
                        draftHex = value.take(7)
                        sanitizeMetricColorInput(draftHex)?.let(onSelect)
                    },
                    singleLine = true,
                    label = { Text("Hex") },
                    isError = !draftValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeckColors.PrimaryText,
                        unfocusedTextColor = DeckColors.PrimaryText,
                        focusedContainerColor = DeckColors.SurfaceRaised,
                        unfocusedContainerColor = DeckColors.SurfaceRaised,
                        focusedBorderColor = DeckColors.Cyan,
                        unfocusedBorderColor = DeckColors.CardStroke,
                        focusedLabelColor = DeckColors.Cyan,
                        unfocusedLabelColor = DeckColors.SecondaryText
                    )
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                choices.forEach { (hex, color) ->
                    MetricColorSwatch(
                        label = label,
                        hex = hex,
                        color = color,
                        selected = hex.equals(selectedHex, ignoreCase = true),
                        onSelect = onSelect
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColorSwatch(
    label: String,
    hex: String,
    color: Color,
    selected: Boolean,
    onSelect: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) DeckColors.PrimaryText else DeckColors.CardStroke,
                shape = CircleShape
            )
            .clickable { onSelect(hex) }
            .semantics { contentDescription = "$label color $hex" }
    )
}

internal fun sanitizeMetricColorInput(value: String): String? =
    sanitizeColorHex(value.take(7))

@Composable
private fun MetricPresetSelectionMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DeckColors.SurfaceRaised else Color.Transparent)
            .border(1.dp, DeckColors.CardStroke.copy(alpha = if (selected) 0.72f else 0.32f), RoundedCornerShape(8.dp))
            .semantics { contentDescription = metricPresetSelectionContentDescription(selected) },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Canvas(Modifier.size(13.dp)) {
                drawLine(
                    DeckColors.Cyan,
                    androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.54f),
                    androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.78f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    DeckColors.Cyan,
                    androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.78f),
                    androidx.compose.ui.geometry.Offset(size.width * 0.86f, size.height * 0.22f),
                    strokeWidth = 2.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

internal fun metricPresetSelectionContentDescription(selected: Boolean): String =
    if (selected) "Selected metric color preset" else "Metric color preset"

internal fun ServerMetricColorPreset.roleSummary(): String {
    return when (this) {
        ServerMetricColorPreset.Theme -> "Uses the active app theme accent colors"
        ServerMetricColorPreset.Custom -> "Manual CPU, RAM, disk, network and latency colors"
        ServerMetricColorPreset.Classic -> "CPU/Net blue · RAM green · Disk amber"
        ServerMetricColorPreset.Calm -> "CPU steel · RAM sage · Disk tan · Net teal"
        ServerMetricColorPreset.Graphite -> "Muted neutral rings for low-contrast themes"
        ServerMetricColorPreset.HighContrast -> "Higher separation without neon accents"
        ServerMetricColorPreset.Ocean -> "Blue CPU · green RAM · amber disk · cyan network"
        ServerMetricColorPreset.Forest -> "Olive CPU · teal RAM/network · warm disk"
        ServerMetricColorPreset.Ember -> "Clay CPU · moss RAM · brass disk · cool network"
        ServerMetricColorPreset.Aurora -> "Blue CPU · emerald RAM · orange disk · violet network"
        ServerMetricColorPreset.Orchid -> "Violet CPU · magenta RAM · ochre disk · cyan network"
        ServerMetricColorPreset.Nordic -> "Frost blue CPU · sage RAM · copper disk · ice network"
        ServerMetricColorPreset.Solar -> "Blue CPU · green RAM · yellow disk · red network"
        ServerMetricColorPreset.Circuit -> "Teal CPU · lime RAM · amber disk · indigo network"
        ServerMetricColorPreset.Harvest -> "Ochre CPU · olive RAM · brass disk · teal network"
        ServerMetricColorPreset.Lagoon -> "Sky CPU · emerald RAM · violet disk · cyan network"
        ServerMetricColorPreset.Metro -> "Blue CPU · magenta RAM · orange disk · green network"
        ServerMetricColorPreset.Mono -> "Single-family neutral rings"
    }
}

@Composable
private fun DiagnosticsPanel(diagnostics: List<ConnectionEvent>) {
    if (diagnostics.isEmpty()) {
        Text("No diagnostic events have been recorded yet.", color = DeckColors.SecondaryText, fontSize = 13.sp)
    } else {
        val clipboard = LocalClipboardManager.current
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Connection diagnostics", color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
            SettingsActionButton("Copy All", onClick = {
                clipboard.setText(AnnotatedString(connectionDiagnosticsCopyText(diagnostics)))
            })
        }
        Spacer(Modifier.height(8.dp))
        val latestVnStat = diagnostics.firstOrNull { it.message.startsWith("vnStat:") }
        latestVnStat?.let { event ->
            DeckCard(modifier = Modifier.fillMaxWidth(), radius = 22.dp, padding = PaddingValues(14.dp)) {
                Text("vnStat", color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text(event.message.removePrefix("vnStat:").trim(), color = DeckColors.SecondaryText, fontSize = 13.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        diagnostics.take(18).forEach { event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { clipboard.setText(AnnotatedString(event.copyText())) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(event.level.diagnosticColor())
                )
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        event.message,
                        color = DeckColors.PrimaryText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (event.message.startsWith("vnStat:")) 4 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${event.level.name.lowercase()} · ${event.serverId}", color = DeckColors.SecondaryText, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

internal fun connectionDiagnosticsCopyText(diagnostics: List<ConnectionEvent>): String {
    return diagnostics.joinToString(separator = "\n\n") { it.copyText() }
}

internal fun ConnectionEvent.copyText(): String {
    return buildString {
        appendLine(level.name)
        appendLine(serverId)
        appendLine(atEpochMillis)
        append(message)
    }
}

@Composable
private fun CrashLogsPanel(crashLogs: List<CrashLogEntry>, onClearCrashLogs: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("System crashes", color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
        SettingsActionButton("Clear", onClick = { onClearCrashLogs() })
    }
    Spacer(Modifier.height(8.dp))
    if (crashLogs.isEmpty()) {
        Text("No system crashes captured yet.", color = DeckColors.SecondaryText, fontSize = 13.sp)
        return
    }
    crashLogs.take(8).forEach { crash ->
        DeckCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { clipboard.setText(AnnotatedString(crashLogCopyText(crash))) },
            radius = 18.dp,
            padding = PaddingValues(12.dp)
        ) {
            Text(
                crash.throwableClass.substringAfterLast('.'),
                color = DeckColors.Red,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${crash.threadName}  ${crash.atEpochMillis}",
                color = DeckColors.SecondaryText,
                fontSize = 11.sp,
                maxLines = 1
            )
            if (crash.message.isNotBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(crash.message, color = DeckColors.PrimaryText, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Text(crash.stackTrace, color = DeckColors.SecondaryText, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 10, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text("Tap to copy full crash log", color = DeckColors.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
    }
}

internal fun crashLogCopyText(crash: CrashLogEntry): String {
    return buildString {
        appendLine(crash.throwableClass)
        appendLine(crash.threadName)
        appendLine(crash.atEpochMillis)
        if (crash.message.isNotBlank()) appendLine(crash.message)
        appendLine()
        append(crash.stackTrace)
    }
}

@Composable
private fun SettingsRow(title: String, detail: String, badge: String, showBadge: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DeckColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (detail.isNotBlank()) {
                Text(detail, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (showBadge) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(DeckColors.SurfaceMuted)
                    .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(14.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(badge, color = DeckColors.PrimaryText, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(title: String, checked: Boolean, detail: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DeckColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (detail.isNotBlank()) {
                Text(detail, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 26.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (checked) DeckColors.SurfaceRaised else DeckColors.SurfaceMuted)
                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (checked) DeckColors.Green else DeckColors.SecondaryText)
            )
        }
    }
}

@Composable
private fun SettingsStepperRow(
    title: String,
    detail: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DeckColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (detail.isNotBlank()) {
                Text(detail, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(DeckColors.SurfaceMuted)
                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepperButton("-", canDecrease, onDecrease)
            Text(value, color = DeckColors.PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp))
            StepperButton("+", canIncrease, onIncrease)
        }
    }
}

@Composable
private fun StepperButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) DeckColors.PrimaryText else DeckColors.TertiaryText, fontSize = 17.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SettingsChoiceRow(title: String, detail: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DeckColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SettingsSectionRow(title: String, detail: String, value: String, onClick: () -> Unit) {
    DeckCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        radius = 28.dp,
        padding = PaddingValues(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = DeckColors.PrimaryText, fontSize = 21.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text(detail, color = DeckColors.SecondaryText, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (title == SettingsSection.AboutDiagnostics.title) {
                Spacer(Modifier.size(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(value, color = DeckColors.PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SettingsStatusMessage(message: String) {
    Spacer(Modifier.height(12.dp))
    val isError = message.contains("failed", ignoreCase = true) || message.contains("malformed", ignoreCase = true)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isError) DeckColors.Red.copy(alpha = 0.10f) else DeckColors.Green.copy(alpha = 0.10f))
            .border(
                1.dp,
                if (isError) DeckColors.Red.copy(alpha = 0.22f) else DeckColors.Green.copy(alpha = 0.22f),
                RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(message, color = DeckColors.PrimaryText, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) DeckColors.SurfaceMuted else DeckColors.Surface)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(text, color = if (enabled) DeckColors.PrimaryText else DeckColors.SecondaryText, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

private fun ConnectionEventLevel.diagnosticColor(): Color {
    return when (this) {
        ConnectionEventLevel.Info -> DeckColors.Cyan
        ConnectionEventLevel.Success -> DeckColors.Green
        ConnectionEventLevel.Warning -> DeckColors.Orange
        ConnectionEventLevel.Error -> DeckColors.Red
    }
}

@Composable
private fun ThemeFamilyRow(
    family: DeckThemeFamily,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PalettePreview(family)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(family.name, color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text(family.description, color = DeckColors.SecondaryText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(if (selected) "ACTIVE" else "USE", color = if (selected) DeckColors.BrandAlt else DeckColors.TertiaryText, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TerminalThemeRow(
    theme: com.chrono.ssh.ui.terminal.TerminalThemeSpec,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) DeckColors.SurfaceMuted else DeckColors.SurfaceRaised)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(terminalThemePreviewColor(theme.backgroundHex, Color(0xFF070A12)))
                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            Text(
                "$ ls",
                color = terminalThemePreviewColor(theme.foregroundHex, Color(0xFFE8EDF8)),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(width = 16.dp, height = 3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(terminalThemePreviewColor(theme.cursorHex, Color(0xFF21C7E8)))
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(theme.name, color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("Foreground, background and cursor preview", color = DeckColors.SecondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(if (selected) "ACTIVE" else "USE", color = if (selected) DeckColors.BrandAlt else DeckColors.TertiaryText, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TerminalFontRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) DeckColors.SurfaceMuted else DeckColors.SurfaceRaised)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 68.dp, height = 44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DeckColors.TerminalPanel)
                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("Aa", color = DeckColors.TerminalAccent, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
            Text("Used only inside terminal sessions", color = DeckColors.SecondaryText, fontSize = 13.sp)
        }
        Text(if (selected) "ACTIVE" else "USE", color = if (selected) DeckColors.BrandAlt else DeckColors.TertiaryText, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SelectorIconButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = DeckColors.PrimaryText, fontSize = 25.sp, lineHeight = 25.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TerminalSelectorPreview(
    theme: com.chrono.ssh.ui.terminal.TerminalThemeSpec,
    fontName: String
) {
    val foreground = terminalThemePreviewColor(theme.foregroundHex, Color(0xFFE8EDF8))
    val background = terminalThemePreviewColor(theme.backgroundHex, Color(0xFF070A12))
    val cursor = terminalThemePreviewColor(theme.cursorHex, Color(0xFF21C7E8))
    Column {
        Text("Terminal Preview", color = DeckColors.Cyan, fontSize = 14.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(270.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(background)
                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Column {
                Text("RM(1) User Commands RM(1)", color = foreground, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("NAME", color = foreground.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black)
                Text("rm - remove files or directories", color = foreground, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text("SYNOPSIS", color = foreground.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black)
                Text("rm [OPTION]... FILE...", color = foreground, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Text("DESCRIPTION", color = foreground.copy(alpha = 0.85f), fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black)
                Text(
                    "This manual page documents the GNU version of rm. The terminal preview uses $fontName with the selected palette.",
                    color = foreground,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(width = 24.dp, height = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(cursor)
            )
        }
    }
}

private fun terminalThemePreviewColor(value: String, fallback: Color): Color {
    return runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)
}

@Composable
private fun PalettePreview(family: DeckThemeFamily) {
    Box(Modifier.size(width = 62.dp, height = 40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(62.dp, 40.dp)) {
            drawRoundRect(color = family.light.background, size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f))
            drawCircle(family.light.cyan, radius = 9.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.5f))
            drawCircle(family.light.orange, radius = 9.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.5f))
            drawCircle(family.dark.background, radius = 9.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.5f))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 32.dp, padding = PaddingValues(20.dp)) {
        Text(title, color = DeckColors.PrimaryText, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(14.dp))
        content()
    }
}
