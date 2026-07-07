package com.chrono.ssh.core.data

import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.service.OpenSshConfigHost
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenSshImportPolicyTest {
    @Test
    fun skipsExistingHostWithSameEndpointAndUser() {
        val host = OpenSshConfigHost(
            alias = "prod",
            hostName = "prod.example.com",
            user = "deploy",
            port = 22,
            identityFile = null,
            proxyJumpAlias = null
        )

        assertEquals(true, shouldSkipOpenSshImport(listOf(server("existing", "prod.example.com", "deploy", 22)), host))
        assertEquals(false, shouldSkipOpenSshImport(listOf(server("other-user", "prod.example.com", "root", 22)), host))
        assertEquals(false, shouldSkipOpenSshImport(listOf(server("other-port", "prod.example.com", "deploy", 2200)), host))
    }

    @Test
    fun endpointKeysNormalizeHostCaseAndWhitespace() {
        assertEquals(
            openSshImportEndpointKey("prod.example.com", 22, "deploy"),
            openSshImportEndpointKey(" PROD.EXAMPLE.COM ", 22, "deploy")
        )
    }

    @Test
    fun importedNamesGetStableSuffixesWhenAliasesCollide() {
        assertEquals("prod", uniqueOpenSshImportName("prod", emptySet()))
        assertEquals("prod (2)", uniqueOpenSshImportName("prod", setOf("prod")))
        assertEquals("prod (3)", uniqueOpenSshImportName("prod", setOf("prod", "prod (2)")))
        assertEquals("Imported Host", uniqueOpenSshImportName(" ", emptySet()))
    }

    @Test
    fun identityFileImportCreatesPendingPrivateKeyCredential() {
        val credential = openSshIdentityCredential("~/.ssh/prod_ed25519", emptySet(), now = 100)

        assertEquals("openssh-prod-ed25519", credential.id)
        assertEquals("OpenSSH prod_ed25519", credential.label)
        assertEquals(false, credential.secretBacked)
        assertEquals("IdentityFile ~/.ssh/prod_ed25519", credential.publicKeyPreview)
    }

    @Test
    fun identityCredentialIdsAvoidExistingCollisions() {
        assertEquals(
            "openssh-prod-ed25519-3",
            uniqueOpenSshIdentityCredentialId("~/.ssh/prod_ed25519", setOf("openssh-prod-ed25519", "openssh-prod-ed25519-2"))
        )
    }

    @Test
    fun proxyJumpImportAllowsCredentialBackedChains() {
        val target = server("target", "app.example.com", "deploy", 22)
        val edge = server("edge", "edge.example.com", "jump", 22).copy(credentialId = "edge-key")
        val jump = server("jump", "bastion.example.com", "jump", 22).copy(
            credentialId = "jump-key",
            proxyJumpHostId = "edge"
        )

        assertEquals(null, openSshProxyJumpImportError(target, jump, listOf(target, jump, edge)))
    }

    @Test
    fun proxyJumpImportRejectsLoops() {
        val target = server("target", "app.example.com", "deploy", 22)
        val jump = server("jump", "bastion.example.com", "jump", 22).copy(
            credentialId = "jump-key",
            proxyJumpHostId = "target"
        )

        assertEquals(
            "ProxyJump chain cannot loop back to this host.",
            openSshProxyJumpImportError(target, jump, listOf(target, jump))
        )
    }

    private fun server(id: String, host: String, username: String, port: Int): ServerProfile {
        return ServerProfile(
            id = id,
            name = id,
            host = host,
            port = port,
            username = username,
            group = "Imported",
            tags = emptyList(),
            osName = "Linux",
            osVersion = "",
            accent = ServerAccent("cyan", 0xff00ffff),
            credentialId = null,
            terminalProfileId = "default",
            monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 2, useOptionalAgent = false)
        )
    }
}
