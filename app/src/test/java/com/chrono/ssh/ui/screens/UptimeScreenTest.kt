package com.chrono.ssh.ui.screens

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
import org.junit.Assert.assertNull
import org.junit.Test

class UptimeScreenTest {
    @Test
    fun uptimeBucketsUseCollectedSamplesNotUptimeString() {
        val samples = listOf(
            snapshot(at = 0, status = ServerStatus.Online, uptime = "99 days"),
            snapshot(at = 60 * 60 * 1000L, status = ServerStatus.Offline, uptime = "99 days"),
            snapshot(at = 90 * 60 * 1000L, status = ServerStatus.Unknown, uptime = "99 days")
        )

        val buckets = uptimeBuckets(samples)

        assertEquals(UptimeBucketStatus.Up, buckets[45])
        assertEquals(UptimeBucketStatus.Down, buckets[46])
        assertEquals(UptimeBucketStatus.Unverified, buckets[47])
    }

    @Test
    fun uptimePercentIgnoresUnverifiedSamples() {
        val samples = listOf(
            snapshot(at = 0, status = ServerStatus.Online),
            snapshot(at = 1, status = ServerStatus.Offline),
            snapshot(at = 2, status = ServerStatus.Unknown)
        )

        assertEquals(50.0, uptimePercent(samples, 24L * 60L * 60L * 1000L) ?: -1.0, 0.001)
        assertNull(uptimePercent(listOf(snapshot(at = 0, status = ServerStatus.Unknown)), 24L * 60L * 60L * 1000L))
    }

    private fun snapshot(at: Long, status: ServerStatus, uptime: String = "1 day"): MetricSnapshot {
        return MetricSnapshot(
            serverId = "server",
            status = status,
            latencyMs = null,
            uptime = uptime,
            cpu = CpuMetrics(
                usagePercent = 0,
                cores = 1,
                model = "CPU",
                userPercent = 0,
                systemPercent = 0,
                nicePercent = 0,
                ioWaitPercent = 0,
                stealPercent = 0,
                load1 = 0f,
                load5 = 0f,
                load15 = 0f,
                recentLoad = emptyList()
            ),
            memory = MemoryMetrics(usedMb = 0, totalMb = 1, swapUsedMb = 0, swapTotalMb = 0),
            disk = DiskMetrics(usedGb = 0f, totalGb = 1f, readPerSecond = "--", writePerSecond = "--"),
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("eth0", "--", "--", "--", "--", "--", 0f),
                interfaces = emptyList(),
                history = NetworkHistory("--", "--", emptyList(), emptyList(), emptyList())
            ),
            processes = ProcessSummary(total = 0, running = 0, topProcess = "--"),
            services = ServiceSummary(total = 0, failed = 0),
            docker = DockerSummary(containers = 0, running = 0),
            collectedAtEpochMillis = at
        )
    }
}
