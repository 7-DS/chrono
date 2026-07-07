package com.chrono.ssh.core.data

import com.chrono.ssh.core.model.CpuMetrics
import com.chrono.ssh.core.model.DiskMetrics
import com.chrono.ssh.core.model.DockerSummary
import com.chrono.ssh.core.model.MemoryMetrics
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.NetworkHistory
import com.chrono.ssh.core.model.NetworkInterfaceMetric
import com.chrono.ssh.core.model.NetworkMetrics
import com.chrono.ssh.core.model.ProcessSummary
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.model.ServiceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricHistoryRetentionPolicyTest {
    @Test
    fun keepsTwentyFourHoursDetailedAndSevenDaysHourlySummary() {
        val now = 7L * 24L * 60L * 60L * 1000L
        val tenMinutes = 10L * 60L * 1000L
        val samples = (0..7 * 24 * 6).map { index ->
            snapshot(now - index * tenMinutes)
        }

        val retained = MetricHistoryRetentionPolicy.retain(samples, now)
        val detailedCutoff = now - 24L * 60L * 60L * 1000L
        val summaryCutoff = now - 7L * 24L * 60L * 60L * 1000L
        val detailed = retained.filter { it.collectedAtEpochMillis >= detailedCutoff }
        val summarized = retained.filter { it.collectedAtEpochMillis in summaryCutoff until detailedCutoff }

        assertEquals(145, detailed.size)
        assertTrue(summarized.size in 143..144)
        assertFalse(retained.any { it.collectedAtEpochMillis < summaryCutoff })
        assertEquals(retained.sortedBy { it.collectedAtEpochMillis }, retained)
    }

    @Test
    fun ignoresUnavailableAndDuplicateSamples() {
        val now = 1_000_000L
        val available = snapshot(now)
        val unavailable = emptySnapshot(now + 1L)

        val retained = MetricHistoryRetentionPolicy.retain(
            listOf(available, available, unavailable),
            nowEpochMillis = now + 1L
        )

        assertEquals(listOf(available), retained)
    }

    @Test
    fun seedPolicyUsesLatestUsefulHistorySample() {
        val older = snapshot(1_000L)
        val newer = snapshot(2_000L)
        val unavailable = emptySnapshot(3_000L)

        val seeded = MetricSnapshotSeedPolicy.seed("server", listOf(older, unavailable, newer)) { id ->
            snapshot(4_000L, uptime = "--").copy(serverId = id)
        }

        assertEquals(newer, seeded)
    }

    @Test
    fun seedPolicyUsesInventoryOnlyHistorySample() {
        val inventoryOnly = snapshot(3_000L, uptime = "--").copy(
            cpu = CpuMetrics(0, 0, "Linux CPU", 0, 0, 0, 0, 0, 0f, 0f, 0f, emptyList()),
            memory = MemoryMetrics(0, 0, 0, 0),
            disk = DiskMetrics(0f, 0f, "--", "--"),
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("eth0", "--", "--", "--", "--", "--", 0.5f),
                interfaces = emptyList(),
                history = NetworkHistory("--", "--", emptyList(), emptyList(), emptyList())
            ),
            processes = ProcessSummary(total = 22, running = 2, topProcess = "sshd"),
            services = ServiceSummary(total = 8, failed = 1),
            docker = DockerSummary(containers = 1, running = 1)
        )

        val seeded = MetricSnapshotSeedPolicy.seed("server", listOf(inventoryOnly)) { id ->
            snapshot(4_000L, uptime = "--").copy(serverId = id)
        }

        assertEquals(inventoryOnly, seeded)
    }

    @Test
    fun retentionKeepsInventoryOnlySamples() {
        val inventoryOnly = snapshot(3_000L, uptime = "--").copy(
            cpu = CpuMetrics(0, 0, "Linux CPU", 0, 0, 0, 0, 0, 0f, 0f, 0f, emptyList()),
            memory = MemoryMetrics(0, 0, 0, 0),
            disk = DiskMetrics(0f, 0f, "--", "--"),
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("eth0", "--", "--", "--", "--", "--", 0.5f),
                interfaces = emptyList(),
                history = NetworkHistory("--", "--", emptyList(), emptyList(), emptyList())
            ),
            processes = ProcessSummary(total = 4, running = 1, topProcess = "sshd")
        )

        assertEquals(
            listOf(inventoryOnly),
            MetricHistoryRetentionPolicy.retain(listOf(inventoryOnly), nowEpochMillis = 3_000L)
        )
    }

    private fun emptySnapshot(at: Long): MetricSnapshot {
        return MetricSnapshot(
            serverId = "server",
            status = ServerStatus.Unknown,
            latencyMs = null,
            uptime = "--",
            cpu = CpuMetrics(0, 0, "Linux CPU", 0, 0, 0, 0, 0, 0f, 0f, 0f, emptyList()),
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
            collectedAtEpochMillis = at
        )
    }

    private fun snapshot(
        at: Long,
        uptime: String = "1 day"
    ): MetricSnapshot {
        return MetricSnapshot(
            serverId = "server",
            status = ServerStatus.Online,
            latencyMs = null,
            uptime = uptime,
            cpu = CpuMetrics(usagePercent = 1, cores = 1, model = "CPU", userPercent = 1, systemPercent = 0, nicePercent = 0, ioWaitPercent = 0, stealPercent = 0, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f, recentLoad = listOf(0.1f)),
            memory = MemoryMetrics(usedMb = 1, totalMb = 2, swapUsedMb = 0, swapTotalMb = 0),
            disk = DiskMetrics(usedGb = 1f, totalGb = 2f, readPerSecond = "0 B/s", writePerSecond = "0 B/s", readTotal = "1 M", writeTotal = "1 M"),
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("eth0", "10.0.0.1/24", "0 B/s", "0 B/s", "1 M", "1 M", 0.5f),
                interfaces = emptyList(),
                history = NetworkHistory("1 M", "1 M", emptyList(), emptyList(), emptyList())
            ),
            processes = ProcessSummary(total = 1, running = 1, topProcess = "init"),
            services = ServiceSummary(total = 1, failed = 0),
            docker = DockerSummary(containers = 0, running = 0),
            collectedAtEpochMillis = at
        )
    }
}
