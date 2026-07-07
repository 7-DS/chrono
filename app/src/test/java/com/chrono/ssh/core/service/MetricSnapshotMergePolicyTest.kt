package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.CpuMetrics
import com.chrono.ssh.core.model.DiskMetrics
import com.chrono.ssh.core.model.ContainerMetric
import com.chrono.ssh.core.model.DockerSummary
import com.chrono.ssh.core.model.FilesystemMetric
import com.chrono.ssh.core.model.GpuMetric
import com.chrono.ssh.core.model.MemoryMetrics
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.NetworkHistory
import com.chrono.ssh.core.model.NetworkInterfaceMetric
import com.chrono.ssh.core.model.NetworkMetrics
import com.chrono.ssh.core.model.PackageUpdateMetric
import com.chrono.ssh.core.model.ProcessMetric
import com.chrono.ssh.core.model.ProcessSummary
import com.chrono.ssh.core.model.PveResourceMetric
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.model.ServiceMetric
import com.chrono.ssh.core.model.ServiceSummary
import com.chrono.ssh.core.model.SensorMetric
import com.chrono.ssh.core.model.SmartDiskMetric
import com.chrono.ssh.core.model.VnStatPeriodUsage
import com.chrono.ssh.core.model.VnStatUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricSnapshotMergePolicyTest {
    @Test
    fun emptyIncomingSnapshotKeepsUsefulPreviousMetrics() {
        val previous = usefulSnapshot().copy(latencyMs = 24, collectedAtEpochMillis = 100)
        val incoming = emptySnapshot().copy(status = ServerStatus.Online, latencyMs = null, collectedAtEpochMillis = 200)

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(ServerStatus.Online, merged.status)
        assertEquals(24, merged.latencyMs)
        assertEquals(200, merged.collectedAtEpochMillis)
        assertEquals(previous.cpu, merged.cpu)
        assertEquals(previous.memory, merged.memory)
        assertEquals(previous.disk, merged.disk)
        assertEquals(previous.network, merged.network)
    }

    @Test
    fun partialIncomingSnapshotKeepsOnlyMissingComponents() {
        val previous = usefulSnapshot()
        val incoming = emptySnapshot().copy(
            cpu = CpuMetrics(12, 4, "AMD EPYC", 8, 4, 0, 0, 0, 0.2f, 0.1f, 0.1f, listOf(0.1f)),
            memory = MemoryMetrics(2048, 4096, 0, 0),
            latencyMs = 31,
            collectedAtEpochMillis = 250
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(incoming.cpu, merged.cpu)
        assertEquals(incoming.memory, merged.memory)
        assertEquals(previous.disk, merged.disk)
        assertEquals(previous.network, merged.network)
        assertEquals(31, merged.latencyMs)
    }

    @Test
    fun historySampleUsesOnlyRawUsefulIncomingMetrics() {
        val previous = usefulSnapshot()
        val incoming = emptySnapshot().copy(
            uptime = "3 h 10 m",
            cpu = CpuMetrics(12, 4, "AMD EPYC", 8, 4, 0, 0, 0, 0.2f, 0.1f, 0.1f, listOf(0.1f)),
            collectedAtEpochMillis = 250
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)
        val history = MetricSnapshotMergePolicy.historySample(incoming)!!

        assertEquals(previous.network, merged.network)
        assertEquals(incoming.network, history.network)
        assertEquals(incoming.disk, history.disk)
        assertEquals(250, history.collectedAtEpochMillis)
    }

    @Test
    fun historySampleRejectsEmptyIncomingMetrics() {
        val incoming = emptySnapshot().copy(uptime = "3 h 10 m", collectedAtEpochMillis = 250)

        assertEquals(null, MetricSnapshotMergePolicy.historySample(incoming))
    }

    @Test
    fun zeroNetworkInterfaceListDoesNotReplaceUsefulPreviousNetwork() {
        val previous = usefulSnapshot()
        val incoming = emptySnapshot().copy(
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("eth0", "10.0.0.5/24", "0.00 B/s", "0.00 B/s", "0.00 B", "0.00 B", 0.5f),
                interfaces = listOf(
                    NetworkInterfaceMetric("eth0", "10.0.0.5/24", "0.00 B/s", "0.00 B/s", "0.00 B", "0.00 B", 0.5f),
                    NetworkInterfaceMetric("docker0", "--", "--", "--", "0.00 B", "0.00 B", 0.5f)
                ),
                history = NetworkHistory("0.00 B", "0.00 B", listOf(0f), listOf(0f), listOf("eth0", "docker0"))
            ),
            collectedAtEpochMillis = 240
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(previous.network, merged.network)
    }

    @Test
    fun vnStatOnlyNetworkSamplePreservesLiveInterfaceMetrics() {
        val previous = usefulSnapshot()
        val incoming = emptySnapshot().copy(
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("eth0", "--", "--", "--", "--", "--", 0.5f),
                interfaces = emptyList(),
                history = NetworkHistory(
                    uploadLabel = "--",
                    downloadLabel = "--",
                    uploadBars = emptyList(),
                    downloadBars = emptyList(),
                    labels = emptyList(),
                    vnStat = VnStatUsage(day = VnStatPeriodUsage(1024, 2048, 3072, "2026-06-30"))
                )
            ),
            collectedAtEpochMillis = 260
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(previous.network.primaryInterface, merged.network.primaryInterface)
        assertEquals(previous.network.interfaces, merged.network.interfaces)
        assertEquals(3072, merged.network.history.vnStat!!.day!!.totalBytes)
    }

    @Test
    fun diskCapacitySampleWithMissingDiskstatsKeepsPreviousRateDetails() {
        val previous = usefulSnapshot()
        val incoming = emptySnapshot().copy(
            disk = DiskMetrics(25f, 80f, "--", "--", "--", "--"),
            collectedAtEpochMillis = 270
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(25f, merged.disk.usedGb)
        assertEquals(80f, merged.disk.totalGb)
        assertEquals(previous.disk.readPerSecond, merged.disk.readPerSecond)
        assertEquals(previous.disk.writePerSecond, merged.disk.writePerSecond)
        assertEquals(previous.disk.readTotal, merged.disk.readTotal)
        assertEquals(previous.disk.writeTotal, merged.disk.writeTotal)
    }

    @Test
    fun diskstatsOnlySampleKeepsPreviousCapacityAndUpdatesAvailableCounters() {
        val previous = usefulSnapshot()
        val incoming = emptySnapshot().copy(
            disk = DiskMetrics(0f, 0f, "0.00 B/s", "2.0 M/s", "--", "12.0 G"),
            collectedAtEpochMillis = 280
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(previous.disk.usedGb, merged.disk.usedGb)
        assertEquals(previous.disk.totalGb, merged.disk.totalGb)
        assertEquals("0.00 B/s", merged.disk.readPerSecond)
        assertEquals("2.0 M/s", merged.disk.writePerSecond)
        assertEquals(previous.disk.readTotal, merged.disk.readTotal)
        assertEquals("12.0 G", merged.disk.writeTotal)
    }

    @Test
    fun diskMergePreservesFilesystemDetailsWhenIncomingHasOnlyCapacity() {
        val filesystem = FilesystemMetric("/", "/dev/sda1", 20f, 80f, "/dev/sda1")
        val previous = usefulSnapshot().copy(disk = usefulSnapshot().disk.copy(filesystems = listOf(filesystem)))
        val incoming = emptySnapshot().copy(
            disk = DiskMetrics(21f, 80f, "--", "--"),
            collectedAtEpochMillis = 281
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(listOf(filesystem), merged.disk.filesystems)
        assertEquals(21f, merged.disk.usedGb)
        assertEquals(80f, merged.disk.totalGb)
    }

    @Test
    fun fastInventoryRefreshPreservesDetailedProcessServiceAndContainerRows() {
        val process = ProcessMetric("S", 1.5f, 2.5f, "postgres", pid = 42)
        val failedService = ServiceMetric("ssh.service", "loaded", "failed", "failed", "SSH")
        val service = ServiceMetric("nginx.service", "loaded", "active", "running", "Nginx")
        val container = container("web", "running")
        val previous = usefulSnapshot().copy(
            processes = ProcessSummary(120, 2, "nginx", listOf(process)),
            services = ServiceSummary(20, 1, failedItems = listOf(failedService), items = listOf(service)),
            docker = DockerSummary(3, 2, listOf(container))
        )
        val incoming = emptySnapshot().copy(
            processes = ProcessSummary(130, 4, "postgres"),
            services = ServiceSummary(22, 1),
            docker = DockerSummary(4, 3),
            collectedAtEpochMillis = 282
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(130, merged.processes.total)
        assertEquals(listOf(process), merged.processes.items)
        assertEquals(22, merged.services.total)
        assertEquals(listOf(failedService), merged.services.failedItems)
        assertEquals(listOf(service), merged.services.items)
        assertEquals(4, merged.docker.containers)
        assertEquals(listOf(container), merged.docker.items)
    }

    @Test
    fun pveOnlySampleIsUsefulAndReplacesPreviousResources() {
        val previous = usefulSnapshot().copy(pveResources = listOf(pveResource("node/old")))
        val incoming = emptySnapshot().copy(
            pveResources = listOf(pveResource("node/pve")),
            collectedAtEpochMillis = 285
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(listOf(pveResource("node/pve")), merged.pveResources)
        assertEquals(285, merged.collectedAtEpochMillis)
    }

    @Test
    fun dockerOnlySampleIsUsefulAndEmptyDockerDoesNotReplacePreviousSummary() {
        val previous = usefulSnapshot().copy(docker = DockerSummary(1, 1, listOf(container("web", "running"))))
        val dockerOnly = emptySnapshot().copy(
            docker = DockerSummary(2, 1, listOf(container("web", "running"), container("job", "exited"))),
            collectedAtEpochMillis = 286
        )
        val partialWithoutDocker = emptySnapshot().copy(
            cpu = CpuMetrics(12, 4, "AMD EPYC", 8, 4, 0, 0, 0, 0.2f, 0.1f, 0.1f, listOf(0.1f)),
            collectedAtEpochMillis = 287
        )

        assertEquals(2, MetricSnapshotMergePolicy.merge(previous, dockerOnly).docker.containers)
        assertEquals(previous.docker, MetricSnapshotMergePolicy.merge(previous, partialWithoutDocker).docker)
    }

    @Test
    fun detailOnlySnapshotPreservesCoreFieldsAndUpdatesInventory() {
        val previous = usefulSnapshot()
        val incoming = emptySnapshot().copy(
            processes = ProcessSummary(220, 3, "postgres"),
            services = ServiceSummary(42, 1),
            docker = DockerSummary(2, 1, listOf(container("web", "running"), container("job", "exited"))),
            collectedAtEpochMillis = 286
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(previous.uptime, merged.uptime)
        assertEquals(previous.cpu, merged.cpu)
        assertEquals(previous.memory, merged.memory)
        assertEquals(previous.disk, merged.disk)
        assertEquals(ProcessSummary(220, 3, "postgres"), merged.processes)
        assertEquals(ServiceSummary(42, 1), merged.services)
        assertEquals(2, merged.docker.containers)
    }

    @Test
    fun previousInventoryOnlySnapshotSurvivesEmptyIncomingRefresh() {
        val previous = emptySnapshot().copy(
            processes = ProcessSummary(220, 3, "postgres", listOf(ProcessMetric("S", 2f, 3f, "postgres", 44))),
            services = ServiceSummary(42, 1, failedItems = listOf(ServiceMetric("bad.service", "loaded", "failed", "failed", "Bad"))),
            docker = DockerSummary(2, 1, listOf(container("web", "running"))),
            collectedAtEpochMillis = 300
        )
        val incoming = emptySnapshot().copy(collectedAtEpochMillis = 400)

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(400, merged.collectedAtEpochMillis)
        assertEquals(previous.processes, merged.processes)
        assertEquals(previous.services, merged.services)
        assertEquals(previous.docker, merged.docker)
    }

    @Test
    fun offlineInventorySnapshotSurvivesNextFastRefresh() {
        val process = ProcessMetric("S", 2f, 3f, "postgres", 44)
        val service = ServiceMetric("bad.service", "loaded", "failed", "failed", "Bad")
        val container = container("web", "running")
        val previous = usefulSnapshot().copy(
            status = ServerStatus.Offline,
            processes = ProcessSummary(220, 3, "postgres", listOf(process)),
            services = ServiceSummary(42, 1, failedItems = listOf(service)),
            docker = DockerSummary(2, 1, listOf(container))
        )
        val incoming = emptySnapshot().copy(
            cpu = CpuMetrics(12, 4, "AMD EPYC", 8, 4, 0, 0, 0, 0.2f, 0.1f, 0.1f, listOf(0.1f)),
            processes = ProcessSummary(230, 4, "postgres"),
            services = ServiceSummary(43, 1),
            docker = DockerSummary(2, 1),
            collectedAtEpochMillis = 401
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(ServerStatus.Online, merged.status)
        assertEquals(listOf(process), merged.processes.items)
        assertEquals(listOf(service), merged.services.failedItems)
        assertEquals(listOf(container), merged.docker.items)
    }

    @Test
    fun listOnlyProcessAndServiceDetailsUpdateInventoryCards() {
        val process = ProcessMetric("S", 1.5f, 2.5f, "postgres", pid = 42)
        val service = ServiceMetric("nginx.service", "loaded", "active", "running", "Nginx")
        val failed = ServiceMetric("bad.service", "loaded", "failed", "failed", "Bad")
        val previous = emptySnapshot().copy(collectedAtEpochMillis = 300)
        val incoming = emptySnapshot().copy(
            processes = ProcessSummary(0, 0, "--", listOf(process)),
            services = ServiceSummary(0, 0, items = listOf(service), failedItems = listOf(failed)),
            collectedAtEpochMillis = 400
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(listOf(process), merged.processes.items)
        assertEquals(listOf(service), merged.services.items)
        assertEquals(listOf(failed), merged.services.failedItems)
    }

    @Test
    fun gpuOnlySampleIsUsefulAndEmptyGpuListDoesNotReplacePreviousGpus() {
        val previous = usefulSnapshot().copy(gpus = listOf(gpu("old")))
        val gpuOnly = emptySnapshot().copy(gpus = listOf(gpu("new")), collectedAtEpochMillis = 288)
        val partialWithoutGpu = emptySnapshot().copy(
            memory = MemoryMetrics(2048, 4096, 0, 0),
            collectedAtEpochMillis = 289
        )

        assertEquals(listOf(gpu("new")), MetricSnapshotMergePolicy.merge(previous, gpuOnly).gpus)
        assertEquals(previous.gpus, MetricSnapshotMergePolicy.merge(previous, partialWithoutGpu).gpus)
    }

    @Test
    fun partialRefreshPreservesNewerInventoryRows() {
        val sensor = SensorMetric("coretemp", "isa", "Package id 0", "+56.0 C")
        val disk = SmartDiskMetric("/dev/sda", healthy = true, temperatureCelsius = 31, powerOnHours = 100, powerCycleCount = 2, lifeLeftPercent = 98, lifetimeWritesGiB = null, lifetimeReadsGiB = null, unsafeShutdowns = null)
        val update = PackageUpdateMetric("apt", "openssh-server", "1.0", "1.1")
        val previous = usefulSnapshot().copy(
            sensors = listOf(sensor),
            smartDisks = listOf(disk),
            packageUpdates = listOf(update)
        )
        val incoming = emptySnapshot().copy(
            cpu = CpuMetrics(12, 4, "AMD EPYC", 8, 4, 0, 0, 0, 0.2f, 0.1f, 0.1f, listOf(0.1f)),
            collectedAtEpochMillis = 289
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(listOf(sensor), merged.sensors)
        assertEquals(listOf(disk), merged.smartDisks)
        assertEquals(listOf(update), merged.packageUpdates)
    }

    @Test
    fun partialNetworkSamplePreservesKnownInterfaceFields() {
        val base = usefulSnapshot()
        val previous = base.copy(
            network = base.network.copy(
                interfaces = listOf(
                    NetworkInterfaceMetric("eth0", "10.0.0.5/24", "1.0 M/s", "2.0 M/s", "10.0 G", "20.0 G", 0.33f),
                    NetworkInterfaceMetric("eth1", "10.0.1.5/24", "4.0 M/s", "5.0 M/s", "40.0 G", "50.0 G", 0.44f)
                )
            )
        )
        val incoming = emptySnapshot().copy(
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("eth0", "10.0.0.9/24", "--", "3.0 M/s", "--", "22.0 G", 0.25f),
                interfaces = listOf(
                    NetworkInterfaceMetric("eth0", "10.0.0.9/24", "--", "3.0 M/s", "--", "22.0 G", 0.25f)
                ),
                history = NetworkHistory("--", "22.0 G", emptyList(), listOf(0.9f), listOf("eth0"))
            ),
            collectedAtEpochMillis = 290
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)
        val mergedInterface = merged.network.interfaces.first { it.name == "eth0" }

        assertEquals("10.0.0.9/24", merged.network.primaryInterface.address)
        assertEquals(previous.network.primaryInterface.uploadRate, merged.network.primaryInterface.uploadRate)
        assertEquals("3.0 M/s", merged.network.primaryInterface.downloadRate)
        assertEquals(previous.network.primaryInterface.uploadTotal, merged.network.primaryInterface.uploadTotal)
        assertEquals("22.0 G", merged.network.primaryInterface.downloadTotal)
        assertEquals("10.0.0.9/24", mergedInterface.address)
        assertEquals(previous.network.interfaces.first { it.name == "eth0" }.uploadRate, mergedInterface.uploadRate)
        assertEquals("3.0 M/s", mergedInterface.downloadRate)
        assertEquals("eth1", merged.network.interfaces.last().name)
        assertEquals(previous.network.history.uploadLabel, merged.network.history.uploadLabel)
        assertEquals("22.0 G", merged.network.history.downloadLabel)
    }

    @Test
    fun usefulIncomingSnapshotReplacesPreviousMetrics() {
        val previous = usefulSnapshot()
        val incoming = usefulSnapshot().copy(
            cpu = CpuMetrics(70, 8, "Intel Xeon", 40, 20, 0, 5, 0, 1.1f, 1.0f, 0.9f, listOf(0.9f, 1.0f, 1.1f)),
            memory = MemoryMetrics(8192, 16384, 0, 0),
            collectedAtEpochMillis = 300
        )

        val merged = MetricSnapshotMergePolicy.merge(previous, incoming)

        assertEquals(incoming.cpu, merged.cpu)
        assertEquals(incoming.memory, merged.memory)
        assertEquals(300, merged.collectedAtEpochMillis)
    }

    private fun usefulSnapshot(): MetricSnapshot {
        return MetricSnapshot(
            serverId = "server-1",
            status = ServerStatus.Online,
            latencyMs = 20,
            uptime = "2 h 5 m",
            cpu = CpuMetrics(35, 4, "AMD EPYC", 20, 10, 0, 2, 0, 0.35f, 0.25f, 0.20f, listOf(0.2f, 0.25f, 0.35f)),
            memory = MemoryMetrics(3072, 8192, 0, 0),
            disk = DiskMetrics(20f, 80f, "3.0 M/s", "1.0 M/s", "20.0 G", "10.0 G"),
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("eth0", "10.0.0.5/24", "1.0 M/s", "2.0 M/s", "10.0 G", "20.0 G", 0.33f),
                interfaces = listOf(NetworkInterfaceMetric("eth0", "10.0.0.5/24", "1.0 M/s", "2.0 M/s", "10.0 G", "20.0 G", 0.33f)),
                history = NetworkHistory("10.0 G", "20.0 G", listOf(0.4f), listOf(0.8f), listOf("eth0"))
            ),
            processes = ProcessSummary(120, 2, "nginx"),
            services = ServiceSummary(20, 0),
            docker = DockerSummary(3, 2),
            collectedAtEpochMillis = 100
        )
    }

    private fun emptySnapshot(): MetricSnapshot {
        return MetricSnapshot(
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
    }

    private fun pveResource(id: String): PveResourceMetric {
        return PveResourceMetric(
            id = id,
            name = id.substringAfterLast('/'),
            type = id.substringBefore('/'),
            status = "online",
            node = "pve",
            vmId = null,
            cpuUsagePercent = null,
            memoryUsedBytes = null,
            memoryMaxBytes = null,
            diskUsedBytes = null,
            diskMaxBytes = null,
            uptimeSeconds = null
        )
    }

    private fun container(name: String, state: String): ContainerMetric {
        return ContainerMetric(
            id = name,
            name = name,
            image = "image:latest",
            status = state,
            state = state,
            engine = "docker"
        )
    }

    private fun gpu(name: String): GpuMetric {
        return GpuMetric(
            id = name,
            name = name,
            vendor = "NVIDIA",
            utilizationPercent = 1,
            memoryUsedMiB = null,
            memoryTotalMiB = null,
            temperatureCelsius = null,
            powerDrawWatts = null,
            powerLimitWatts = null,
            fanSpeed = null,
            clockMhz = null
        )
    }
}
