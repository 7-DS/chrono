package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.PortForwardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenSshConfigParserTest {
    @Test
    fun parsesConcreteHostsAndMultiAliasBlocksAndSkipsWildcards() {
        val hosts = OpenSshConfigParser.parse(
            """
            Host *
              User defaultuser
              Port 2200

            Host dev
              HostName dev.example.com
              User deploy # inline comment
              Port 2222
              IdentityFile ~/.ssh/dev.pem

            Host server1 server2
              HostName 10.0.0.10
              User ops
            """.trimIndent()
        )

        assertEquals(3, hosts.size)
        assertEquals("dev", hosts.first().alias)
        assertEquals("dev.example.com", hosts.first().hostName)
        assertEquals("deploy", hosts.first().user)
        assertEquals(2222, hosts.first().port)
        assertEquals("~/.ssh/dev.pem", hosts.first().identityFile)
        assertEquals(listOf("server1", "server2"), hosts.drop(1).map { it.alias })
        assertEquals(listOf("10.0.0.10", "10.0.0.10"), hosts.drop(1).map { it.hostName })
        assertEquals(listOf("ops", "ops"), hosts.drop(1).map { it.user })
        assertEquals(listOf(2200, 2200), hosts.drop(1).map { it.port })
    }

    @Test
    fun handlesQuotedValuesAndInvalidPorts() {
        val hosts = OpenSshConfigParser.parse(
            """
            Host "server with spaces"
              HostName "192.168.1.100"
              User "admin user"
              Port nope
            """.trimIndent()
        )

        assertEquals(1, hosts.size)
        assertEquals("server with spaces", hosts.first().alias)
        assertEquals("192.168.1.100", hosts.first().hostName)
        assertEquals("admin user", hosts.first().user)
        assertEquals(22, hosts.first().port)
    }

    @Test
    fun preservesHashesInsideTokensQuotesAndEscapes() {
        val host = OpenSshConfigParser.parse(
            """
            Host hash#alias
              HostName host#name.example
              User "deploy#user"
              IdentityFile ~/.ssh/key\#prod # inline comment
            """.trimIndent()
        ).single()

        assertEquals("hash#alias", host.alias)
        assertEquals("host#name.example", host.hostName)
        assertEquals("deploy#user", host.user)
        assertEquals("~/.ssh/key#prod", host.identityFile)
    }

    @Test
    fun parsesSingleHopProxyJumpAliases() {
        val hosts = OpenSshConfigParser.parse(
            """
            Host app
              HostName app.internal
              ProxyJump deploy@bastion:2222

            Host skipped
              HostName skipped.internal
              ProxyJump first,second
            """.trimIndent()
        )

        assertEquals(2, hosts.size)
        assertEquals("bastion", hosts.first { it.alias == "app" }.proxyJumpAlias)
        assertEquals(null, hosts.first { it.alias == "skipped" }.proxyJumpAlias)
    }

    @Test
    fun parsesOpenSshPortForwards() {
        val host = OpenSshConfigParser.parse(
            """
            Host app
              HostName app.internal
              LocalForward 127.0.0.1:15432 db.internal:5432
              LocalForward 18080 web.internal 80
              RemoteForward 0.0.0.0:10022 127.0.0.1:22
              DynamicForward 127.0.0.1:1080
            """.trimIndent()
        ).single()

        assertEquals(4, host.forwards.size)
        assertEquals(PortForwardType.Local, host.forwards[0].type)
        assertEquals("127.0.0.1", host.forwards[0].localHost)
        assertEquals(15432, host.forwards[0].localPort)
        assertEquals("db.internal", host.forwards[0].remoteHost)
        assertEquals(5432, host.forwards[0].remotePort)
        assertEquals(PortForwardType.Local, host.forwards[1].type)
        assertEquals("web.internal", host.forwards[1].remoteHost)
        assertEquals(80, host.forwards[1].remotePort)
        assertEquals(PortForwardType.Remote, host.forwards[2].type)
        assertEquals("127.0.0.1", host.forwards[2].localHost)
        assertEquals(22, host.forwards[2].localPort)
        assertEquals("0.0.0.0", host.forwards[2].remoteHost)
        assertEquals(10022, host.forwards[2].remotePort)
        assertEquals(PortForwardType.DynamicSocks, host.forwards[3].type)
        assertEquals(1080, host.forwards[3].localPort)
    }

    @Test
    fun appliesWildcardDefaultsToFollowingConcreteHosts() {
        val host = OpenSshConfigParser.parse(
            """
            Host *
              User deploy
              Port 2200
              IdentityFile ~/.ssh/default_ed25519
              DynamicForward 1080

            Host app
              HostName app.internal
            """.trimIndent()
        ).single()

        assertEquals("deploy", host.user)
        assertEquals(2200, host.port)
        assertEquals("~/.ssh/default_ed25519", host.identityFile)
        assertEquals(1, host.forwards.size)
        assertEquals(PortForwardType.DynamicSocks, host.forwards.single().type)
        assertEquals(1080, host.forwards.single().localPort)
    }

    @Test
    fun rejectsMissingAndInvalidHostNames() {
        val hosts = OpenSshConfigParser.parse(
            """
            Host missing-hostname
              User root

            Host bad-host
              HostName https://example.com/root
            """.trimIndent()
        )

        assertTrue(hosts.isEmpty())
    }
}
