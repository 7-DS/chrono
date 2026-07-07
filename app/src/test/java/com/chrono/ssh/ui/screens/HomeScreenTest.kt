package com.chrono.ssh.ui.screens

import com.chrono.ssh.core.model.DiskMetrics
import com.chrono.ssh.core.model.CpuMetrics
import com.chrono.ssh.core.model.DockerSummary
import com.chrono.ssh.core.model.MemoryMetrics
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.NetworkHistory
import com.chrono.ssh.core.model.NetworkInterfaceMetric
import com.chrono.ssh.core.model.NetworkMetrics
import com.chrono.ssh.core.model.ProcessSummary
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerCardDiskMode
import com.chrono.ssh.core.model.ServerCardNetworkMode
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.model.ServiceSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScreenTest {
    @Test
    fun homeFleetFiltersIncludeGroupsBeforeTags() {
        val servers = listOf(
            server("a", group = "Cloud", tags = listOf("All", "prod"), favorite = true),
            server("b", group = "Lab", tags = listOf("dev", "prod")),
            server("c", group = "Cloud", tags = listOf("edge"))
        )

        assertEquals(listOf("All", "Favorites", "Cloud", "Lab", "prod", "dev", "edge"), homeFleetFilters(servers))
    }

    @Test
    fun filterServersByHomeFilterMatchesGroupOrTag() {
        val cloud = server("a", group = "Cloud", tags = listOf("prod"), favorite = true)
        val lab = server("b", group = "Lab", tags = listOf("prod"))
        val edge = server("c", group = "Edge", tags = listOf("dmz"))

        assertEquals(listOf(cloud, lab), filterServersByHomeFilter(listOf(cloud, lab, edge), "prod"))
        assertEquals(listOf(cloud), filterServersByHomeFilter(listOf(cloud, lab, edge), "Cloud"))
        assertEquals(listOf(cloud), filterServersByHomeFilter(listOf(cloud, lab, edge), "Favorites"))
        assertEquals(listOf(cloud, lab, edge), filterServersByHomeFilter(listOf(cloud, lab, edge), "All"))
    }

    @Test
    fun homeServerRowsKeepHostsWithoutSnapshotsVisible() {
        val cloud = server("a", group = "Cloud", tags = listOf("prod"))
        val lab = server("b", group = "Lab", tags = listOf("prod"))

        assertEquals(listOf(cloud to null, lab to null), homeServerRows(listOf(cloud, lab), emptyMap(), "All"))
    }

    @Test
    fun compactAddHostVisibilityShowsOnlyWhenScrollingDown() {
        assertEquals(true, compactAddHostVisibility(false, previousScroll = 20, currentScroll = 40))
        assertEquals(false, compactAddHostVisibility(false, previousScroll = 40, currentScroll = 44))
    }

    @Test
    fun compactAddHostVisibilityHidesWhenScrollingUp() {
        assertEquals(false, compactAddHostVisibility(true, previousScroll = 40, currentScroll = 20))
        assertEquals(true, compactAddHostVisibility(true, previousScroll = 40, currentScroll = 36))
    }

    @Test
    fun diskUsageLabelShowsUsedAndTotalCapacity() {
        assertEquals(
            "32.00 G / 64.00 G",
            DiskMetrics(usedGb = 32f, totalGb = 64f, readPerSecond = "1 M/s", writePerSecond = "2 M/s").homeUsageLabel()
        )
    }

    @Test
    fun diskUsageLabelDoesNotInventUnknownCapacity() {
        assertEquals("-- / --", DiskMetrics(usedGb = 0f, totalGb = 0f, readPerSecond = "--", writePerSecond = "--").homeUsageLabel())
    }

    @Test
    fun diskUsageRingRequiresKnownCapacity() {
        assertEquals(false, DiskMetrics(0f, 0f, "1.00 M/s", "2.00 M/s").homeShouldUseUsageRing(ServerCardDiskMode.Usage))
        assertEquals(true, DiskMetrics(32f, 64f, "--", "--").homeShouldUseUsageRing(ServerCardDiskMode.Usage))
        assertEquals(false, DiskMetrics(32f, 64f, "--", "--").homeShouldUseUsageRing(ServerCardDiskMode.Rates))
    }

    @Test
    fun serverCardTreatsDiskCapacityAsCollectedMetricsEvenWhenUsedIsZero() {
        assertEquals(true, emptySnapshot().copy(disk = DiskMetrics(0f, 64f, "--", "--")).hasCollectedMetrics())
    }

    @Test
    fun serverCardDoesNotTreatUptimeOnlyAsCollectedComponentMetrics() {
        assertEquals(false, noComponentSnapshot().copy(uptime = "1 day").hasCollectedMetrics())
    }

    @Test
    fun serverCardTreatsProcessServiceAndContainerDetailsAsCollectedMetrics() {
        assertEquals(true, noComponentSnapshot().copy(processes = ProcessSummary(4, 1, "sshd")).hasCollectedMetrics())
        assertEquals(true, noComponentSnapshot().copy(services = ServiceSummary(12, 0)).hasCollectedMetrics())
        assertEquals(true, noComponentSnapshot().copy(docker = DockerSummary(2, 1)).hasCollectedMetrics())
    }

    @Test
    fun serverCardDetectsCollectedComponentsIndividually() {
        assertEquals(false, emptySnapshot().copy(cpu = CpuMetrics(0, 2, "Linux CPU", 0, 0, 0, 0, 0, 0f, 0f, 0f, emptyList())).hasCpuMetrics())
        assertEquals(true, emptySnapshot().copy(cpu = CpuMetrics(0, 2, "AMD EPYC", 0, 0, 0, 0, 0, 0f, 0f, 0f, emptyList())).hasCpuMetrics())
        assertEquals(true, emptySnapshot().copy(memory = MemoryMetrics(0, 512, 0, 0)).hasMemoryMetrics())
        assertEquals(false, noComponentSnapshot().hasMemoryMetrics())
        assertEquals(false, emptySnapshot().copy(network = emptySnapshot().network.copy(
            primaryInterface = emptySnapshot().network.primaryInterface.copy(uploadTotal = "0.00 B", downloadTotal = "0.00 B")
        )).hasNetworkMetrics())
        assertEquals(true, emptySnapshot().copy(network = emptySnapshot().network.copy(
            primaryInterface = emptySnapshot().network.primaryInterface.copy(uploadTotal = "5 MB")
        )).hasNetworkMetrics())
    }

    @Test
    fun serverCardDiskLabelsFallbackToUsefulValuesForSelectedMode() {
        val disk = DiskMetrics(usedGb = 32f, totalGb = 64f, readPerSecond = "--", writePerSecond = "--", readTotal = "5.00 G", writeTotal = "7.00 G")

        assertEquals("--", disk.homeReadLabel(ServerCardDiskMode.Rates))
        assertEquals("--", disk.homeWriteLabel(ServerCardDiskMode.Rates))
        assertEquals("5.00 G", disk.homeReadLabel(ServerCardDiskMode.Totals))
        assertEquals("7.00 G", disk.homeWriteLabel(ServerCardDiskMode.Totals))
        assertEquals("--", DiskMetrics(32f, 64f, "--", "--").homeReadLabel(ServerCardDiskMode.Rates))
        assertEquals("64.00 G", DiskMetrics(32f, 64f, "--", "--").homeWriteLabel(ServerCardDiskMode.Totals))
    }

    @Test
    fun serverCardNetworkLabelsFallbackToUsefulValuesForSelectedMode() {
        val iface = NetworkInterfaceMetric("eth0", "--", "--", "--", "12.00 G", "18.00 G", 0.5f)

        assertEquals("12.00 G", iface.homeUploadLabel(ServerCardNetworkMode.Rates))
        assertEquals("18.00 G", iface.homeDownloadLabel(ServerCardNetworkMode.Rates))
        assertEquals("--", NetworkInterfaceMetric("eth0", "--", "--", "--", "0.00 B", "0.00 B", 0f).homeUploadLabel(ServerCardNetworkMode.Rates))
    }

    private fun server(id: String, group: String, tags: List<String>, favorite: Boolean = false) = ServerProfile(
        id = id,
        name = id,
        host = "$id.test",
        port = 22,
        username = "root",
        group = group,
        tags = tags,
        osName = "linux",
        osVersion = "",
        accent = ServerAccent("cyan", 0xff00ffff),
        credentialId = null,
        terminalProfileId = "default",
        monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 2, useOptionalAgent = false),
        favorite = favorite
    )

    private fun emptySnapshot() = MetricSnapshot(
        serverId = "server-1",
        status = ServerStatus.Online,
        latencyMs = null,
        uptime = "--",
        cpu = CpuMetrics(0, 2, "Linux CPU", 0, 0, 0, 0, 0, 0f, 0f, 0f, emptyList()),
        memory = MemoryMetrics(0, 0, 0, 0),
        disk = DiskMetrics(0f, 0f, "--", "--"),
        network = NetworkMetrics(
            primaryInterface = NetworkInterfaceMetric("eth0", "--", "--", "--", "--", "--", 0.5f),
            interfaces = emptyList(),
            history = NetworkHistory("--", "--", emptyList(), emptyList(), emptyList())
        ),
        processes = ProcessSummary(0, 0, "--"),
        services = ServiceSummary(0, 0),
        docker = DockerSummary(0, 0),
        collectedAtEpochMillis = 200
    )

    private fun noComponentSnapshot() = emptySnapshot().copy(
        cpu = CpuMetrics(0, 0, "Unavailable", 0, 0, 0, 0, 0, 0f, 0f, 0f, emptyList())
    )
}
