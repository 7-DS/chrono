package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.PortForwardType
import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.Snippet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostShareLinkCodecTest {
    @Test
    fun hostShareLinkRoundTripsNotesWithoutSecrets() {
        val server = ServerProfile(
            id = "server-1",
            name = "Prod",
            host = "ssh.example.test",
            port = 2222,
            username = "deploy",
            group = "Ops",
            tags = listOf("All", "prod"),
            osName = "Linux",
            osVersion = "Unknown",
            accent = ServerAccent("cyan", 0xff00ffff),
            credentialId = "secret-credential",
            terminalProfileId = "term-default",
            monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 2, useOptionalAgent = false),
            notes = "Owner: platform",
            connectTimeoutSeconds = 14,
            sshCompressionEnabled = true
        )

        val decoded = HostShareLinkCodec.decode(HostShareLinkCodec.encode(server))

        checkNotNull(decoded)
        assertEquals("Owner: platform", decoded.notes)
        assertEquals(14, decoded.connectTimeoutSeconds)
        assertTrue(decoded.sshCompressionEnabled)
        assertNull(decoded.credentialId)
    }

    @Test
    fun snippetShareLinkRoundTripsSanitizedMetadata() {
        val snippet = Snippet(
            id = "snippet-logs",
            name = "  Tail logs  ",
            command = "tail -f {{ log_path }}",
            tags = listOf("Logs, Ops"),
            serverScope = " server-1 ",
            variables = listOf("manualVar")
        )

        val decoded = HostShareLinkCodec.decodeSnippet(HostShareLinkCodec.encode(snippet))

        checkNotNull(decoded)
        assertEquals("snippet-logs", decoded.id)
        assertEquals("Tail logs", decoded.name)
        assertEquals("tail -f {{ log_path }}", decoded.command)
        assertEquals(listOf("logs", "ops"), decoded.tags)
        assertEquals("server-1", decoded.serverScope)
        assertEquals(listOf("manualVar", "log_path"), decoded.variables)
    }

    @Test
    fun snippetShareLinkRejectsUnsafeCommands() {
        assertNull(
            HostShareLinkCodec.decodeSnippet(
                "chronossh://snippet?id=snip-root&name=Root&command=sudo+apt+update"
            )
        )
        assertTrue(
            runCatching {
                HostShareLinkCodec.encode(
                    Snippet("snip-root", "Root", "sudo apt update", emptyList(), null, emptyList())
                )
            }.isFailure
        )
    }

    @Test
    fun identityShareLinkRoundTripsMetadataWithoutSecretRefs() {
        val credential = Credential(
            id = "identity-prod",
            label = "Prod key",
            type = CredentialType.PrivateKey,
            publicKeyPreview = "ssh-ed25519 AAAATEST prod",
            encryptedPayloadRef = "secret-prod",
            createdAtEpochMillis = 123L,
            passphraseRef = "secret-passphrase",
            lastUsedEpochMillis = 456L,
            username = "deploy",
            group = "Ops",
            tags = listOf("Prod", "Keys"),
            notes = "Rotate quarterly",
            favorite = true,
            importedAtEpochMillis = 789L
        )

        val decoded = HostShareLinkCodec.decodeCredential(HostShareLinkCodec.encode(credential))

        checkNotNull(decoded)
        assertEquals("identity-prod", decoded.id)
        assertEquals("Prod key", decoded.label)
        assertEquals(CredentialType.PrivateKey, decoded.type)
        assertEquals("ssh-ed25519 AAAATEST prod", decoded.publicKeyPreview)
        assertEquals(BackupCredentialPolicy.IMPORT_REQUIRED_REF, decoded.encryptedPayloadRef)
        assertNull(decoded.passphraseRef)
        assertEquals(0L, decoded.lastUsedEpochMillis)
        assertEquals("deploy", decoded.username)
        assertEquals("Ops", decoded.group)
        assertEquals(listOf("prod", "keys"), decoded.tags)
        assertEquals("Rotate quarterly", decoded.notes)
        assertTrue(decoded.favorite)
        assertEquals(789L, decoded.importedAtEpochMillis)
    }

    @Test
    fun identityShareLinkDecodesOlderLinksWithoutOptionalMetadata() {
        val decoded = HostShareLinkCodec.decodeCredential(
            "chronossh://identity?id=identity-old&label=Old+key&type=PrivateKey&preview=ssh-ed25519+AAAA&created=123"
        )

        checkNotNull(decoded)
        assertEquals("identity-old", decoded.id)
        assertEquals("Old key", decoded.label)
        assertEquals("", decoded.username)
        assertEquals("", decoded.group)
        assertEquals(emptyList<String>(), decoded.tags)
        assertEquals("", decoded.notes)
        assertFalse(decoded.favorite)
        assertEquals(0L, decoded.importedAtEpochMillis)
    }

    @Test
    fun identityShareLinkRoundTripsHardwareKeyMetadataWithoutSecretRefs() {
        val decoded = HostShareLinkCodec.decodeCredential(
            HostShareLinkCodec.encode(
                Credential("identity-1", "Security key", CredentialType.HardwareKey, "sk-ssh-ed25519 AAAA", "secret-1", 1L)
            )
        )

        checkNotNull(decoded)
        assertEquals(CredentialType.HardwareKey, decoded.type)
        assertEquals("sk-ssh-ed25519 AAAA", decoded.publicKeyPreview)
        assertEquals(BackupCredentialPolicy.IMPORT_REQUIRED_REF, decoded.encryptedPayloadRef)
    }

    @Test
    fun portForwardShareLinkRoundTripsWithoutRuntimeState() {
        val rule = PortForwardRule(
            id = "forward-db",
            serverId = "server-1",
            type = PortForwardType.Local,
            localHost = "",
            localPort = 15432,
            remoteHost = "db.internal",
            remotePort = 5432,
            enabled = true,
            autoStart = true,
            label = "Production DB",
            group = "Data",
            favorite = true
        )

        val decoded = HostShareLinkCodec.decodePortForward(HostShareLinkCodec.encode(rule))

        checkNotNull(decoded)
        assertEquals("forward-db", decoded.id)
        assertEquals("server-1", decoded.serverId)
        assertEquals(PortForwardType.Local, decoded.type)
        assertEquals("127.0.0.1", decoded.localHost)
        assertEquals(15432, decoded.localPort)
        assertEquals("db.internal", decoded.remoteHost)
        assertEquals(5432, decoded.remotePort)
        assertEquals("Production DB", decoded.label)
        assertEquals("Data", decoded.group)
        assertTrue(decoded.favorite)
        assertFalse(decoded.enabled)
        assertFalse(decoded.autoStart)
    }

    @Test
    fun portForwardShareLinkRejectsInvalidTargets() {
        assertNull(
            HostShareLinkCodec.decodePortForward(
                "chronossh://tunnel?id=f1&server=server-1&type=Local&localPort=15432&remoteHost=https%3A%2F%2Fdb.example.test%3Brm&remotePort=5432"
            )
        )
        assertNull(HostShareLinkCodec.decodePortForward("chronossh://tunnel?id=f1&server=server-1&type=Bad"))
        assertTrue(
            runCatching {
                HostShareLinkCodec.encode(
                    PortForwardRule("f1", "server-1", PortForwardType.Local, "127.0.0.1", 80, "db", 5432, false, false)
                )
            }.isFailure
        )
    }
}
