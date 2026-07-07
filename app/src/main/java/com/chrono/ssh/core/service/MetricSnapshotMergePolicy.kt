package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.CpuMetrics
import com.chrono.ssh.core.model.DiskMetrics
import com.chrono.ssh.core.model.DockerSummary
import com.chrono.ssh.core.model.MemoryMetrics
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.NetworkInterfaceMetric
import com.chrono.ssh.core.model.NetworkMetrics
import com.chrono.ssh.core.model.ProcessSummary
import com.chrono.ssh.core.model.ServiceSummary

object MetricSnapshotMergePolicy {
    fun merge(previous: MetricSnapshot?, incoming: MetricSnapshot): MetricSnapshot {
        if (previous == null || !previous.hasUsefulMetrics()) return incoming
        if (!incoming.hasAnyUsefulComponent()) {
            return previous.copy(
                status = incoming.status,
                latencyMs = incoming.latencyMs ?: previous.latencyMs,
                collectedAtEpochMillis = incoming.collectedAtEpochMillis
            )
        }
        return incoming.copy(
            uptime = incoming.uptime.takeIf { it != "--" } ?: previous.uptime,
            cpu = incoming.cpu.takeIf { it.isUseful() } ?: previous.cpu,
            memory = incoming.memory.takeIf { it.isUseful() } ?: previous.memory,
            disk = mergeDisk(previous.disk, incoming.disk),
            network = mergeNetwork(previous.network, incoming.network),
            processes = mergeProcesses(previous.processes, incoming.processes),
            services = mergeServices(previous.services, incoming.services),
            docker = mergeDocker(previous.docker, incoming.docker),
            sensors = incoming.sensors.ifEmpty { previous.sensors },
            batteries = incoming.batteries.ifEmpty { previous.batteries },
            smartDisks = incoming.smartDisks.ifEmpty { previous.smartDisks },
            pveResources = incoming.pveResources.ifEmpty { previous.pveResources },
            gpus = incoming.gpus.ifEmpty { previous.gpus },
            packageUpdates = incoming.packageUpdates.ifEmpty { previous.packageUpdates },
            latencyMs = incoming.latencyMs ?: previous.latencyMs
        )
    }

    fun historySample(incoming: MetricSnapshot): MetricSnapshot? {
        if (incoming.uptime == "--") return null
        return incoming.takeIf { it.hasAnyUsefulComponent() }
    }

    private fun MetricSnapshot.hasUsefulMetrics(): Boolean {
        return uptime != "--" || hasAnyUsefulComponent() || hasUsefulInventoryRows()
    }

    private fun MetricSnapshot.hasAnyUsefulComponent(): Boolean {
        return cpu.isUseful() ||
            memory.isUseful() ||
            disk.isUseful() ||
            network.isUseful() ||
            processes.isUseful() ||
            services.isUseful() ||
            docker.isUseful() ||
            sensors.isNotEmpty() ||
            batteries.isNotEmpty() ||
            smartDisks.isNotEmpty() ||
            pveResources.isNotEmpty() ||
            gpus.isNotEmpty() ||
            packageUpdates.isNotEmpty()
    }

    private fun MetricSnapshot.hasUsefulInventoryRows(): Boolean {
        return processes.items.isNotEmpty() ||
            services.items.isNotEmpty() ||
            services.failedItems.isNotEmpty() ||
            docker.items.isNotEmpty() ||
            sensors.isNotEmpty() ||
            batteries.isNotEmpty() ||
            smartDisks.isNotEmpty() ||
            pveResources.isNotEmpty() ||
            gpus.isNotEmpty() ||
            packageUpdates.isNotEmpty()
    }

    private fun CpuMetrics.isUseful(): Boolean {
        return usagePercent > 0 ||
            perCore.isNotEmpty() ||
            model != "Linux CPU" ||
            recentLoad.any { it > 0f }
    }

    private fun MemoryMetrics.isUseful(): Boolean = totalMb > 0 && (usedMb > 0 || swapTotalMb > 0)

    private fun DiskMetrics.isUseful(): Boolean {
        return hasCapacity() || readPerSecond != "--" || writePerSecond != "--" || readTotal.isUsefulTotalLabel() || writeTotal.isUsefulTotalLabel()
    }

    private fun mergeDisk(previous: DiskMetrics, incoming: DiskMetrics): DiskMetrics {
        if (!incoming.isUseful()) return previous
        val hasIncomingCapacity = incoming.hasCapacity()
        return DiskMetrics(
            usedGb = incoming.usedGb.takeIf { hasIncomingCapacity } ?: previous.usedGb,
            totalGb = incoming.totalGb.takeIf { hasIncomingCapacity } ?: previous.totalGb,
            readPerSecond = incoming.readPerSecond.takeIf { it != "--" } ?: previous.readPerSecond,
            writePerSecond = incoming.writePerSecond.takeIf { it != "--" } ?: previous.writePerSecond,
            readTotal = incoming.readTotal.takeIf { it.isUsefulTotalOrPlaceholderSafe() } ?: previous.readTotal,
            writeTotal = incoming.writeTotal.takeIf { it.isUsefulTotalOrPlaceholderSafe() } ?: previous.writeTotal,
            filesystems = incoming.filesystems.ifEmpty { previous.filesystems }
        )
    }

    private fun DiskMetrics.hasCapacity(): Boolean = totalGb > 0f

    private fun NetworkMetrics.isUseful(): Boolean {
        return primaryInterface.hasUsefulTraffic() ||
            interfaces.any { it.hasUsefulTraffic() } ||
            history.vnStat != null
    }

    private fun mergeNetwork(previous: NetworkMetrics, incoming: NetworkMetrics): NetworkMetrics {
        if (!incoming.isUseful()) return previous
        val incomingHasLiveInterfaces = incoming.interfaces.any { it.hasUsefulTraffic() }
        val interfaces = mergeInterfaces(previous.interfaces, incoming.interfaces).takeIf { incomingHasLiveInterfaces } ?: previous.interfaces
        val primary = mergeInterface(previous.primaryInterface, incoming.primaryInterface)
            .takeIf { incoming.primaryInterface.hasUsefulTraffic() }
            ?: previous.primaryInterface
        return incoming.copy(
            primaryInterface = primary,
            interfaces = interfaces,
            history = incoming.history.copy(
                uploadLabel = incoming.history.uploadLabel.takeIf { it.isUsefulTotalLabel() || it.isUsefulRateLabel() } ?: previous.history.uploadLabel,
                downloadLabel = incoming.history.downloadLabel.takeIf { it.isUsefulTotalLabel() || it.isUsefulRateLabel() } ?: previous.history.downloadLabel,
                uploadBars = incoming.history.uploadBars.takeIf { incomingHasLiveInterfaces && it.isNotEmpty() } ?: previous.history.uploadBars,
                downloadBars = incoming.history.downloadBars.takeIf { incomingHasLiveInterfaces && it.isNotEmpty() } ?: previous.history.downloadBars,
                labels = incoming.history.labels.takeIf { incomingHasLiveInterfaces && it.isNotEmpty() } ?: previous.history.labels,
                vnStat = incoming.history.vnStat ?: previous.history.vnStat
            )
        )
    }

    private fun mergeInterfaces(
        previous: List<NetworkInterfaceMetric>,
        incoming: List<NetworkInterfaceMetric>
    ): List<NetworkInterfaceMetric> {
        if (incoming.isEmpty()) return previous
        val incomingByName = incoming.associateBy { it.name }
        val mergedPrevious = previous.map { existing ->
            incomingByName[existing.name]?.let { mergeInterface(existing, it) } ?: existing
        }
        val newInterfaces = incoming.filterNot { current -> previous.any { it.name == current.name } }
        return mergedPrevious + newInterfaces
    }

    private fun mergeInterface(
        previous: NetworkInterfaceMetric,
        incoming: NetworkInterfaceMetric
    ): NetworkInterfaceMetric {
        return incoming.copy(
            address = incoming.address.takeIf { it != "--" && it.isNotBlank() } ?: previous.address,
            uploadRate = incoming.uploadRate.takeIf { it.isUsefulRateLabel() } ?: previous.uploadRate,
            downloadRate = incoming.downloadRate.takeIf { it.isUsefulRateLabel() } ?: previous.downloadRate,
            uploadTotal = incoming.uploadTotal.takeIf { it.isUsefulTotalLabel() } ?: previous.uploadTotal,
            downloadTotal = incoming.downloadTotal.takeIf { it.isUsefulTotalLabel() } ?: previous.downloadTotal,
            uploadShare = incoming.uploadShare.takeIf { incoming.uploadRate.isUsefulRateLabel() || incoming.downloadRate.isUsefulRateLabel() }
                ?: previous.uploadShare
        )
    }

    private fun NetworkInterfaceMetric.hasUsefulTraffic(): Boolean {
        return uploadRate.isUsefulRateLabel() ||
            downloadRate.isUsefulRateLabel() ||
            uploadTotal.isUsefulTotalLabel() ||
            downloadTotal.isUsefulTotalLabel()
    }

    private fun String.isUsefulRateLabel(): Boolean {
        return isNotBlank() && this != "--" && !MetricFormatters.isZeroRateLabel(this)
    }

    private fun String.isUsefulTotalLabel(): Boolean {
        return isNotBlank() && this != "--" && this != "0.00 B" && this != "0 B"
    }

    private fun String.isUsefulTotalOrPlaceholderSafe(): Boolean = isUsefulTotalLabel()

    private fun ProcessSummary.isUseful(): Boolean = total > 0 || running > 0 || topProcess != "--" || items.isNotEmpty()

    private fun ServiceSummary.isUseful(): Boolean = total > 0 || failed > 0 || items.isNotEmpty() || failedItems.isNotEmpty()

    private fun DockerSummary.isUseful(): Boolean = containers > 0 || running > 0 || items.isNotEmpty()

    private fun mergeProcesses(previous: ProcessSummary, incoming: ProcessSummary): ProcessSummary {
        if (!incoming.isUseful()) return previous
        return incoming.copy(items = incoming.items.ifEmpty { previous.items })
    }

    private fun mergeServices(previous: ServiceSummary, incoming: ServiceSummary): ServiceSummary {
        if (!incoming.isUseful()) return previous
        return incoming.copy(
            failedItems = incoming.failedItems.ifEmpty { previous.failedItems },
            items = incoming.items.ifEmpty { previous.items }
        )
    }

    private fun mergeDocker(previous: DockerSummary, incoming: DockerSummary): DockerSummary {
        if (!incoming.isUseful()) return previous
        return incoming.copy(items = incoming.items.ifEmpty { previous.items })
    }
}
