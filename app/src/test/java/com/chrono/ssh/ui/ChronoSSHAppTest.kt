package com.chrono.ssh.ui

import androidx.lifecycle.Lifecycle
import com.chrono.ssh.core.model.CrashLogEntry
import com.chrono.ssh.core.model.AppSettings
import com.chrono.ssh.core.service.CommandResult
import com.chrono.ssh.core.service.PinLockPolicy
import com.chrono.ssh.core.service.SshSession
import com.chrono.ssh.core.model.ConnectionEventLevel
import com.chrono.ssh.ui.screens.TerminalWorkspaceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronoSSHAppTest {
    @Test
    fun terminalMetricsSessionEligibilityRequiresMatchingConnectedSession() {
        assertTrue(
            terminalMetricsSessionEligible(
                workspaceServerId = "server-1",
                requestedServerId = "server-1",
                connected = true,
                hasSession = true
            )
        )
        assertFalse(terminalMetricsSessionEligible("server-2", "server-1", connected = true, hasSession = true))
        assertFalse(terminalMetricsSessionEligible("server-1", "server-1", connected = false, hasSession = true))
        assertFalse(terminalMetricsSessionEligible("server-1", "server-1", connected = true, hasSession = false))
    }

    @Test
    fun terminalMetricsSessionLoopKeyTracksEligibleActiveHosts() {
        val connected = TerminalWorkspaceState("server-2", engineFactory = { error("unused") }).apply {
            session = FakeSshSession("session-2", "server-2")
        }
        val disconnected = TerminalWorkspaceState("server-1", engineFactory = { error("unused") }).apply {
            session = null
        }
        val pending = TerminalWorkspaceState("server-3", engineFactory = { error("unused") })

        assertEquals("server-2", terminalMetricsSessionLoopKey(listOf(disconnected, connected, pending)))
    }

    @Test
    fun foregroundTerminalServiceStaysRunningForBackgroundSessions() {
        assertFalse(terminalForegroundServiceShouldRun(0))
        assertTrue(terminalForegroundServiceShouldRun(1))
        assertTrue(terminalForegroundServiceShouldRun(2))
    }

    @Test
    fun foregroundServiceStaysRunningForAnyActiveConnection() {
        assertFalse(foregroundServiceShouldRun(0))
        assertTrue(foregroundServiceShouldRun(1))
        assertTrue(foregroundServiceShouldRun(3))
    }

    @Test
    fun foregroundResumeReconnectProbeOnlyTargetsLostConnectedSessions() {
        assertTrue(terminalShouldProbeReconnectOnForeground(connected = true, hasSession = false))
        assertFalse(terminalShouldProbeReconnectOnForeground(connected = true, hasSession = true))
        assertFalse(terminalShouldProbeReconnectOnForeground(connected = false, hasSession = false))
    }

    @Test
    fun backgroundUsagePromptShowsProactivelyWhenNotAllowed() {
        assertFalse(backgroundUsagePromptVisible(activeConnectionCount = 0, backgroundUsageAllowed = true))
        assertFalse(backgroundUsagePromptVisible(activeConnectionCount = 0, backgroundUsageAllowed = false))
    }

    @Test
    fun backgroundUsagePromptHidesWhenAllowed() {
        assertFalse(backgroundUsagePromptVisible(activeConnectionCount = 1, backgroundUsageAllowed = true))
        assertTrue(backgroundUsagePromptVisible(activeConnectionCount = 1, backgroundUsageAllowed = false))
    }

    @Test
    fun notificationPromptOnlyShowsForActiveSessionsWhenNotAllowed() {
        assertFalse(notificationPermissionPromptVisible(activeConnectionCount = 0, notificationAllowed = false))
        assertFalse(notificationPermissionPromptVisible(activeConnectionCount = 1, notificationAllowed = true))
        assertTrue(notificationPermissionPromptVisible(activeConnectionCount = 1, notificationAllowed = false))
    }

    @Test
    fun appRelocksOnlyAfterBackgroundWhenPinIsEnabledAndUnlocked() {
        assertTrue(appShouldRelockOnResume(hasAppLockPin = true, wasBackgrounded = true, currentlyLocked = false))
        assertFalse(appShouldRelockOnResume(hasAppLockPin = false, wasBackgrounded = true, currentlyLocked = false))
        assertFalse(appShouldRelockOnResume(hasAppLockPin = true, wasBackgrounded = false, currentlyLocked = false))
        assertFalse(appShouldRelockOnResume(hasAppLockPin = true, wasBackgrounded = true, currentlyLocked = true))
    }

    @Test
    fun enablingAppLockFromSettingsDoesNotImmediatelyLockCurrentSession() {
        assertFalse(appLockStateAfterSettingsChange(wasEnabled = false, isEnabled = true, currentlyLocked = false))
        assertFalse(appLockStateAfterSettingsChange(wasEnabled = true, isEnabled = false, currentlyLocked = true))
        assertTrue(appLockStateAfterSettingsChange(wasEnabled = true, isEnabled = true, currentlyLocked = true))
    }

    @Test
    fun lockNowRequiresUsablePersistedPin() {
        assertTrue(appLockStateAfterLockNow(hasAppLockPin = true))
        assertFalse(appLockStateAfterLockNow(hasAppLockPin = false))
    }

    @Test
    fun appLockStartsLockedWhenPersistedPinExists() {
        assertTrue(initialAppLockState(hasPersistedPin = true))
        assertFalse(initialAppLockState(hasPersistedPin = false))
    }

    @Test
    fun appLockOnlyUsesValidPersistedPinMaterial() {
        val pin = PinLockPolicy.hashPin("123456", salt = "MTIzNDU2Nzg5MGFiY2RlZg")

        assertTrue(
            appLockPinUsable(
                AppSettings(
                    themeModeName = "System",
                    themeFamilyId = "default",
                    appLockPinHash = pin.hash,
                    appLockPinSalt = pin.salt
                )
            )
        )
        assertFalse(
            appLockPinUsable(
                AppSettings(
                    themeModeName = "System",
                    themeFamilyId = "default",
                    appLockPinHash = "hash",
                    appLockPinSalt = "bad salt"
                )
            )
        )
    }

    @Test
    fun startupSettingsClearCorruptAppLockBeforeInitialLockState() {
        val settings = startupSettings(
            AppSettings(
                themeModeName = "System",
                themeFamilyId = "default",
                appLockPinHash = "hash",
                appLockPinSalt = "not-base64",
                appLockBiometricEnabled = true
            )
        )

        assertNull(settings.appLockPinHash)
        assertNull(settings.appLockPinSalt)
        assertFalse(settings.appLockBiometricEnabled)
        assertFalse(appLockPinUsable(settings))
    }

    @Test
    fun startupSettingsDisableUsableAppLockAfterLockCrashBeforeInitialState() {
        val pin = PinLockPolicy.hashPin("123456", salt = "MTIzNDU2Nzg5MGFiY2RlZg")
        val settings = startupSettingsAfterCrashRecovery(
            settings = AppSettings(
                themeModeName = "System",
                themeFamilyId = "default",
                appLockPinHash = pin.hash,
                appLockPinSalt = pin.salt,
                appLockBiometricEnabled = true
            ),
            crashes = listOf(
                CrashLogEntry(
                    id = "crash",
                    atEpochMillis = 9_900L,
                    threadName = "main",
                    throwableClass = "java.lang.IllegalStateException",
                    message = "AppLock failed",
                    stackTrace = "com.chrono.ssh.ui.AppLockScreen"
                )
            ),
            nowEpochMillis = 10_000L
        )

        assertNull(settings.appLockPinHash)
        assertNull(settings.appLockPinSalt)
        assertFalse(settings.appLockBiometricEnabled)
        assertFalse(initialAppLockState(appLockPinUsable(settings)))
    }

    @Test
    fun persistSettingsClearsCorruptAppLockBeforeStateUsesIt() {
        val previous = AppSettings(
            themeModeName = "System",
            themeFamilyId = "default"
        )
        val state = persistedSettingsStateAfterChange(
            previousSettings = previous,
            nextSettings = previous.copy(
                appLockPinHash = "hash",
                appLockPinSalt = "not-base64",
                appLockBiometricEnabled = true
            ),
            currentlyLocked = false
        )

        assertNull(state.settings.appLockPinHash)
        assertNull(state.settings.appLockPinSalt)
        assertFalse(state.settings.appLockBiometricEnabled)
        assertFalse(state.locked)
    }

    @Test
    fun appLockRecoveryClearsStrandedCorruptPinMaterial() {
        val recovered = appLockRecoveredSettings(
            AppSettings(
                themeModeName = "System",
                themeFamilyId = "default",
                appLockPinHash = "old-hash",
                appLockPinSalt = "bad-salt",
                appLockBiometricEnabled = true
            ),
            pin = "123456"
        )

        assertNull(recovered!!.appLockPinHash)
        assertNull(recovered.appLockPinSalt)
        assertFalse(recovered.appLockBiometricEnabled)
    }

    @Test
    fun appLockRecoveryDoesNotClearUsablePin() {
        val pin = PinLockPolicy.hashPin("123456", salt = "MTIzNDU2Nzg5MGFiY2RlZg")
        assertNull(
            appLockRecoveredSettings(
                AppSettings(
                    themeModeName = "System",
                    themeFamilyId = "default",
                    appLockPinHash = pin.hash,
                    appLockPinSalt = pin.salt
                ),
                pin = "123456"
            )
        )
    }

    @Test
    fun appLockRenderCrashContextOnlyArmsForUsablePin() {
        val pin = PinLockPolicy.hashPin("123456", salt = "MTIzNDU2Nzg5MGFiY2RlZg")

        assertTrue(
            appLockRenderCrashContext(
                AppSettings(
                    themeModeName = "System",
                    themeFamilyId = "default",
                    appLockPinHash = pin.hash,
                    appLockPinSalt = pin.salt
                )
            ).orEmpty().contains("AppLock render armed")
        )
        assertNull(appLockRenderCrashContext(AppSettings(themeModeName = "System", themeFamilyId = "default")))
    }

    @Test
    fun startupSettingsDisableUsableAppLockAfterRenderBreadcrumbAndComposeCrash() {
        val pin = PinLockPolicy.hashPin("123456", salt = "MTIzNDU2Nzg5MGFiY2RlZg")
        val settings = startupSettingsAfterCrashRecovery(
            settings = AppSettings(
                themeModeName = "System",
                themeFamilyId = "default",
                appLockPinHash = pin.hash,
                appLockPinSalt = pin.salt,
                appLockBiometricEnabled = true
            ),
            crashes = listOf(
                CrashLogEntry(
                    id = "armed",
                    atEpochMillis = 9_000L,
                    threadName = "main",
                    throwableClass = "java.lang.IllegalStateException",
                    message = appLockRenderCrashContext(AppSettings(themeModeName = "System", themeFamilyId = "default", appLockPinHash = pin.hash, appLockPinSalt = pin.salt)).orEmpty(),
                    stackTrace = "com.chrono.ssh.ui.ChronoSSHApp"
                ),
                CrashLogEntry(
                    id = "compose",
                    atEpochMillis = 9_900L,
                    threadName = "main",
                    throwableClass = "java.lang.ArrayIndexOutOfBoundsException",
                    message = "length=320; index=-2",
                    stackTrace = "androidx.compose.runtime.IntStack.peek2(Stack.kt:52)\nandroidx.compose.runtime.ComposerImpl.end(Composer.kt:2599)"
                )
            ),
            nowEpochMillis = 10_000L
        )

        assertNull(settings.appLockPinHash)
        assertNull(settings.appLockPinSalt)
        assertFalse(settings.appLockBiometricEnabled)
    }

    @Test
    fun startupSettingsDisableUsableAppLockAfterStaleRenderMarkerWithoutCrashLog() {
        val pin = PinLockPolicy.hashPin("123456", salt = "MTIzNDU2Nzg5MGFiY2RlZg")
        val settings = startupSettingsAfterCrashRecovery(
            settings = AppSettings(
                themeModeName = "System",
                themeFamilyId = "default",
                appLockPinHash = pin.hash,
                appLockPinSalt = pin.salt,
                appLockBiometricEnabled = true,
                appLockRenderArmedAtEpochMillis = 1_000L
            ),
            crashes = emptyList(),
            nowEpochMillis = 12_000L
        )

        assertNull(settings.appLockPinHash)
        assertNull(settings.appLockPinSalt)
        assertFalse(settings.appLockBiometricEnabled)
        assertNull(settings.appLockRenderArmedAtEpochMillis)
    }

    @Test
    fun startupSettingsKeepsFreshRenderMarkerUntilCrashWindowExpires() {
        val pin = PinLockPolicy.hashPin("123456", salt = "MTIzNDU2Nzg5MGFiY2RlZg")
        val settings = startupSettingsAfterCrashRecovery(
            settings = AppSettings(
                themeModeName = "System",
                themeFamilyId = "default",
                appLockPinHash = pin.hash,
                appLockPinSalt = pin.salt,
                appLockRenderArmedAtEpochMillis = 9_500L
            ),
            crashes = emptyList(),
            nowEpochMillis = 10_000L
        )

        assertTrue(appLockPinUsable(settings))
        assertEquals(9_500L, settings.appLockRenderArmedAtEpochMillis)
    }

    @Test
    fun persistSettingsKeepsExistingSessionUnlockedWhenEnablingValidAppLock() {
        val previous = AppSettings(
            themeModeName = "System",
            themeFamilyId = "default"
        )
        val pin = PinLockPolicy.hashPin("123456", salt = "MTIzNDU2Nzg5MGFiY2RlZg")
        val state = persistedSettingsStateAfterChange(
            previousSettings = previous,
            nextSettings = previous.copy(appLockPinHash = pin.hash, appLockPinSalt = pin.salt),
            currentlyLocked = false
        )

        assertTrue(appLockPinUsable(state.settings))
        assertFalse(state.locked)
    }

    @Test
    fun appLockTreatsPauseOrStopAsBackgrounding() {
        assertTrue(appLockWasBackgroundedAfterLifecycleEvent(previous = false, Lifecycle.Event.ON_PAUSE))
        assertTrue(appLockWasBackgroundedAfterLifecycleEvent(previous = false, Lifecycle.Event.ON_STOP))
        assertTrue(appLockWasBackgroundedAfterLifecycleEvent(previous = true, Lifecycle.Event.ON_RESUME))
        assertFalse(appLockWasBackgroundedAfterLifecycleEvent(previous = false, Lifecycle.Event.ON_RESUME))
    }

    @Test
    fun appLockRenderMarkerExpiresAfterCrashFuseWindow() {
        assertFalse(appLockRenderArmedMarkerExpired(null, nowEpochMillis = 20_000L))
        assertFalse(appLockRenderArmedMarkerExpired(11_000L, nowEpochMillis = 20_000L))
        assertTrue(appLockRenderArmedMarkerExpired(10_000L, nowEpochMillis = 20_000L))
    }

    @Test
    fun connectionsBackHonorsTransientReturnTarget() {
        val detail = ReturnTarget.ServerDetail("server-1")

        assertEquals(detail, connectionBackTarget(detail))
        assertEquals(ReturnTarget.Root(AppTab.Servers), connectionBackTarget(null))
    }

    @Test
    fun vaultRoutePrefersExplicitSectionBeforeCredentialFallback() {
        assertEquals("Tunnels", vaultInitialSectionForRoute("Tunnels", "credential-1"))
        assertEquals("Keys", vaultInitialSectionForRoute(null, "credential-1"))
        assertNull(vaultInitialSectionForRoute(null, null))
    }

    @Test
    fun terminalEntryCapturesCurrentPageWhenEnteringFromNonTerminalSurface() {
        val current = ReturnTarget.Root(AppTab.Servers)

        assertEquals(
            current,
            terminalReturnTargetOnEntry(current, previousTarget = null, terminalVisible = false)
        )
    }

    @Test
    fun terminalEntryKeepsPreviousTargetWhenSwitchingInsideTerminal() {
        val previous = ReturnTarget.ServerDetail("server-1")

        assertEquals(
            previous,
            terminalReturnTargetOnEntry(ReturnTarget.Root(AppTab.Connections), previous, terminalVisible = true)
        )
    }

    @Test
    fun terminalChromeCloseOnlyHidesSurface() {
        assertEquals(TerminalCloseIntent.HideSurface, terminalCloseIntent(fromTerminalChrome = true))
        assertEquals(TerminalCloseIntent.DisconnectWorkspace, terminalCloseIntent(fromTerminalChrome = false))
    }

    @Test
    fun metricsSchedulerSkipsTcpProbeWhenSshMetricsCanRunNow() {
        assertTrue(
            shouldCollectMetricsBeforeProbe(
                monitoringEnabled = true,
                credentialReady = true,
                hostTrusted = true,
                alreadyCollecting = false,
                refreshDue = true
            )
        )
    }

    @Test
    fun metricsSchedulerKeepsProbeFallbackWhenSshMetricsCannotRun() {
        assertFalse(shouldCollectMetricsBeforeProbe(true, credentialReady = false, hostTrusted = true, alreadyCollecting = false, refreshDue = true))
        assertFalse(shouldCollectMetricsBeforeProbe(true, credentialReady = true, hostTrusted = false, alreadyCollecting = false, refreshDue = true))
        assertFalse(shouldCollectMetricsBeforeProbe(true, credentialReady = true, hostTrusted = true, alreadyCollecting = true, refreshDue = true))
        assertFalse(shouldCollectMetricsBeforeProbe(true, credentialReady = true, hostTrusted = true, alreadyCollecting = false, refreshDue = false))
    }

    @Test
    fun manualRefreshCollectsSshMetricsImmediatelyWhenReady() {
        assertTrue(
            manualRefreshShouldCollectMetricsBeforeProbe(
                monitoringEnabled = true,
                credentialReady = true,
                hostTrusted = true,
                alreadyCollecting = false
            )
        )
    }

    @Test
    fun manualRefreshKeepsProbeFallbackWhenSshMetricsCannotRun() {
        assertFalse(manualRefreshShouldCollectMetricsBeforeProbe(true, credentialReady = false, hostTrusted = true, alreadyCollecting = false))
        assertFalse(manualRefreshShouldCollectMetricsBeforeProbe(true, credentialReady = true, hostTrusted = false, alreadyCollecting = false))
        assertFalse(manualRefreshShouldCollectMetricsBeforeProbe(true, credentialReady = true, hostTrusted = true, alreadyCollecting = true))
        assertFalse(manualRefreshShouldCollectMetricsBeforeProbe(false, credentialReady = true, hostTrusted = true, alreadyCollecting = false))
    }

    @Test
    fun metricsRefreshSkipDiagnosticExplainsWhyStatsStayProbeOnly() {
        assertEquals(
            "save a password or private key for Box.",
            metricsRefreshSkipDiagnostic(
                monitoringEnabled = true,
                credentialReady = false,
                hostTrusted = true,
                alreadyCollecting = false,
                refreshDue = true,
                serverName = "Box"
            )?.reason
        )
        assertEquals(
            ConnectionEventLevel.Warning,
            metricsRefreshSkipDiagnostic(true, credentialReady = true, hostTrusted = false, alreadyCollecting = false, refreshDue = true, serverName = "Box")?.level
        )
        assertEquals(
            "refresh already running for Box.",
            metricsRefreshSkipDiagnostic(true, credentialReady = true, hostTrusted = true, alreadyCollecting = true, refreshDue = true, serverName = "Box")?.reason
        )
        assertNull(
            metricsRefreshSkipDiagnostic(true, credentialReady = true, hostTrusted = true, alreadyCollecting = true, refreshDue = false, serverName = "Box")
        )
    }

    @Test
    fun loopTcpProbeRunsWhenMetricsAreDueOrProbeIsStale() {
        assertTrue(shouldRunTcpProbeForLoop(refreshDue = true, lastProbeAtEpochMillis = 10, nowEpochMillis = 11, minProbeIntervalMillis = 10_000))
        assertTrue(shouldRunTcpProbeForLoop(refreshDue = false, lastProbeAtEpochMillis = null, nowEpochMillis = 11, minProbeIntervalMillis = 10_000))
        assertTrue(shouldRunTcpProbeForLoop(refreshDue = false, lastProbeAtEpochMillis = 0, nowEpochMillis = 10_000, minProbeIntervalMillis = 10_000))
        assertFalse(shouldRunTcpProbeAfterMetricsGate(alreadyCollecting = true, refreshDue = true, lastProbeAtEpochMillis = 10, nowEpochMillis = 11, minProbeIntervalMillis = 10_000))
        assertTrue(shouldRunTcpProbeAfterMetricsGate(alreadyCollecting = false, refreshDue = true, lastProbeAtEpochMillis = 10, nowEpochMillis = 11, minProbeIntervalMillis = 10_000))
    }

    @Test
    fun loopTcpProbeSkipsWhenRecentProbeAlreadyUpdatedReachability() {
        assertFalse(shouldRunTcpProbeForLoop(refreshDue = false, lastProbeAtEpochMillis = 9_000, nowEpochMillis = 10_000, minProbeIntervalMillis = 2_000))
    }

    @Test
    fun hostDeepLinkParsesTerminalOpenForSavedHostId() {
        assertEquals(
            HostDeepLink("server-1", HostDeepLinkTarget.Terminal),
            parseHostDeepLink("chronossh://host?id=server-1&open=terminal")
        )
    }

    @Test
    fun hostDeepLinkParsesSftpPathOpenForSavedHostId() {
        assertEquals(
            HostDeepLink("server 1", HostDeepLinkTarget.Sftp),
            parseHostDeepLink("ChronoSSH://HOST/sftp?id=server+1")
        )
    }

    @Test
    fun hostDeepLinkIgnoresImportLinksWithoutDirectOpenTarget() {
        assertNull(parseHostDeepLink("chronossh://host?host=ssh.example.test&user=deploy"))
    }

    @Test
    fun hostDeepLinkRejectsMalformedEncoding() {
        assertNull(parseHostDeepLink("chronossh://host?id=server%ZZ&open=terminal"))
    }

    @Test
    fun nextSftpWorkspaceAfterCloseKeepsUnclosedSelection() {
        assertEquals(
            "sftp:b",
            nextSftpWorkspaceAfterClose(
                closedKey = "sftp:a",
                selectedKey = "sftp:b",
                remainingKeys = listOf("sftp:b", "sftp:c")
            )
        )
    }

    @Test
    fun nextSftpWorkspaceAfterCloseSelectsRemainingTabWhenClosingActive() {
        assertEquals(
            "sftp:b",
            nextSftpWorkspaceAfterClose(
                closedKey = "sftp:a",
                selectedKey = "sftp:a",
                remainingKeys = listOf("sftp:b", "sftp:c")
            )
        )
    }

    @Test
    fun nextSftpWorkspaceAfterCloseClearsSelectionWhenNoTabsRemain() {
        assertNull(
            nextSftpWorkspaceAfterClose(
                closedKey = "sftp:a",
                selectedKey = "sftp:a",
                remainingKeys = emptyList()
            )
        )
    }

    @Test
    fun sftpWorkspaceKeysForServerReturnsOnlyDeletedHostTabs() {
        assertEquals(
            listOf("sftp:a", "sftp:c"),
            sftpWorkspaceKeysForServer(
                mapOf("sftp:a" to "server-1", "sftp:b" to "server-2", "sftp:c" to "server-1"),
                "server-1"
            )
        )
    }
}

private class FakeSshSession(
    override val id: String,
    override val serverId: String
) : SshSession {
    override val transcriptPreview: String = ""
    override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult = CommandResult(command, 0, "", "")
    override suspend fun resizeTerminal(columns: Int, rows: Int) = Unit
    override suspend fun writeTerminal(input: String) = Unit
    override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) = Unit
    override suspend fun close() = Unit
}
