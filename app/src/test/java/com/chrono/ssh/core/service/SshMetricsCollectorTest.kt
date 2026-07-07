package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SshMetricsCollectorTest {
    @Test
    fun vnStatCommandIncludesVersionTolerantJsonRangeForms() {
        val command = SshMetricsCollector().vnStatCommandForTest(listOf("eth0"), includeExtendedFallback = true)

        assertTrue(command.contains("vnstat --json --all"))
        assertTrue(command.contains("vnstat --json d"))
        assertTrue(command.contains("vnstat -d --json"))
        assertTrue(command.contains("vnstat --json w"))
        assertTrue(command.contains("vnstat -w --json"))
        assertTrue(command.contains("vnstat --json m"))
        assertTrue(command.contains("vnstat -m --json"))
        assertTrue(command.contains("vnstat --json y"))
        assertTrue(command.contains("vnstat -y --json"))
        assertTrue(command.contains("vnstat --json --days"))
        assertTrue(command.contains("vnstat --json --weeks"))
        assertTrue(command.contains("vnstat --json --months"))
        assertTrue(command.contains("vnstat --json --years"))
        assertTrue(command.contains("vnstat --json d -i 'eth0'"))
        assertTrue(command.contains("vnstat -d --json -i 'eth0'"))
        assertTrue(command.contains("vnstat --json w -i 'eth0'"))
        assertTrue(command.contains("vnstat -w --json -i 'eth0'"))
        assertTrue(command.contains("vnstat --json --days -i 'eth0'"))
        assertTrue(command.contains("vnstat --json --weeks -i 'eth0'"))
        assertTrue(command.contains("vnstat --json --months -i 'eth0'"))
        assertTrue(command.contains("vnstat --json --years -i 'eth0'"))
        assertTrue(command.contains("vnstat --json --all"))
        assertTrue(command.contains("chrono_timeout 1 vnstat --json --all"))
        assertEquals(false, command.contains("vnstat --json d 30"))
        assertEquals(false, command.contains("vnstat --json w 12"))
        assertEquals(false, command.contains("vnstat --json m 12"))
    }

    @Test
    fun vnStatCommandUsesAllJsonAndKeepsMoreThanEightInterfacesReachable() {
        val interfaces = (0 until 20).map { "eth$it" }
        val command = SshMetricsCollector().vnStatCommandForTest(interfaces, includeExtendedFallback = false)

        assertTrue(command.contains("vnstat --json --all"))
        assertTrue(command.contains("vnstat --json -i 'eth0'"))
        assertTrue(command.contains("vnstat --json -i 'eth19'"))
    }

    @Test
    fun vnStatExtendedFallbackCapsPerInterfaceCompatibilityCommands() {
        val command = SshMetricsCollector().vnStatCommandForTest(
            interfaceNames = listOf("eth0", "eth1", "eth2"),
            includeExtendedFallback = true
        )

        assertTrue(command.contains("vnstat --json --all"))
        assertTrue(command.contains("vnstat --json d -i 'eth0'"))
        assertEquals(false, command.contains("vnstat --json d -i 'eth1'"))
        assertEquals(false, command.contains("vnstat --json d -i 'eth2'"))
    }

    @Test
    fun collectionDiagnosticUsesLabelsInsteadOfRawCommands() {
        val diagnostic = SshMetricsCollector().collectionDiagnosticForTest(
            listOf(
                "vnStat fallback timeout",
                "PATH=\$PATH:/usr/sbin:/usr/bin:/sbin:/bin; vnstat --json --all"
            )
        )

        assertTrue(diagnostic.contains("Metrics partial"))
        assertTrue(diagnostic.contains("vnStat fallback timeout"))
        assertEquals(false, diagnostic.contains("PATH="))
        assertEquals(false, diagnostic.contains("vnstat --json"))
    }

    @Test
    fun vnStatDiagnosticIncludesTriedInterfacesAndParseStatus() {
        val diagnostic = SshMetricsCollector().vnStatDiagnosticForTest(
            interfaceNames = listOf("eth0", "ens3"),
            output = """{"jsonversion":"2","interfaces":[{"name":"eth0","traffic":{"total":{"rx":0,"tx":0}}}]}""",
            parsed = null
        )

        assertTrue(diagnostic.contains("interfaces=2:eth0,ens3"))
        assertTrue(diagnostic.contains("installed or callable"))
        assertTrue(diagnostic.contains("no parsed totals"))
    }

    @Test
    fun collectKeepsUsefulMetricsWhenOptionalCommandsThrow() = runBlocking {
        val collector = SshMetricsCollector()
        val snapshot = collector.collect(testServer(), PartialFailureSession())

        assertTrue(snapshot.cpu.usagePercent > 0)
        assertEquals(3072, snapshot.memory.usedMb)
        assertEquals("4.00 K/s", snapshot.network.primaryInterface.downloadRate)
        assertEquals("8.00 K/s", snapshot.network.primaryInterface.uploadRate)
        assertEquals(0, snapshot.docker.containers)
        assertEquals(0f, snapshot.disk.totalGb, 0.01f)
        assertTrue(collector.lastCollectionDiagnostic.contains("disk capacity"))
        assertTrue(collector.lastVnStatDiagnostic.contains("skipped"))
        assertTrue("slow inventory commands should not run during fast refresh", snapshot.processes.total == 0)
    }

    @Test
    fun collectUsesShortTransportTimeoutForFastRefresh() = runBlocking {
        val session = PartialFailureSession()

        SshMetricsCollector().collect(testServer(), session)

        assertEquals(listOf(3L), session.timeoutSeconds)
    }

    @Test
    fun collectReportsMissingFastMetricSectionsInsteadOfSilentZeros() = runBlocking {
        val collector = SshMetricsCollector()
        val session = EmptyFastBundleSession()

        val snapshot = collector.collect(testServer(), session)

        assertEquals(0, snapshot.memory.totalMb)
        assertTrue(collector.lastCollectionDiagnostic.contains("STAT1"))
        assertTrue(collector.lastCollectionDiagnostic.contains("MEMINFO"))
        assertEquals(listOf(3L), session.timeoutSeconds)
    }

    @Test
    fun fastMetricsCommandUsesSubSecondSamplingWindow() {
        val command = SshMetricsCollector().fastMetricsCommandForTest()

        assertTrue(command.contains("sleep 0.25"))
        assertTrue(command.contains("chrono_timeout 1"))
        assertTrue(command.contains("kill \"${'$'}pid\""))
        assertTrue(command.contains("__CHRONO_STATFS__"))
        assertTrue(command.contains("chrono_timeout 1 ip -o -4 addr show scope global"))
    }

    @Test
    fun collectDetailsRestoresSlowInventoryMetricsOutsideFastRefresh() = runBlocking {
        val collector = SshMetricsCollector()
        val snapshot = collector.collectDetails(testServer(), DetailSession())

        assertEquals("postgres", snapshot.processes.topProcess)
        assertEquals("postgres", snapshot.processes.items.first().command)
        assertEquals(2, snapshot.services.total)
        assertEquals(1, snapshot.services.failed)
        assertEquals("bad.service", snapshot.services.failedItems.single().unit)
        assertEquals(1, snapshot.docker.containers)
        assertEquals("hbbs", snapshot.docker.items.single().name)
    }

    @Test
    fun collectDetailsKeepsContainerRowsWhenStatsFails() = runBlocking {
        val collector = SshMetricsCollector()
        val snapshot = collector.collectDetails(testServer(), ContainerStatsFailureSession())

        assertEquals(1, snapshot.docker.containers)
        assertEquals(1, snapshot.docker.running)
        assertEquals("hbbs", snapshot.docker.items.single().name)
        assertTrue(collector.lastCollectionDiagnostic.contains("container stats"))
    }

    @Test
    fun collectDetailsUsesInventoryTransportTimeoutForOptionalCommands() = runBlocking {
        val session = DetailSession()

        SshMetricsCollector().collectDetails(testServer(), session)

        assertTrue(session.timeoutSeconds.isNotEmpty())
        assertTrue(session.timeoutSeconds.all { it == 5L })
    }

    @Test
    fun processListCommandProbesPortablePsVariants() {
        val command = SshMetricsCollector().processListCommandForTest()

        assertTrue(command.contains("PATH=\$PATH:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"))
        assertTrue(command.contains("ps -eo pid=,ppid=,user=,stat=,pcpu=,pmem=,rss=,vsz=,etimes=,args="))
        assertTrue(command.contains("ps auxww"))
        assertTrue(command.contains("ps -eo pid=,stat=,pcpu=,pmem=,args="))
        assertTrue(command.contains("ps -eo stat=,pcpu=,pmem=,comm="))
        assertTrue(command.contains("ps 2>/dev/null"))
    }

    @Test
    fun detailCommandsExpandPathAndProbeSudoOnlyContainerRuntimes() {
        val collector = SshMetricsCollector()
        val systemd = collector.systemdListCommandForTest(failedOnly = false)
        val containers = collector.containerListCommandForTest()
        val stats = collector.containerStatsCommandForTest()
        val images = collector.containerImagesCommandForTest()

        assertTrue(systemd.contains("PATH=\$PATH:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"))
        assertTrue(systemd.contains("systemctl list-units --type=service --all --no-legend --plain"))
        assertTrue(containers.contains("sudo -n command -v docker"))
        assertTrue(containers.contains("sudo -n command -v podman"))
        assertTrue(stats.contains("sudo -n docker stats --no-stream"))
        assertTrue(stats.contains("sudo -n podman stats --no-stream"))
        assertTrue(images.contains("docker images --format"))
        assertTrue(images.contains("podman images --format"))
        assertTrue(images.contains("sudo -n docker images"))
        assertTrue(images.contains("sudo -n podman images"))
    }

    @Test
    fun diskRateSelectionPrefersLogicalAggregateDevices() {
        val names = SshMetricsCollector().diskSampleNamesForTest(listOf("sda", "sdb", "dm-0", "md0"))

        assertEquals(listOf("dm-0", "md0"), names)
    }

    @Test
    fun diskRateSelectionKeepsPhysicalDevicesWhenNoLogicalAggregateExists() {
        val names = SshMetricsCollector().diskSampleNamesForTest(listOf("vda", "nvme0n1", "xvda"))

        assertEquals(listOf("nvme0n1", "vda", "xvda"), names)
    }

    @Test
    fun diskRatesUseActivePhysicalCountersWhenLogicalAggregateIsIdle() {
        val first = """
           253       0 dm-0 100 0 4096 1 100 0 4096 1 0 0 0 0 0 0
           252       0 vda 100 0 2048 1 100 0 4096 1 0 0 0 0 0 0
        """.trimIndent()
        val second = """
           253       0 dm-0 100 0 4096 1 100 0 4096 1 0 0 0 0 0 0
           252       0 vda 100 0 4096 1 100 0 8192 1 0 0 0 0 0 0
        """.trimIndent()

        val labels = SshMetricsCollector().diskRateLabelsForTest(first, second, elapsedSeconds = 1f)

        assertEquals("1.00 M/s", labels[0])
        assertEquals("2.00 M/s", labels[1])
    }

    @Test
    fun diskRatesUsePartitionCountersWhenHostExposesNoWholeDisk() {
        val first = """
           252       1 vda1 100 0 2048 1 100 0 4096 1 0 0 0 0 0 0
           259       1 nvme0n1p1 100 0 2048 1 100 0 4096 1 0 0 0 0 0 0
        """.trimIndent()
        val second = """
           252       1 vda1 100 0 4096 1 100 0 8192 1 0 0 0 0 0 0
           259       1 nvme0n1p1 100 0 4096 1 100 0 8192 1 0 0 0 0 0 0
        """.trimIndent()

        val labels = SshMetricsCollector().diskRateLabelsForTest(first, second, elapsedSeconds = 1f)

        assertEquals("2.00 M/s", labels[0])
        assertEquals("4.00 M/s", labels[1])
    }

    @Test
    fun diskRatesPreferActiveLogicalAggregateWhenItHasCounters() {
        val first = """
           253       0 dm-0 100 0 4096 1 100 0 4096 1 0 0 0 0 0 0
           252       0 vda 100 0 2048 1 100 0 4096 1 0 0 0 0 0 0
        """.trimIndent()
        val second = """
           253       0 dm-0 100 0 6144 1 100 0 8192 1 0 0 0 0 0 0
           252       0 vda 100 0 8192 1 100 0 16384 1 0 0 0 0 0 0
        """.trimIndent()

        val labels = SshMetricsCollector().diskRateLabelsForTest(first, second, elapsedSeconds = 1f)

        assertEquals("1.00 M/s", labels[0])
        assertEquals("2.00 M/s", labels[1])
    }

    @Test
    fun networkMetricsPreserveAllInterfacesAndNormalizeAddressAliases() {
        val first = """
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
               lo: 1000 0 0 0 0 0 0 0 2000 0 0 0 0 0 0 0
             eth0: 1000 0 0 0 0 0 0 0 2000 0 0 0 0 0 0 0
            wlan0: 3000 0 0 0 0 0 0 0 4000 0 0 0 0 0 0 0
            tailscale0: 5000 0 0 0 0 0 0 0 6000 0 0 0 0 0 0 0
        """.trimIndent()
        val second = """
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
               lo: 2000 0 0 0 0 0 0 0 3000 0 0 0 0 0 0 0
             eth0: 1049576 0 0 0 0 0 0 0 2099152 0 0 0 0 0 0 0
            wlan0: 527288 0 0 0 0 0 0 0 1052576 0 0 0 0 0 0 0
            tailscale0: 25000 0 0 0 0 0 0 0 46000 0 0 0 0 0 0 0
        """.trimIndent()
        val addresses = """
            2: eth0@if3    inet 10.0.0.5/24 brd 10.0.0.255 scope global eth0
            3: wlan0       inet 192.168.1.20/24 brd 192.168.1.255 scope global wlan0
            4: tailscale0  inet 100.64.0.7/32 scope global tailscale0
        """.trimIndent()

        val metrics = SshMetricsCollector().networkMetricsForTest(first, second, addresses)

        assertEquals(listOf("lo", "eth0", "wlan0", "tailscale0"), metrics.interfaces.map { it.name })
        assertEquals("10.0.0.5/24", metrics.interfaces.first { it.name == "eth0" }.address)
        assertEquals("1.52 M/s", metrics.primaryInterface.downloadRate)
        assertEquals("3.04 M/s", metrics.primaryInterface.uploadRate)
        assertEquals("1.53 M", metrics.primaryInterface.downloadTotal)
        assertEquals("3.05 M", metrics.primaryInterface.uploadTotal)
    }

    private fun testServer(): ServerProfile {
        return ServerProfile(
            id = "server-1",
            name = "Test",
            host = "127.0.0.1",
            port = 22,
            username = "tester",
            group = "Test",
            tags = listOf("All"),
            osName = "Linux",
            osVersion = "Test",
            accent = ServerAccent("Test", 0xff00ff00),
            credentialId = null,
            terminalProfileId = "terminal",
            monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 2, useOptionalAgent = false)
        )
    }

    private class PartialFailureSession : SshSession {
        override val id: String = "session-1"
        override val serverId: String = "server-1"
        override val transcriptPreview: String = ""
        val timeoutSeconds = mutableListOf<Long>()
        private var procStatCalls = 0
        private var netDevCalls = 0

        override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult {
            this.timeoutSeconds += timeoutSeconds
            val output = when {
                command.contains("__CHRONO_STAT1__") -> fastMetricsBundle()
                command.startsWith("PATH=") -> throw IllegalStateException("vnstat unavailable")
                command.startsWith("ps ") -> "R 12.0 1.0 sshd"
                command.startsWith("systemctl list-units") -> "ssh.service loaded active running OpenSSH"
                command.startsWith("systemctl --failed") -> ""
                command.contains("docker ps") -> "docker\t0e9e2ef860d2\thbbs\trustdesk/rustdesk-server:latest\tUp 2 hours\trunning"
                else -> ""
            }
            return CommandResult(command, 0, output, "")
        }

        override suspend fun resizeTerminal(columns: Int, rows: Int) = Unit
        override suspend fun writeTerminal(input: String) = Unit
        override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) = Unit
        override suspend fun close() = Unit

        private fun fastMetricsBundle(): String {
            return listOf(
                "__CHRONO_STAT1__",
                procStat(++procStatCalls),
                "__CHRONO_NET1__",
                netDev(++netDevCalls),
                "__CHRONO_DISKSTATS1__",
                "__CHRONO_STAT2__",
                procStat(++procStatCalls),
                "__CHRONO_NET2__",
                netDev(++netDevCalls),
                "__CHRONO_DISKSTATS2__",
                "__CHRONO_MEMINFO__",
                "MemTotal:       4194304 kB\nMemAvailable:   1048576 kB\nSwapTotal:            0 kB\nSwapFree:             0 kB",
                "__CHRONO_UPTIME__",
                "7200.00 120000.00",
                "__CHRONO_LOADAVG__",
                "0.12 0.20 0.30 1/100 42",
                "__CHRONO_CPUINFO__",
                "processor: 0\nmodel name: Test CPU",
                "__CHRONO_DF__",
                "__CHRONO_LSBLK__",
                "__CHRONO_ADDR4__",
                "2: eth0    inet 10.0.0.5/24 brd 10.0.0.255 scope global eth0",
                "__CHRONO_END__"
            ).joinToString("\n")
        }

        private fun procStat(call: Int): String {
            return if (call == 1) {
                "cpu  100 0 50 850 0 0 0 0 0 0\ncpu0 100 0 50 850 0 0 0 0 0 0"
            } else {
                "cpu  160 0 90 950 0 0 0 0 0 0\ncpu0 160 0 90 950 0 0 0 0 0 0"
            }
        }

        private fun netDev(call: Int): String {
            return if (call == 1) {
                """
                Inter-|   Receive                                                |  Transmit
                 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                 eth0: 1000 0 0 0 0 0 0 0 2000 0 0 0 0 0 0 0
                """.trimIndent()
            } else {
                """
                Inter-|   Receive                                                |  Transmit
                 face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                 eth0: 2024 0 0 0 0 0 0 0 4048 0 0 0 0 0 0 0
                """.trimIndent()
            }
        }
    }

    private open class DetailSession : SshSession {
        override val id: String = "session-2"
        override val serverId: String = "server-1"
        override val transcriptPreview: String = ""
        val timeoutSeconds = mutableListOf<Long>()

        override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult {
            this.timeoutSeconds += timeoutSeconds
            val output = when {
                command.contains("cat /proc/net/dev") -> """
                    Inter-|   Receive                                                |  Transmit
                     face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed
                     eth0: 2024 0 0 0 0 0 0 0 4048 0 0 0 0 0 0 0
                """.trimIndent()
                command.contains("ps -eo") || command.contains("ps auxww") -> "R 12.0 1.0 postgres\nS 1.0 0.5 sshd"
                command.contains("systemctl list-units") -> "ssh.service loaded active running OpenSSH\nbad.service loaded failed failed Bad"
                command.contains("systemctl --failed") -> "bad.service loaded failed failed Bad"
                command.contains("docker ps") -> "docker\t0e9e2ef860d2\thbbs\trustdesk/rustdesk-server:latest\tUp 2 hours\trunning"
                command.startsWith("PATH=") -> ""
                else -> ""
            }
            return CommandResult(command, 0, output, "")
        }

        override suspend fun resizeTerminal(columns: Int, rows: Int) = Unit
        override suspend fun writeTerminal(input: String) = Unit
        override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) = Unit
        override suspend fun close() = Unit
    }

    private class ContainerStatsFailureSession : DetailSession() {
        override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult {
            if (command.contains("docker stats") || command.contains("podman stats")) {
                throw IllegalStateException("stats slow")
            }
            return super.execute(command, timeoutSeconds)
        }
    }

    private class EmptyFastBundleSession : SshSession {
        override val id: String = "session-empty"
        override val serverId: String = "server-1"
        override val transcriptPreview: String = ""
        val timeoutSeconds = mutableListOf<Long>()

        override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult {
            this.timeoutSeconds += timeoutSeconds
            return CommandResult(command, 0, "", "")
        }

        override suspend fun resizeTerminal(columns: Int, rows: Int) = Unit
        override suspend fun writeTerminal(input: String) = Unit
        override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) = Unit
        override suspend fun close() = Unit
    }
}
