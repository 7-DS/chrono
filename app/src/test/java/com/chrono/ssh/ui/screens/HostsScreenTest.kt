package com.chrono.ssh.ui.screens

import com.chrono.ssh.core.model.HostKeyTrustState
import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.KnownHost
import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.PortForwardType
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.Snippet
import com.chrono.ssh.core.model.TransferRecord
import com.chrono.ssh.core.model.TransferRecordState
import com.chrono.ssh.core.model.TransferDirection
import com.chrono.ssh.core.service.BackupCredentialPolicy
import com.chrono.ssh.core.service.HostShareLinkCodec
import com.chrono.ssh.core.service.SftpEntry
import com.chrono.ssh.core.service.SftpSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostsScreenTest {
    @Test
    fun hostSavedForwardCountIncludesAllSupportedTunnelTypes() {
        val forwards = listOf(
            forward("target", PortForwardType.Local),
            forward("target", PortForwardType.Remote),
            forward("target", PortForwardType.DynamicSocks),
            forward("other", PortForwardType.Local)
        )

        assertEquals(3, hostSavedForwardCount("target", forwards))
    }

    @Test
    fun defaultLocalForwardUsesPreferredServerWhenAvailable() {
        val servers = listOf(serverProfile("first"), serverProfile("preferred"))

        assertEquals("preferred", defaultLocalForward(servers, "preferred").serverId)
        assertEquals("first", defaultLocalForward(servers, "missing").serverId)
    }

    @Test
    fun portForwardPageOnlyShowsSelectedHostTunnels() {
        val local = forward("target", PortForwardType.Local)
        val remote = forward("target", PortForwardType.Remote)
        val socks = forward("target", PortForwardType.DynamicSocks)
        val other = forward("other", PortForwardType.Local)

        assertEquals(listOf(local, remote, socks), portForwardPageForwards("target", listOf(local, other, remote, socks)))
    }

    @Test
    fun portForwardPageKeepsAVisibleExpandedTunnelAfterForwardRefresh() {
        val local = forward("target", PortForwardType.Local)
        val remote = forward("target", PortForwardType.Remote)

        assertEquals(local.id, portForwardPageExpandedId(null, listOf(local, remote)))
        assertEquals(remote.id, portForwardPageExpandedId(remote.id, listOf(local, remote)))
        assertEquals(local.id, portForwardPageExpandedId("deleted", listOf(local, remote)))
        assertEquals(null, portForwardPageExpandedId("deleted", emptyList()))
    }

    @Test
    fun tunnelEditorTitlesCoverAllTunnelTypes() {
        assertEquals("Local tunnel", tunnelEditorTitle(PortForwardType.Local))
        assertEquals("Remote tunnel", tunnelEditorTitle(PortForwardType.Remote))
        assertEquals("SOCKS tunnel", tunnelEditorTitle(PortForwardType.DynamicSocks))
    }

    @Test
    fun hostFilesLaunchSelectsFilesSectionAndHost() {
        assertEquals(HostFilesLaunch("Files", "server-1"), hostFilesLaunch("server-1"))
    }

    @Test
    fun vaultHostVisualUsesOsLogoOnlyWhenMetadataMatchesKnownLogo() {
        assertEquals(true, vaultHostUsesOsLogo("Ubuntu 24.04 LTS"))
        assertEquals(true, vaultHostUsesOsLogo("Windows Server 2022"))
        assertEquals(false, vaultHostUsesOsLogo(""))
        assertEquals(false, vaultHostUsesOsLogo("Custom appliance"))
    }

    @Test
    fun scpTransferFailureMessagesUseActionableSftpMapping() {
        val message = scpTransferFailureMessage(
            TransferDirection.Download,
            IllegalStateException("Permission denied"),
            "/root/secret.txt"
        )

        assertTrue(message.startsWith("SCP download failed"))
        assertTrue(message.contains("Permission denied"))
        assertTrue(message.contains("ownership"))
    }

    @Test
    fun sftpTextEditDiscardConfirmationOnlyAppearsForUnsavedChanges() {
        assertEquals(false, sftpTextEditNeedsDiscardConfirmation("same", "same"))
        assertEquals(true, sftpTextEditNeedsDiscardConfirmation("same", "changed"))
    }

    @Test
    fun sftpOpenFailureRecoveryKeepsLastLoadedEmptyDirectory() {
        val recovery = sftpOpenFailureRecovery(
            previousPath = "/srv/empty",
            previousEntries = emptyList(),
            requestedPath = "/srv/missing"
        )

        assertEquals("/srv/empty", recovery.path)
        assertEquals(emptyList<SftpEntry>(), recovery.entries)
    }

    @Test
    fun sftpOpenFailureRecoveryKeepsLastLoadedPopulatedDirectory() {
        val entries = listOf(SftpEntry("logs", "/srv/logs", true, 0, 1L))
        val recovery = sftpOpenFailureRecovery(
            previousPath = "/srv",
            previousEntries = entries,
            requestedPath = "/root"
        )

        assertEquals("/srv", recovery.path)
        assertEquals(entries, recovery.entries)
    }

    @Test
    fun sftpOpenFailureRecoveryUsesRequestedPathBeforeFirstLoad() {
        val recovery = sftpOpenFailureRecovery(
            previousPath = "",
            previousEntries = emptyList(),
            requestedPath = "/srv"
        )

        assertEquals("/srv", recovery.path)
        assertEquals(emptyList<SftpEntry>(), recovery.entries)
    }

    @Test
    fun transferCancelledStatusKeepsProtocolDirectionAndName() {
        assertEquals(
            "SFTP Download cancelled: logs.txt",
            transferCancelledStatus("SFTP", TransferDirection.Download, "logs.txt")
        )
        assertEquals(
            "SCP Upload cancelled: file",
            transferCancelledStatus("SCP", TransferDirection.Upload, "")
        )
    }

    @Test
    fun sftpCompactBreadcrumbSegmentsKeepsShallowPaths() {
        val segments = listOf("/" to "/", "srv" to "/srv", "app" to "/srv/app")

        assertEquals(segments, sftpCompactBreadcrumbSegments(segments))
    }

    @Test
    fun sftpCompactBreadcrumbSegmentsCollapsesDeepPathsWithClickableHiddenParent() {
        assertEquals(
            listOf(
                "/" to "/",
                "..." to "/srv/app/releases",
                "2026" to "/srv/app/releases/2026",
                "current" to "/srv/app/releases/2026/current"
            ),
            sftpCompactBreadcrumbSegments(
                listOf(
                    "/" to "/",
                    "srv" to "/srv",
                    "app" to "/srv/app",
                    "releases" to "/srv/app/releases",
                    "2026" to "/srv/app/releases/2026",
                    "current" to "/srv/app/releases/2026/current"
                )
            )
        )
    }

    @Test
    fun smbFileBrowserStartPathKeepsShareSubpath() {
        val server = serverProfile("smb").copy(protocol = ConnectionProtocol.Smb, host = "nas")

        assertEquals("/media/movies", fileBrowserStartPath(server.copy(startDirectory = "//nas/media/movies"), emptyList()))
        assertEquals("/media/movies", fileBrowserStartPath(server.copy(startDirectory = "smb://nas/media/movies"), emptyList()))
        assertEquals("/media/movies", fileBrowserStartPath(server.copy(startDirectory = "media/movies"), emptyList()))
        assertEquals(".", fileBrowserStartPath(server.copy(startDirectory = ""), emptyList()))
    }

    @Test
    fun fileCopyDestinationReadinessMatchesBackendRequirements() {
        val ssh = serverProfile("ssh")
        val smb = serverProfile("smb").copy(protocol = ConnectionProtocol.Smb)
        val rclone = serverProfile("rclone").copy(protocol = ConnectionProtocol.Rclone)
        val password = credential()
        val key = password.copy(type = CredentialType.PrivateKey)

        assertEquals(true, fileCopyDestinationReady(ssh, password, trusted = true))
        assertEquals(false, fileCopyDestinationReady(ssh, password, trusted = false))
        assertEquals(true, fileCopyDestinationReady(smb, password, trusted = false))
        assertEquals(false, fileCopyDestinationReady(smb, key, trusted = true))
        assertEquals(true, fileCopyDestinationReady(rclone, credential = null, trusted = false))
    }

    @Test
    fun rcloneSpaceLabelShowsUnknownAsDash() {
        assertEquals("--", (-1L).rcloneSpaceLabel())
        assertEquals("2.00 K", 2048L.rcloneSpaceLabel())
    }

    @Test
    fun sftpHostListOnlyExpandsAfterAHostIsSelectedOnDemand() {
        assertEquals(true, sftpHostListExpanded(null, hostsExpanded = false))
        assertEquals(false, sftpHostListExpanded("server-1", hostsExpanded = false))
        assertEquals(true, sftpHostListExpanded("server-1", hostsExpanded = true))
    }

    @Test
    fun sftpSortMenuLabelUsesCompactDirectionGlyphs() {
        assertEquals("Name ↑", sftpSortMenuLabel(SftpSortMode.Name, descending = false))
        assertEquals("Size ↓", sftpSortMenuLabel(SftpSortMode.Size, descending = true))
    }

    @Test
    fun activeTransferForChoosesNewestActiveTransferForServer() {
        val oldRunning = transfer("old", "server-1", TransferRecordState.Running, 10L)
        val newestQueued = transfer("new", "server-1", TransferRecordState.Queued, 20L)
        val complete = transfer("done", "server-1", TransferRecordState.Complete, 30L)
        val otherServer = transfer("other", "server-2", TransferRecordState.Running, 40L)

        assertEquals(newestQueued, activeTransferFor(listOf(oldRunning, newestQueued, complete, otherServer), "server-1"))
    }

    @Test
    fun knownHostChangedAndRejectedStatesUseExplicitRecoveryLabels() {
        val changed = knownHost(HostKeyTrustState.Changed)
        val rejected = knownHost(HostKeyTrustState.Rejected)

        assertEquals("CHANGED", knownHostStatusLabel(changed))
        assertEquals("ssh-ed25519 · fingerprint changed", knownHostStatusSubtitle(changed))
        assertEquals("Replace Key", knownHostTrustActionLabel(changed))
        assertEquals("REJECTED", knownHostStatusLabel(rejected))
        assertEquals("ssh-ed25519 · previously rejected", knownHostStatusSubtitle(rejected))
        assertEquals("Replace Key", knownHostTrustActionLabel(rejected))
    }

    @Test
    fun knownHostAuditCopyTextIncludesFingerprintAndSeenTimes() {
        val text = knownHostAuditCopyText(knownHost(HostKeyTrustState.Trusted))

        assertTrue(text.contains("box.test:22"))
        assertTrue(text.contains("State: TRUST"))
        assertTrue(text.contains("Algorithm: ssh-ed25519"))
        assertTrue(text.contains("Fingerprint: SHA256:test"))
        assertTrue(text.contains("First seen: 1970-01-01 00:00:00"))
        assertTrue(text.contains("Last seen: 1970-01-01 00:00:00"))
    }

    @Test
    fun knownHostAuditTimeLabelHandlesMissingTimestamp() {
        assertEquals("Unknown", knownHostAuditTimeLabel(0L))
        assertEquals("Unknown", knownHostAuditTimeLabel(-1L))
    }

    @Test
    fun importSnippetShareLinkUpsertsDecodedSnippet() {
        val snippet = Snippet("snippet-1", "Deploy", "systemctl restart app", listOf("ops"), null, emptyList(), group = "Ops", favorite = true, confirmBeforeRun = false)
        var imported: Snippet? = null

        val status = importSnippetShareLink(HostShareLinkCodec.encode(snippet)) {
            imported = it
            Result.success(it)
        }

        assertEquals(true, status.imported)
        assertEquals("Imported snippet: Deploy", status.message)
        val importedSnippet = checkNotNull(imported)
        assertEquals(snippet.id, importedSnippet.id)
        assertEquals(snippet.name, importedSnippet.name)
        assertEquals(snippet.command, importedSnippet.command)
        assertEquals(snippet.tags, importedSnippet.tags)
        assertEquals(snippet.group, importedSnippet.group)
        assertEquals(snippet.favorite, importedSnippet.favorite)
        assertEquals(snippet.confirmBeforeRun, importedSnippet.confirmBeforeRun)
        assertTrue(importedSnippet.createdAtEpochMillis > 0L)
    }

    @Test
    fun credentialMetadataNormalizesOrganizationFields() {
        val updated = credential().withMetadata(
            group = " Ops ",
            tagsText = " prod, deploy, prod, ",
            notes = " rotate quarterly ",
            favorite = true
        )

        assertEquals("Ops", updated.group)
        assertEquals(listOf("prod", "deploy"), updated.tags)
        assertEquals("rotate quarterly", updated.notes)
        assertEquals(true, updated.favorite)
        assertEquals("Password · Ops · Favorite", credentialDetailSubtitle(updated))
    }

    @Test
    fun credentialDetailSubtitleSkipsBlankOrganization() {
        assertEquals("Password", credential().withMetadata("", "", "", false).let(::credentialDetailSubtitle))
    }

    @Test
    fun expandedCredentialActionsExposeUsefulSecretActionsWhenAvailable() {
        val key = credential().copy(
            type = CredentialType.PrivateKey,
            publicKeyPreview = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMockPublicKeyPayload user@example",
            encryptedPayloadRef = "secret-key"
        )
        val metadataOnlyKey = key.copy(encryptedPayloadRef = BackupCredentialPolicy.IMPORT_REQUIRED_REF)

        assertEquals(
            listOf(
                "Details",
                "Validate",
                "Copy",
                "Copy Pub",
                "Export Pub",
                "Export",
                "Share",
                "Rename",
                "Organize",
                "Unlink",
                "Replace",
                "Copy Link",
                "Share Link",
                "QR",
                "Remove"
            ),
            expandedCredentialActionLabels(key)
        )
        assertEquals(
            listOf("Details", "Validate", "Rename", "Organize", "Unlink", "Replace", "Copy Link", "Share Link", "QR", "Remove"),
            expandedCredentialActionLabels(metadataOnlyKey)
        )
    }

    @Test
    fun importSnippetShareLinkRejectsInvalidClipboardPayload() {
        val status = importSnippetShareLink("not a chronossh link") {
            error("should not upsert")
        }

        assertEquals(false, status.imported)
        assertEquals("Clipboard does not contain a valid snippet link.", status.message)
    }

    @Test
    fun importCredentialShareLinkUpsertsPendingIdentity() {
        val credential = Credential(
            id = "identity-1",
            label = "Prod key",
            type = CredentialType.PrivateKey,
            publicKeyPreview = "ssh-ed25519 AAAATEST prod",
            encryptedPayloadRef = "secret-1",
            createdAtEpochMillis = 1L
        )
        var imported: Credential? = null

        val status = importCredentialShareLink(HostShareLinkCodec.encode(credential), emptyList()) {
            imported = it
        }

        assertEquals(true, status.imported)
        assertEquals("Imported identity: Prod key", status.message)
        assertEquals("import-required", imported?.encryptedPayloadRef)
    }

    @Test
    fun importCredentialShareLinkDoesNotOverwriteSavedSecret() {
        val existing = Credential("identity-1", "Prod key", CredentialType.Password, null, "secret-1", 1L)

        val status = importCredentialShareLink(HostShareLinkCodec.encode(existing), listOf(existing)) {
            error("should not upsert")
        }

        assertEquals(false, status.imported)
        assertEquals("Identity import skipped: Prod key already has a saved secret.", status.message)
    }

    @Test
    fun importForwardShareLinkUpsertsDecodedTunnelForExistingHost() {
        val server = serverProfile("server-1")
        val forward = forward(server.id, PortForwardType.Local)
        var imported: PortForwardRule? = null

        val status = importForwardShareLink(HostShareLinkCodec.encode(forward), listOf(server)) {
            imported = it
        }

        assertEquals(true, status.imported)
        assertEquals("Imported tunnel: 127.0.0.1:8022 -> 127.0.0.1:22", status.message)
        assertEquals(forward, imported)
    }

    @Test
    fun importForwardShareLinkRejectsMissingHost() {
        val forward = forward("missing", PortForwardType.Local)
        val status = importForwardShareLink(HostShareLinkCodec.encode(forward), emptyList()) {
            error("should not upsert")
        }

        assertEquals(false, status.imported)
        assertEquals("Tunnel import failed: host profile is missing.", status.message)
    }

    @Test
    fun hostEditorSavedKeyCredentialsOnlyExposeKeys() {
        val privateKey = Credential("key-1", "Deploy key", CredentialType.PrivateKey, "ssh-ed25519 AAAA", "secret-key", 1L)
        val password = Credential("password-1", "Root password", CredentialType.Password, null, "secret-password", 1L)
        val hardwareKey = Credential("hardware-1", "Security key", CredentialType.HardwareKey, null, "secret-hardware", 1L)

        assertEquals(listOf(privateKey, hardwareKey), hostEditorSavedKeyCredentials(listOf(password, privateKey, hardwareKey)))
    }

    @Test
    fun hostEditorVaultDialogShowsAllSavedIdentitiesWithKeysFirst() {
        val privateKey = Credential("key-1", "Deploy key", CredentialType.PrivateKey, "ssh-ed25519 AAAA", "secret-key", 1L)
        val password = Credential("password-1", "Root password", CredentialType.Password, null, "secret-password", 1L)
        val hardwareKey = Credential("hardware-1", "Security key", CredentialType.HardwareKey, null, "secret-hardware", 1L)
        val credentials = listOf(password, privateKey, hardwareKey)

        assertEquals(listOf(privateKey, hardwareKey), hostEditorIdentityDialogCredentials(credentials, vaultMode = false))
        assertEquals(listOf(privateKey, hardwareKey, password), hostEditorIdentityDialogCredentials(credentials, vaultMode = true))
    }

    @Test
    fun hostEditorIdentityDialogActionsStayInEditorFlow() {
        assertEquals(
            listOf("No saved identity", "Add Key", "Add Password"),
            hostEditorIdentityDialogActions(hasSelectedCredential = false)
        )
        assertEquals(
            listOf("No saved identity", "Add Key", "Add Password", "Clear Saved Identity"),
            hostEditorIdentityDialogActions(hasSelectedCredential = true)
        )
    }

    @Test
    fun hostEditorOsPickerProvidesLargeSearchableCatalog() {
        assertEquals(true, hostEditorOsPresets().size >= 100)
        assertEquals(true, "OpenWrt" in hostEditorFilteredOsPresets("wrt"))
        assertEquals(true, "FreeBSD" in hostEditorFilteredOsPresets("bsd"))
        assertEquals(true, "Ubuntu" in hostEditorFilteredOsPresets("ubuntu"))
        assertEquals(true, "iStoreOS" in hostEditorFilteredOsPresets("istore"))
        assertEquals(true, "Synology DSM" in hostEditorFilteredOsPresets("synology"))
        assertEquals(true, "MikroTik RouterOS" in hostEditorFilteredOsPresets("router"))
    }

    @Test
    fun hostEditorIdentityDetailShowsTypeAndReadiness() {
        val savedKey = Credential("key-1", "Deploy key", CredentialType.PrivateKey, null, "secret-key", 1L)
        val pendingKey = Credential("key-2", "Imported key", CredentialType.PrivateKey, null, "pending", 1L)

        assertEquals("Private Key / saved", hostEditorIdentityDetail(savedKey))
        assertEquals("Private Key / needs key material", hostEditorIdentityDetail(pendingKey))
    }

    private fun forward(serverId: String, type: PortForwardType): PortForwardRule {
        return PortForwardRule(
            id = "$serverId-$type",
            serverId = serverId,
            type = type,
            localHost = "127.0.0.1",
            localPort = 8022,
            remoteHost = if (type == PortForwardType.DynamicSocks) "" else "127.0.0.1",
            remotePort = if (type == PortForwardType.DynamicSocks) 0 else 22,
            enabled = false,
            autoStart = false
        )
    }

    private fun credential() = Credential(
        id = "identity-test",
        label = "Root password",
        type = CredentialType.Password,
        publicKeyPreview = null,
        encryptedPayloadRef = "secret-test",
        createdAtEpochMillis = 1L
    )

    private fun transfer(id: String, serverId: String, state: TransferRecordState, updatedAt: Long): TransferRecord {
        return TransferRecord(
            id = id,
            serverId = serverId,
            direction = TransferDirection.Download,
            remotePath = "/tmp/$id",
            localDisplayName = id,
            progress = 0.5f,
            state = state,
            message = "",
            updatedAtEpochMillis = updatedAt
        )
    }

    private fun knownHost(state: HostKeyTrustState) = KnownHost(
        id = "host",
        host = "box.test",
        port = 22,
        algorithm = "ssh-ed25519",
        fingerprint = "SHA256:test",
        trusted = state == HostKeyTrustState.Trusted,
        firstSeenEpochMillis = 1L,
        lastSeenEpochMillis = 2L,
        trustState = state
    )

    private fun serverProfile(id: String) = ServerProfile(
        id = id,
        name = "Box",
        host = "box.test",
        port = 22,
        username = "root",
        group = "",
        tags = emptyList(),
        osName = "",
        osVersion = "",
        accent = ServerAccent("cyan", 0xff00ffff),
        credentialId = null,
        terminalProfileId = "",
        monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 2, useOptionalAgent = false)
    )
}
