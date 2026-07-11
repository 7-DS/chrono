package com.chrono.ssh.ui.screens

import com.chrono.ssh.core.model.CpuMetrics
import com.chrono.ssh.core.model.BatteryMetric
import com.chrono.ssh.core.model.ContainerMetric
import com.chrono.ssh.core.model.ConnectionEvent
import com.chrono.ssh.core.model.ConnectionEventLevel
import com.chrono.ssh.core.model.DiskMetrics
import com.chrono.ssh.core.model.DockerSummary
import com.chrono.ssh.core.model.FilesystemMetric
import com.chrono.ssh.core.model.GpuMetric
import com.chrono.ssh.core.model.MemoryMetrics
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.NetworkHistory
import com.chrono.ssh.core.model.NetworkInterfaceMetric
import com.chrono.ssh.core.model.NetworkMetrics
import com.chrono.ssh.core.model.ProcessMetric
import com.chrono.ssh.core.model.ProcessSummary
import com.chrono.ssh.core.model.ServiceMetric
import com.chrono.ssh.core.model.PveResourceMetric
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.model.ServiceSummary
import com.chrono.ssh.core.model.SmartDiskMetric
import com.chrono.ssh.ui.design.DeckColors
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerDetailScreenTest {
    @Test
    fun recentActivityItemsSortLimitAndFlattenMessages() {
        val events = listOf(
            event(ConnectionEventLevel.Info, 10, "first"),
            event(ConnectionEventLevel.Error, 30, "line1\nline2"),
            event(ConnectionEventLevel.Success, 20, "ok")
        )

        assertEquals(
            listOf(
                ActivityItem("Error", "line1 line2", 30, "red"),
                ActivityItem("Success", "ok", 20, "green")
            ),
            recentActivityItems(events, limit = 2)
        )
    }

    @Test
    fun cpuLoadSeriesUsesOnlyRealSamples() {
        val snapshot = snapshot(at = 2, load1 = 0.9f, load5 = 0.5f, load15 = 0.2f)

        val series = cpuLoadSeries(snapshot, emptyList())

        assertEquals(listOf(0.9f), series.load1)
        assertEquals(listOf(0.5f), series.load5)
        assertEquals(listOf(0.2f), series.load15)
        assertEquals(listOf("-45s", "-30s", "-15s", "now"), series.axisLabels)
    }

    @Test
    fun cpuLoadSeriesSortsDeduplicatesClampsAndCapsHistory() {
        val history = (0L..70L).map { index ->
            snapshot(at = index, load1 = index.toFloat(), load5 = -1f, load15 = 0.1f)
        } + snapshot(at = 70, load1 = 999f, load5 = 999f, load15 = 999f)

        val series = cpuLoadSeries(
            snapshot = snapshot(at = 71, load1 = 71f, load5 = 5f, load15 = 15f),
            metricHistory = history.shuffled()
        )

        assertEquals(64, series.load1.size)
        assertEquals(8f, series.load1.first())
        assertEquals(71f, series.load1.last())
        assertEquals(0f, series.load5.first())
        assertEquals(5f, series.load5.last())
        assertEquals(listOf("-45s", "-30s", "-15s", "now"), series.axisLabels)
    }

    @Test
    fun cpuLoadScaleUsesCoreCapacityAsFloor() {
        assertEquals(8f, niceLoadMax(maxSeen = 0.4f, cores = 8))
        assertEquals(11.5f, niceLoadMax(maxSeen = 10f, cores = 8))
    }

    @Test
    fun uptimeHistorySummaryUsesExistingSamples() {
        val history = listOf(
            snapshot(at = 3_600_000, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f).copy(status = ServerStatus.Offline),
            snapshot(at = 0, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f),
            snapshot(at = 0, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f).copy(status = ServerStatus.Offline)
        )

        val summary = uptimeHistorySummary(
            snapshot = snapshot(at = 7_200_000, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f).copy(uptime = "2 days"),
            metricHistory = history
        )

        assertEquals(UptimeHistorySummary("2 days", 66, 2, 3, "2h"), summary)
    }

    @Test
    fun uptimeHistorySummaryFallsBackToCurrentSnapshot() {
        val summary = uptimeHistorySummary(
            snapshot = snapshot(at = 7, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f).copy(uptime = "--", status = ServerStatus.Unknown),
            metricHistory = emptyList()
        )

        assertEquals(UptimeHistorySummary("Unknown", 0, 0, 1, "latest"), summary)
    }

    @Test
    fun systemSummaryRowsExposeCollectedProcessServiceAndDockerState() {
        val rows = systemSummaryRows(
            snapshot(at = 2, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f).copy(
                processes = ProcessSummary(total = 128, running = 3, topProcess = "postgres"),
                services = ServiceSummary(total = 42, failed = 2),
                docker = DockerSummary(containers = 6, running = 4)
            )
        )

        assertEquals(
            listOf(
                SystemSummaryItem("Processes", "3/128", "Top: postgres"),
                SystemSummaryItem("Services", "2/42", "Failed services need attention"),
                SystemSummaryItem("Docker", "4/6", "Containers running")
            ),
            rows
        )
    }

    @Test
    fun countOnlyServiceAndContainerSummariesStillRenderCards() {
        assertEquals(true, ServiceSummary(total = 42, failed = 0).shouldShowSystemdCard())
        assertEquals(true, ServiceSummary(total = 0, failed = 2).shouldShowSystemdCard())
        assertEquals(false, ServiceSummary(total = 0, failed = 0).shouldShowSystemdCard())
        assertEquals(true, DockerSummary(containers = 6, running = 4).shouldShowContainerCard())
        assertEquals(true, DockerSummary(containers = 0, running = 1).shouldShowContainerCard())
        assertEquals(false, DockerSummary(containers = 0, running = 0).shouldShowContainerCard())
    }

    @Test
    fun processPageMetricsAndSortingExposeServerBoxStyleProcessData() {
        val processes = listOf(
            ProcessMetric(state = "S", cpuPercent = 1.2f, memoryPercent = 0.5f, command = "sshd", pid = 30),
            ProcessMetric(state = "R", cpuPercent = 22.5f, memoryPercent = 18.2f, command = "postgres", pid = 20),
            ProcessMetric(state = "S", cpuPercent = 4f, memoryPercent = 3.1f, command = "nginx", pid = 10)
        )

        assertEquals(
            listOf(
                ProcessPageMetric("Running", "1/128", "green"),
                ProcessPageMetric("Top CPU", "22.5%", "orange"),
                ProcessPageMetric("Top RAM", "18.2%", "cyan")
            ),
            processPageMetrics(ProcessSummary(total = 128, running = 1, topProcess = "postgres"), processes)
        )
        assertEquals(listOf("postgres", "nginx", "sshd"), sortedProcesses(processes, ProcessSortMode.Cpu).map { it.command })
        assertEquals(listOf("postgres", "nginx", "sshd"), sortedProcesses(processes, ProcessSortMode.Memory).map { it.command })
        assertEquals(listOf("nginx", "postgres", "sshd"), sortedProcesses(processes, ProcessSortMode.Pid).map { it.command })
    }

    @Test
    fun processSummaryIncludesExtendedProcessFields() {
        assertEquals(
            "PID 42 / PPID 1 / postgres / R / 1.2% RAM / 64.00 M RSS / 256.00 M VSZ / Age 3600",
            processSummary(
                ProcessMetric(
                    state = "R",
                    cpuPercent = 31.4f,
                    memoryPercent = 1.2f,
                    command = "postgres",
                    pid = 42,
                    user = "postgres",
                    parentPid = 1,
                    rssKb = 65536,
                    virtualSizeKb = 262144,
                    elapsed = "3600"
                )
            )
        )
    }

    @Test
    fun emptyRuntimeStatusMessagesDistinguishPendingTotalsFromUnavailableData() {
        assertEquals(
            "No process data yet. Metrics may still be pending or the last refresh timed out.",
            processEmptyStatus(ProcessSummary(total = 0, running = 0, topProcess = "--"))
        )
        assertEquals(
            "Process totals are available; detailed rows are pending refresh.",
            processEmptyStatus(ProcessSummary(total = 8, running = 1, topProcess = "sshd"))
        )
        assertEquals("Service status unavailable", systemdSummaryLabel(ServiceSummary(total = 0, failed = 0)))
        assertEquals(
            "No systemd data yet. Metrics may still be pending or this host may not use systemd.",
            systemdEmptyStatus(ServiceSummary(total = 0, failed = 0))
        )
        assertEquals("Container status unavailable", containerSummaryLabel(DockerSummary(containers = 0, running = 0)))
        assertEquals(
            "No container data yet. Metrics may still be pending or Docker/Podman may be unavailable.",
            containerEmptyStatus(DockerSummary(containers = 0, running = 0))
        )
    }

    @Test
    fun serviceStateColorMakesServiceStatusReadable() {
        assertEquals(DeckColors.Cyan, serviceStateColor(service(active = "active", sub = "running")))
        assertEquals(DeckColors.SecondaryText, serviceStateColor(service(active = "active", sub = "exited")))
        assertEquals(DeckColors.PrimaryText, serviceStateColor(service(active = "activating", sub = "auto-restart")))
        assertEquals(DeckColors.Red, serviceStateColor(service(active = "failed", sub = "failed")))
        assertEquals(DeckColors.Red, serviceStateColor(service(active = "inactive", sub = "dead")))
        assertEquals(DeckColors.SecondaryText, serviceStateColor(service(active = "inactive", sub = "waiting")))
    }

    @Test
    fun filesystemDetailLabelShowsMountAndFreeCapacity() {
        assertEquals(
            "Used 12.50 G / Total 50.00 G / Free 37.50 G - ext4 on /",
            filesystemDetailLabel(FilesystemMetric(mountPoint = "/", filesystem = "ext4", usedGb = 12.5f, totalGb = 50f))
        )
    }

    @Test
    fun batteryDetailLabelShowsUsefulTimeEstimate() {
        assertEquals(
            "Discharging / Good / Li-poly / Empty in 1h 30m",
            batteryDetailLabel(
                BatteryMetric(
                    name = "battery",
                    status = "Discharging",
                    capacityPercent = 73,
                    health = "Good",
                    technology = "Li-poly",
                    timeToEmptySeconds = 5400
                )
            )
        )
    }

    @Test
    fun smartDiskLabelsExposeHealthAndSelfTestExtras() {
        val disk = SmartDiskMetric(
            device = "/dev/sda",
            model = "Samsung SSD",
            serial = "S123",
            healthy = true,
            selfTestStatus = "Completed without error",
            temperatureCelsius = 35,
            powerOnHours = 100,
            powerCycleCount = 4,
            lifeLeftPercent = 93,
            lifetimeWritesGiB = 500,
            lifetimeReadsGiB = 600,
            unsafeShutdowns = 1
        )

        assertEquals("/dev/sda - Samsung SSD", smartDiskTitle(disk))
        assertEquals("OK", smartDiskBadge(disk))
        assertEquals("SN S123 / Self-test: Completed without error / 35C / 100h / 4 cycles / 1 unsafe", smartDiskSummary(disk))
    }

    @Test
    fun systemSummaryRowsHideUncollectedZeroStateDetails() {
        val rows = systemSummaryRows(
            snapshot(at = 2, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f).copy(
                processes = ProcessSummary(total = 0, running = 0, topProcess = ""),
                services = ServiceSummary(total = 0, failed = 0),
                docker = DockerSummary(containers = 0, running = 0)
            )
        )

        assertEquals(emptyList<SystemSummaryItem>(), rows)
    }

    @Test
    fun systemSummaryRowsExposeProxmoxClusterResources() {
        val rows = systemSummaryRows(
            snapshot(at = 2, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f).copy(
                pveResources = listOf(
                    pveResource("lxc/100", "LXC", "running"),
                    pveResource("qemu/101", "qemu", "stopped"),
                    pveResource("node/pve", "node", "online"),
                    pveResource("storage/pve/local", "storage", "available")
                )
            )
        )

        assertEquals(SystemSummaryItem("Proxmox", "1/2", "1 nodes · 1 storage"), rows.last())
    }

    @Test
    fun systemSummaryRowsExposeGpuResources() {
        val rows = systemSummaryRows(
            snapshot(at = 2, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f).copy(
                gpus = listOf(
                    gpu("NVIDIA RTX", 3, 34),
                    gpu("AMD Radeon", 0, 45)
                )
            )
        )

        assertEquals(SystemSummaryItem("GPU", "1/2", "Peak 45C"), rows.last())
    }

    @Test
    fun detailRowsSummarizeCollectedReferenceMetrics() {
        assertEquals(
            "docker / postgres:16 / Up 2 hours",
            containerSummary(
                ContainerMetric(
                    id = "abc123",
                    name = "db",
                    image = "postgres:16",
                    status = "Up 2 hours",
                    state = "running",
                    engine = "docker"
                )
            )
        )
        assertEquals(
            "NVIDIA / 1024/8192 MiB / 55C / 120/200 W / 35% / 1800 MHz",
            gpuSummary(
                GpuMetric(
                    id = "gpu0",
                    name = "RTX",
                    vendor = "NVIDIA",
                    utilizationPercent = 42,
                    memoryUsedMiB = 1024,
                    memoryTotalMiB = 8192,
                    temperatureCelsius = 55,
                    powerDrawWatts = 120f,
                    powerLimitWatts = 200f,
                    fanSpeed = "35%",
                    clockMhz = 1800
                )
            )
        )
        assertEquals(
            "qemu / pve / 14% CPU / 1.00 G / 2.00 G RAM / 4.00 G / 8.00 G disk",
            pveResourceSummary(
                PveResourceMetric(
                    id = "qemu/100",
                    name = "vm",
                    type = "qemu",
                    status = "running",
                    node = "pve",
                    vmId = 100,
                    cpuUsagePercent = 14,
                    memoryUsedBytes = 1024L * 1024L * 1024L,
                    memoryMaxBytes = 2L * 1024L * 1024L * 1024L,
                    diskUsedBytes = 4L * 1024L * 1024L * 1024L,
                    diskMaxBytes = 8L * 1024L * 1024L * 1024L,
                    uptimeSeconds = null
                )
            )
        )
    }

    @Test
    fun runtimeActionDescriptionsStayAccessibleWithoutTextLabels() {
        assertEquals("Restart", runtimeActionDescription(" restart "))
        assertEquals("Lower priority", runtimeActionDescription("lower-priority"))
        assertEquals("Force stop", runtimeActionDescription("force-stop"))
        assertEquals("Run action", runtimeActionDescription("unknown"))
    }

    @Test
    fun runtimeActionConfirmationCoversDestructiveActionsOnly() {
        assertEquals(false, runtimeActionNeedsConfirmation("start"))
        assertEquals(false, runtimeActionNeedsConfirmation("status"))
        assertEquals(false, runtimeActionNeedsConfirmation("logs"))
        assertEquals(false, runtimeActionNeedsConfirmation("lower-priority"))
        assertEquals(true, runtimeActionNeedsConfirmation("restart"))
        assertEquals(true, runtimeActionNeedsConfirmation("stop"))
        assertEquals(true, runtimeActionNeedsConfirmation("terminate"))
        assertEquals(true, runtimeActionNeedsConfirmation("force-stop"))
    }

    @Test
    fun serverDetailDoesNotRenderUnavailableMetricPlaceholders() {
        assertEquals(false, shouldRenderServerDetailMetrics(unavailableSnapshot()))
        assertEquals(true, shouldRenderServerDetailMetrics(snapshot(at = 2, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f)))
        assertEquals(false, shouldRenderServerDetailMetricUnavailableState(unavailableSnapshot()))
        assertEquals(true, shouldRenderServerDetailMetricUnavailableState(unavailableSnapshot().copy(collectedAtEpochMillis = 2)))
    }

    @Test
    fun serverDetailDiskCapacityRequiresKnownTotal() {
        assertEquals(false, snapshot(at = 2, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f).copy(
            disk = DiskMetrics(usedGb = 0f, totalGb = 0f, readPerSecond = "1.00 M/s", writePerSecond = "2.00 M/s")
        ).hasServerDetailDiskCapacity())
        assertEquals(true, snapshot(at = 2, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f).hasServerDetailDiskCapacity())
    }

    @Test
    fun serverDetailNetworkSummaryKeepsPartialRefreshData() {
        val partial = unavailableSnapshot().copy(
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("", "10.0.0.4/24", "--", "--", "8 M", "16 M", 0f),
                interfaces = emptyList(),
                history = NetworkHistory("8 M", "16 M", listOf(0.1f), listOf(0.2f), emptyList())
            )
        )

        assertEquals(true, partial.hasServerDetailNetworkSummary())
        assertEquals(false, unavailableSnapshot().copy(
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("", "--", "--", "--", "--", "--", 0f),
                interfaces = emptyList(),
                history = NetworkHistory("--", "--", emptyList(), emptyList(), emptyList())
            )
        ).hasServerDetailNetworkSummary())
        assertEquals(false, unavailableSnapshot().copy(
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("", "", "0.00 B/s", "0.00 B/s", "0 B", "0.00 B", 0f),
                interfaces = listOf(NetworkInterfaceMetric("docker0", "", "0.00 B/s", "0.00 B/s", "0 B", "0.00 B", 0f)),
                history = NetworkHistory("0 B", "0.00 B", listOf(0f), listOf(0f), emptyList())
            )
        ).hasServerDetailNetworkSummary())
    }

    @Test
    fun serverDetailPrimaryMetricSectionsKeepFilesystemProcessesNetworkOrder() {
        val sections = serverDetailPrimaryMetricSections(
            snapshot(at = 2, load1 = 0.1f, load5 = 0.1f, load15 = 0.1f).copy(
                disk = DiskMetrics(
                    usedGb = 1f,
                    totalGb = 2f,
                    readPerSecond = "0 B/s",
                    writePerSecond = "0 B/s",
                    filesystems = listOf(FilesystemMetric(mountPoint = "/", filesystem = "ext4", usedGb = 1f, totalGb = 2f))
                )
            )
        )

        assertEquals(
            listOf(
                ServerDetailPrimaryMetricSection.Filesystems,
                ServerDetailPrimaryMetricSection.Processes,
                ServerDetailPrimaryMetricSection.Systemd,
                ServerDetailPrimaryMetricSection.Network
            ),
            sections
        )
    }

    @Test
    fun serverDetailPrimaryMetricSectionsSkipEmptyCards() {
        assertEquals(
            emptyList<ServerDetailPrimaryMetricSection>(),
            serverDetailPrimaryMetricSections(unavailableSnapshot())
        )
    }

    @Test
    fun serverDetailPrimaryMetricSectionsShowsProcessRowsWithoutSummary() {
        val sections = serverDetailPrimaryMetricSections(
            unavailableSnapshot().copy(
                processes = ProcessSummary(
                    total = 0,
                    running = 0,
                    topProcess = "",
                    items = listOf(ProcessMetric(command = "sshd", state = "S", pid = 22, cpuPercent = null, memoryPercent = null))
                )
            )
        )

        assertEquals(
            listOf(ServerDetailPrimaryMetricSection.Processes, ServerDetailPrimaryMetricSection.Systemd),
            sections
        )
    }

    private fun pveResource(id: String, type: String, status: String): PveResourceMetric {
        return PveResourceMetric(
            id = id,
            name = id.substringAfterLast('/'),
            type = type,
            status = status,
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

    private fun gpu(name: String, utilizationPercent: Int, temperatureCelsius: Int): GpuMetric {
        return GpuMetric(
            id = name,
            name = name,
            vendor = name.substringBefore(' '),
            utilizationPercent = utilizationPercent,
            memoryUsedMiB = null,
            memoryTotalMiB = null,
            temperatureCelsius = temperatureCelsius,
            powerDrawWatts = null,
            powerLimitWatts = null,
            fanSpeed = null,
            clockMhz = null
        )
    }

    private fun event(level: ConnectionEventLevel, at: Long, message: String): ConnectionEvent {
        return ConnectionEvent(
            id = "event-$at",
            serverId = "server-1",
            atEpochMillis = at,
            level = level,
            message = message
        )
    }

    private fun service(active: String, sub: String): ServiceMetric {
        return ServiceMetric(unit = "demo.service", load = "loaded", active = active, sub = sub, description = "Demo")
    }

    private fun snapshot(at: Long, load1: Float, load5: Float, load15: Float): MetricSnapshot {
        return MetricSnapshot(
            serverId = "server",
            status = ServerStatus.Online,
            latencyMs = null,
            uptime = "1 day",
            cpu = CpuMetrics(
                usagePercent = 1,
                cores = 1,
                model = "CPU",
                userPercent = 1,
                systemPercent = 0,
                nicePercent = 0,
                ioWaitPercent = 0,
                stealPercent = 0,
                load1 = load1,
                load5 = load5,
                load15 = load15,
                recentLoad = emptyList()
            ),
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

    private fun unavailableSnapshot(): MetricSnapshot {
        return MetricSnapshot(
            serverId = "server",
            status = ServerStatus.Unknown,
            latencyMs = null,
            uptime = "--",
            cpu = CpuMetrics(
                usagePercent = 0,
                cores = 0,
                model = "Linux CPU",
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
            memory = MemoryMetrics(usedMb = 0, totalMb = 0, swapUsedMb = 0, swapTotalMb = 0),
            disk = DiskMetrics(usedGb = 0f, totalGb = 0f, readPerSecond = "--", writePerSecond = "--"),
            network = NetworkMetrics(
                primaryInterface = NetworkInterfaceMetric("eth0", "--", "--", "--", "--", "--", 0.5f),
                interfaces = emptyList(),
                history = NetworkHistory("--", "--", emptyList(), emptyList(), emptyList())
            ),
            processes = ProcessSummary(total = 0, running = 0, topProcess = "--"),
            services = ServiceSummary(total = 0, failed = 0),
            docker = DockerSummary(containers = 0, running = 0),
            collectedAtEpochMillis = 0
        )
    }
}
