package com.chrono.ssh.core.metrics

import com.chrono.ssh.core.model.SensorMetric
import com.chrono.ssh.core.model.BatteryMetric
import com.chrono.ssh.core.model.ContainerImageMetric
import com.chrono.ssh.core.model.ContainerMetric
import com.chrono.ssh.core.model.FilesystemMetric
import com.chrono.ssh.core.model.GpuMetric
import com.chrono.ssh.core.model.PveResourceMetric
import com.chrono.ssh.core.model.ProcessMetric
import com.chrono.ssh.core.model.ServiceMetric
import com.chrono.ssh.core.model.SmartDiskMetric

data class ProcStatCpu(
    val user: Long,
    val nice: Long,
    val system: Long,
    val idle: Long,
    val ioWait: Long,
    val irq: Long,
    val softIrq: Long,
    val steal: Long,
    val index: Int? = null
)

data class MemInfo(
    val totalKb: Long,
    val availableKb: Long,
    val swapTotalKb: Long,
    val swapFreeKb: Long
) {
    val usedKb: Long = (totalKb - availableKb).coerceAtLeast(0)
    val swapUsedKb: Long = (swapTotalKb - swapFreeKb).coerceAtLeast(0)
}

data class DiskInfo(
    val usedGb: Float,
    val totalGb: Float
)

data class DiskStatSample(
    val name: String,
    val sectorsRead: Long,
    val sectorsWritten: Long
)

data class LoadAverage(
    val load1: Float,
    val load5: Float,
    val load15: Float
)

data class CpuModelInfo(
    val model: String,
    val cores: Int
)

data class ProcessInfo(
    val total: Int,
    val running: Int,
    val topProcess: String,
    val items: List<ProcessMetric> = emptyList()
)

data class ServiceInfo(
    val total: Int,
    val failed: Int,
    val failedItems: List<ServiceMetric> = emptyList(),
    val items: List<ServiceMetric> = emptyList()
)

object LinuxMetricParsers {
    fun parseProcStatCpu(output: String): ProcStatCpu? {
        val cpuLine = output.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        return parseProcStatLine(cpuLine, null)
    }

    fun parseProcStatCores(output: String): List<ProcStatCpu> {
        return output.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (!trimmed.startsWith("cpu") || trimmed.startsWith("cpu ")) return@mapNotNull null
                val label = trimmed.substringBefore(" ")
                val index = label.removePrefix("cpu").toIntOrNull() ?: return@mapNotNull null
                parseProcStatLine(trimmed, index)
            }
            .sortedBy { it.index ?: Int.MAX_VALUE }
            .toList()
    }

    private fun parseProcStatLine(line: String, index: Int?): ProcStatCpu? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 9) return null
        return ProcStatCpu(
            user = parts[1].toLongOrNull() ?: return null,
            nice = parts[2].toLongOrNull() ?: return null,
            system = parts[3].toLongOrNull() ?: return null,
            idle = parts[4].toLongOrNull() ?: return null,
            ioWait = parts[5].toLongOrNull() ?: return null,
            irq = parts[6].toLongOrNull() ?: return null,
            softIrq = parts[7].toLongOrNull() ?: return null,
            steal = parts[8].toLongOrNull() ?: return null,
            index = index
        )
    }

    fun parseMemInfo(output: String): MemInfo? {
        val values = output.lineSequence()
            .mapNotNull { line ->
                val parts = line.split(Regex(":\\s*"), limit = 2)
                val key = parts.getOrNull(0) ?: return@mapNotNull null
                val value = parts.getOrNull(1)
                    ?.trim()
                    ?.split(Regex("\\s+"))
                    ?.firstOrNull()
                    ?.toLongOrNull()
                    ?: return@mapNotNull null
                key to value
            }
            .toMap()

        val total = values["MemTotal"] ?: return null
        val available = values["MemAvailable"] ?: values["MemFree"] ?: return null
        return MemInfo(
            totalKb = total,
            availableKb = available,
            swapTotalKb = values["SwapTotal"] ?: 0,
            swapFreeKb = values["SwapFree"] ?: 0
        )
    }

    fun parseUptime(output: String): String {
        val seconds = output.trim().split(Regex("\\s+")).firstOrNull()?.toDoubleOrNull()?.toLong() ?: return "--"
        val days = seconds / 86_400
        val hours = (seconds % 86_400) / 3_600
        val minutes = (seconds % 3_600) / 60
        return when {
            days > 0 -> "${days} d ${hours} h"
            hours > 0 -> "${hours} h ${minutes} m"
            else -> "${minutes} m"
        }
    }

    fun parseLoadAverage(output: String): LoadAverage {
        val parts = output.trim().split(Regex("\\s+"))
        return LoadAverage(
            load1 = parts.getOrNull(0)?.toFloatOrNull() ?: 0f,
            load5 = parts.getOrNull(1)?.toFloatOrNull() ?: 0f,
            load15 = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
        )
    }

    fun parseCpuModel(output: String): CpuModelInfo {
        val lines = output.lineSequence().toList()
        val model = lines.firstNotNullOfOrNull { line ->
            line.substringAfter("model name", "")
                .substringAfter(":", "")
                .trim()
                .takeIf { it.isNotBlank() }
        } ?: lines.firstNotNullOfOrNull { line ->
            line.substringAfter("Hardware", "")
                .substringAfter(":", "")
                .trim()
                .takeIf { it.isNotBlank() }
        } ?: "Linux CPU"
        val cores = lines.count { it.trimStart().startsWith("processor") }.takeIf { it > 0 }
            ?: Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return CpuModelInfo(cleanMetricLabel(model), cores)
    }

    fun parseDf(output: String): DiskInfo? {
        val row = output.lineSequence()
            .drop(1)
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: return null
        val parts = row.split(Regex("\\s+"))
        if (parts.size < 4) return null
        val totalKb = parts[1].toLongOrNull() ?: return null
        val usedKb = parts[2].toLongOrNull() ?: return null
        return DiskInfo(
            usedGb = (usedKb / 1_048_576f),
            totalGb = (totalKb / 1_048_576f)
        )
    }

    fun parseStatFilesystem(output: String): DiskInfo? {
        val parts = output.trim().split(Regex("\\s+"))
        if (parts.size < 3) return null
        val blocks = parts[0].toLongOrNull() ?: return null
        val available = parts[1].toLongOrNull() ?: return null
        val blockSize = parts[2].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val totalBytes = blocks * blockSize
        val usedBytes = (blocks - available).coerceAtLeast(0L) * blockSize
        if (totalBytes <= 0L) return null
        return DiskInfo(
            usedGb = usedBytes / 1_073_741_824f,
            totalGb = totalBytes / 1_073_741_824f
        )
    }

    fun parseDiskStats(output: String): Map<String, DiskStatSample> {
        val samples = output.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 14) return@mapNotNull null
                val name = parts[2]
                val wholeDevice = name.isLikelyDiskDevice()
                val partition = name.isLikelyDiskPartition()
                if (!wholeDevice && !partition) return@mapNotNull null
                val sectorsRead = parts[5].toLongOrNull() ?: return@mapNotNull null
                val sectorsWritten = parts[9].toLongOrNull() ?: return@mapNotNull null
                DiskStatCandidate(DiskStatSample(name, sectorsRead, sectorsWritten), wholeDevice)
            }
            .toList()
        val preferred = samples.filter { it.wholeDevice }.ifEmpty { samples }
        return preferred.associateBy { it.sample.name }.mapValues { it.value.sample }
    }

    fun parseProcessSummary(output: String): ProcessInfo {
        val lines = output.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return ProcessInfo(0, 0, "--")
        val items = lines.mapNotNull(::parseProcessLine).take(64)
        val running = items.count { it.state.startsWith("R") }
            .takeIf { items.isNotEmpty() }
            ?: lines.count { it.startsWith("R") }
        val top = items.firstOrNull()?.command
            ?: lines.first().substringAfterLast(' ').ifBlank { "--" }
        return ProcessInfo(lines.size, running, cleanMetricLabel(top), items)
    }

    fun parseSystemdServiceSummary(allServicesOutput: String, failedServicesOutput: String): ServiceInfo {
        val allItems = allServicesOutput.lineSequence().mapNotNull(::parseServiceLine).take(128).toList()
        val all = allItems.size.takeIf { it > 0 } ?: allServicesOutput.lineSequence().count(::isSystemdServiceRow)
        val failedItems = failedServicesOutput.lineSequence().mapNotNull(::parseServiceLine).take(32).toList()
        return ServiceInfo(total = all.coerceAtLeast(failedItems.size), failed = failedItems.size, failedItems = failedItems, items = allItems)
    }

    fun parseSensors(output: String): List<SensorMetric> {
        if (output.isBlank()) return emptyList()
        return output.split(Regex("\\n\\s*\\n"))
            .mapNotNull(::parseSensorBlock)
            .take(32)
    }

    fun parsePowerSupplies(output: String): List<BatteryMetric> {
        if (output.isBlank()) return emptyList()
        return output.split(Regex("\\n\\s*\\n"))
            .mapNotNull(::parsePowerSupplyBlock)
            .take(16)
    }

    fun parseSmartDisks(output: String): List<SmartDiskMetric> {
        if (output.isBlank()) return emptyList()
        return splitJsonObjects(output)
            .mapNotNull(::parseSmartDiskBlock)
            .take(16)
    }

    fun parseLsblkFilesystems(output: String): List<FilesystemMetric> {
        if (output.isBlank()) return emptyList()
        return Regex("""[{][^{}]*[}]""")
            .findAll(output)
            .mapNotNull { parseLsblkFilesystemObject(it.value) }
            .distinctBy { "${it.sourcePath.ifBlank { it.mountPoint }}:${it.filesystem}" }
            .sortedWith(filesystemPriorityComparator())
            .take(32)
            .toList()
    }

    fun parseDfFilesystems(output: String): List<FilesystemMetric> {
        if (output.isBlank()) return emptyList()
        return output.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 7) return@mapNotNull null
                val totalKb = parts[2].toLongOrNull() ?: return@mapNotNull null
                val usedKb = parts[3].toLongOrNull() ?: return@mapNotNull null
                val sourcePath = parts[0]
                val filesystem = parts[1]
                val mountPoint = parts.last()
                if (totalKb <= 0 || mountPoint.isBlank()) return@mapNotNull null
                if (!shouldShowFilesystem(sourcePath, filesystem, mountPoint)) return@mapNotNull null
                FilesystemMetric(
                    mountPoint = cleanMetricLabel(mountPoint),
                    filesystem = cleanMetricLabel(filesystem),
                    usedGb = usedKb / 1_048_576f,
                    totalGb = totalKb / 1_048_576f,
                    sourcePath = cleanMetricLabel(sourcePath)
                )
            }
            .distinctBy { "${it.sourcePath}:${it.mountPoint}" }
            .sortedWith(filesystemPriorityComparator())
            .take(32)
            .toList()
    }

    fun parsePveResources(output: String): List<PveResourceMetric> {
        if (output.isBlank()) return emptyList()
        return Regex("""[{][^{}]*[}]""")
            .findAll(output)
            .mapNotNull { parsePveResourceObject(it.value) }
            .distinctBy { it.id }
            .take(64)
            .toList()
    }

    fun parseContainers(output: String): List<ContainerMetric> {
        if (output.isBlank()) return emptyList()
        val parsed = output.lineSequence()
            .mapNotNull(::parseContainerLine)
            .toList()
        val stats = parsed.filter { it.hasContainerStats() }.associateBy { "${it.engine}:${it.id}" }
        return parsed
            .filterNot { it.isStatsOnlyContainer() }
            .map { container ->
                val stat = stats["${container.engine}:${container.id}"]
                    ?: stats.entries.firstOrNull { (key, _) ->
                        val statId = key.substringAfter(':')
                        key.substringBefore(':') == container.engine &&
                            (statId.startsWith(container.id) || container.id.startsWith(statId))
                    }?.value
                    ?: return@map container
                container.copy(
                    cpuPercent = stat.cpuPercent,
                    memoryPercent = stat.memoryPercent,
                    memoryUsedBytes = stat.memoryUsedBytes,
                    memoryMaxBytes = stat.memoryMaxBytes,
                    networkIo = stat.networkIo,
                    blockIo = stat.blockIo
                )
            }
            .distinctBy { "${it.engine}:${it.id}" }
            .take(64)
            .toList()
    }

    fun parseContainerImages(output: String): List<ContainerImageMetric> {
        if (output.isBlank()) return emptyList()
        return output.lineSequence()
            .mapNotNull(::parseContainerImageLine)
            .distinctBy { "${it.engine}:${it.id}" }
            .take(128)
            .toList()
    }

    fun parseNvidiaGpus(output: String): List<GpuMetric> {
        if (output.isBlank()) return emptyList()
        return Regex("""<gpu\b[\s\S]*?</gpu>""")
            .findAll(output)
            .mapNotNull { parseNvidiaGpuBlock(it.value) }
            .take(16)
            .toList()
    }

    fun parseAmdGpus(output: String): List<GpuMetric> {
        if (output.isBlank() || !output.trimStart().startsWith("[")) return emptyList()
        return splitJsonObjects(output)
            .mapNotNull { parseAmdGpuBlock(it) }
            .take(16)
            .toList()
    }

    fun cpuUsagePercent(first: ProcStatCpu, second: ProcStatCpu): Int {
        val idleDelta = (second.idle + second.ioWait) - (first.idle + first.ioWait)
        val totalDelta = second.total() - first.total()
        if (totalDelta <= 0) return 0
        return (((totalDelta - idleDelta).toFloat() / totalDelta) * 100).toInt().coerceIn(0, 100)
    }

    fun cpuFieldPercent(first: ProcStatCpu, second: ProcStatCpu, field: (ProcStatCpu) -> Long): Int {
        val totalDelta = second.total() - first.total()
        if (totalDelta <= 0) return 0
        return (((field(second) - field(first)).toFloat() / totalDelta) * 100).toInt().coerceIn(0, 100)
    }

    private fun ProcStatCpu.total(): Long {
        return user + nice + system + idle + ioWait + irq + softIrq + steal
    }

    private fun isSystemdServiceRow(line: String): Boolean {
        val unit = line.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        return unit.endsWith(".service")
    }

    private fun parseProcessLine(line: String): ProcessMetric? {
        val tabParts = line.trim().split('\t')
        if (tabParts.size >= 10) {
            return ProcessMetric(
                pid = tabParts[0].toIntOrNull()?.takeIf { it > 0 },
                parentPid = tabParts[1].toIntOrNull()?.takeIf { it > 0 },
                user = cleanMetricLabel(tabParts[2]),
                state = cleanMetricLabel(tabParts[3]),
                cpuPercent = tabParts[4].toFloatOrNull(),
                memoryPercent = tabParts[5].toFloatOrNull(),
                rssKb = tabParts[6].toLongOrNull()?.takeIf { it >= 0 },
                virtualSizeKb = tabParts[7].toLongOrNull()?.takeIf { it >= 0 },
                elapsed = cleanMetricLabel(tabParts[8]),
                command = cleanMetricLabel(tabParts.drop(9).joinToString(" ").ifBlank { tabParts[9] })
            )
        }
        val parts = line.trim().split(Regex("\\s+"), limit = 5)
        if (parts.size >= 5 && parts[0].toIntOrNull() != null) {
            return ProcessMetric(
                state = cleanMetricLabel(parts[1]),
                cpuPercent = parts[2].toFloatOrNull(),
                memoryPercent = parts[3].toFloatOrNull(),
                command = cleanMetricLabel(parts[4]),
                pid = parts[0].toIntOrNull()?.takeIf { it > 0 }
            )
        }
        if (parts.size >= 5) {
            return ProcessMetric(
                state = cleanMetricLabel(parts[0]),
                cpuPercent = parts[1].toFloatOrNull(),
                memoryPercent = parts[2].toFloatOrNull(),
                command = cleanMetricLabel(parts.drop(3).joinToString(" "))
            )
        }
        val legacy = line.trim().split(Regex("\\s+"), limit = 4)
        if (legacy.size >= 4) {
            return ProcessMetric(
                state = cleanMetricLabel(legacy[0]),
                cpuPercent = legacy[1].toFloatOrNull(),
                memoryPercent = legacy[2].toFloatOrNull(),
                command = cleanMetricLabel(legacy[3])
            )
        }
        val fallback = line.trim().split(Regex("\\s+"), limit = 2)
        val command = fallback.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return ProcessMetric(
            state = cleanMetricLabel(fallback[0]),
            cpuPercent = null,
            memoryPercent = null,
            command = cleanMetricLabel(command)
        )
    }

    private fun parseServiceLine(line: String): ServiceMetric? {
        val parts = line.trim().split(Regex("\\s+"), limit = 5)
        val unit = parts.getOrNull(0)?.takeIf { it.endsWith(".service") } ?: return null
        return ServiceMetric(
            unit = cleanMetricLabel(unit),
            load = cleanMetricLabel(parts.getOrNull(1).orEmpty().ifBlank { "--" }),
            active = cleanMetricLabel(parts.getOrNull(2).orEmpty().ifBlank { "--" }),
            sub = cleanMetricLabel(parts.getOrNull(3).orEmpty().ifBlank { "--" }),
            description = cleanMetricLabel(parts.getOrNull(4).orEmpty().ifBlank { "--" })
        )
    }

    private fun cleanMetricLabel(value: String): String {
        return value.map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .take(80)
            .trim()
            .ifBlank { "--" }
    }

    private fun parseSensorBlock(block: String): SensorMetric? {
        val lines = block.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toList()
        val device = lines.firstOrNull()?.trim()?.takeIf { ':' !in it } ?: return null
        val adapter = lines.firstOrNull { it.startsWith("Adapter:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            .orEmpty()
        val row = lines.firstOrNull { line ->
            val value = line.substringAfter(':', "").trim()
            line.contains(':') && sensorValueRegex.containsMatchIn(value)
        } ?: return null
        return SensorMetric(
            device = cleanMetricLabel(device),
            adapter = cleanMetricLabel(adapter.ifBlank { "Sensor" }),
            label = cleanMetricLabel(row.substringBefore(':')),
            value = cleanMetricLabel(row.substringAfter(':').trim())
        )
    }

    private val sensorValueRegex = Regex("""[-+]?\d+(?:\.\d+)?\s*(?:°C|C|V|mV|A|RPM|W)\b""", RegexOption.IGNORE_CASE)

    private fun parsePowerSupplyBlock(block: String): BatteryMetric? {
        val values = block.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                val split = trimmed.indexOf('=')
                if (split <= 0) return@mapNotNull null
                trimmed.substring(0, split) to trimmed.substring(split + 1)
            }
            .toMap()
        val name = values["POWER_SUPPLY_NAME"]?.trim().orEmpty()
        val type = values["POWER_SUPPLY_TYPE"].orEmpty()
        val status = values["POWER_SUPPLY_STATUS"]?.trim().orEmpty()
        val capacity = values["POWER_SUPPLY_CAPACITY"]?.toIntOrNull()?.coerceIn(0, 100)
        val looksLikeBattery = type.equals("Battery", ignoreCase = true) ||
            status.equals("Charging", ignoreCase = true) ||
            status.equals("Discharging", ignoreCase = true) ||
            values.containsKey("POWER_SUPPLY_CHARGE_FULL") ||
            values.containsKey("POWER_SUPPLY_ENERGY_FULL")
        if (name.isBlank() || !looksLikeBattery || (status.isBlank() && capacity == null)) return null
        return BatteryMetric(
            name = cleanMetricLabel(name),
            status = cleanMetricLabel(status.ifBlank { "--" }),
            capacityPercent = capacity,
            health = cleanMetricLabel(values["POWER_SUPPLY_HEALTH"].orEmpty().ifBlank { "--" }),
            technology = cleanMetricLabel(values["POWER_SUPPLY_TECHNOLOGY"].orEmpty().ifBlank { "--" }),
            timeToEmptySeconds = values["POWER_SUPPLY_TIME_TO_EMPTY_NOW"]?.toLongOrNull()?.takeIf { it > 0L },
            timeToFullSeconds = values["POWER_SUPPLY_TIME_TO_FULL_NOW"]?.toLongOrNull()?.takeIf { it > 0L }
        )
    }

    private fun parseSmartDiskBlock(block: String): SmartDiskMetric? {
        val device = stringValue(block, "name") ?: return null
        val attributes = block.substringAfter("\"table\"", "")
        fun rawString(attribute: String): String? {
            val match = Regex(""""name"\s*:\s*"${Regex.escape(attribute)}"""").find(attributes) ?: return null
            return stringValue(attributes.substring(match.range.first).substringAfter("\"raw\"", ""), "string")
        }
        return SmartDiskMetric(
            device = cleanMetricLabel(device),
            model = cleanNullableMetricLabel(stringValue(block, "model_name") ?: stringValue(block, "model_family")),
            serial = cleanNullableMetricLabel(stringValue(block, "serial_number")),
            healthy = parseSmartHealthy(block, attributes),
            selfTestStatus = parseSmartSelfTestStatus(block),
            temperatureCelsius = intValue(block, "current"),
            powerOnHours = intValue(block, "hours") ?: rawString("Power_On_Hours")?.toIntOrNull(),
            powerCycleCount = intValue(block, "power_cycle_count") ?: rawString("Power_Cycle_Count")?.toIntOrNull(),
            lifeLeftPercent = rawString("SSD_Life_Left")?.toIntOrNull(),
            lifetimeWritesGiB = rawString("Lifetime_Writes_GiB")?.toLongOrNull()
                ?: rawString("Flash_Writes_GiB")?.toLongOrNull(),
            lifetimeReadsGiB = rawString("Lifetime_Reads_GiB")?.toLongOrNull(),
            unsafeShutdowns = rawString("Unsafe_Shutdown_Count")?.toIntOrNull()
        )
    }

    private fun cleanNullableMetricLabel(value: String?): String? {
        return value?.let(::cleanMetricLabel)?.takeIf { it != "--" }
    }

    private fun parseSmartHealthy(block: String, attributes: String): Boolean? {
        val passed = boolValue(block, "passed")
        if (passed != null) return passed
        val status = stringValue(block.substringAfter("\"smart_status\"", ""), "status")?.lowercase()
        if (status != null) {
            if ("fail" in status) return false
            if ("pass" in status || "ok" in status || "healthy" in status) return true
        }
        if (Regex(""""when_failed"\s*:\s*"(?!never"|")([^"]+)"""", RegexOption.IGNORE_CASE).containsMatchIn(attributes)) return false
        return attributes.takeIf { it.isNotBlank() }?.let { true }
    }

    private fun parseSmartSelfTestStatus(block: String): String? {
        val selfTest = listOf("\"self_test\"", "\"self_test_log\"", "\"ata_smart_self_test_log\"")
            .firstNotNullOfOrNull { key -> block.substringAfter(key, "").takeIf { it.isNotBlank() } }
            ?: return null
        return cleanNullableMetricLabel(stringValue(selfTest, "string") ?: stringValue(selfTest, "status"))
    }

    private fun parseLsblkFilesystemObject(block: String): FilesystemMetric? {
        val fstype = stringValue(block, "fstype") ?: return null
        val mountPoint = stringValue(block, "mountpoint") ?: return null
        val totalBytes = longValue(block, "fssize") ?: return null
        val usedBytes = longValue(block, "fsused") ?: return null
        val path = stringValue(block, "path") ?: stringValue(block, "kname") ?: stringValue(block, "name").orEmpty()
        if (totalBytes <= 0 || mountPoint.isBlank()) return null
        if (!shouldShowFilesystem(path, fstype, mountPoint)) return null
        return FilesystemMetric(
            mountPoint = cleanMetricLabel(mountPoint),
            filesystem = cleanMetricLabel(fstype),
            usedGb = usedBytes / 1_073_741_824f,
            totalGb = totalBytes / 1_073_741_824f,
            sourcePath = cleanMetricLabel(path)
        )
    }

    private fun filesystemPriorityComparator(): Comparator<FilesystemMetric> {
        return compareBy<FilesystemMetric>(
            { if (it.mountPoint == "/") 0 else 1 },
            { if (it.sourcePath.startsWith("/dev/") || it.sourcePath.startsWith("/dev/mapper/")) 0 else 1 },
            { if (it.filesystem in setOf("tmpfs", "devtmpfs", "overlay", "squashfs", "proc", "sysfs", "cgroup", "cgroup2")) 1 else 0 },
            { it.mountPoint.count { char -> char == '/' } },
            { it.mountPoint.length },
            { it.mountPoint }
        )
    }

    private fun shouldShowFilesystem(sourcePath: String, filesystem: String, mountPoint: String): Boolean {
        if (sourcePath.startsWith("/dev") || sourcePath.startsWith("//") || mountPoint.startsWith("/mnt")) return true
        return filesystem !in setOf("tmpfs", "devtmpfs", "overlay", "squashfs", "proc", "sysfs", "cgroup", "cgroup2", "shm")
    }

    private fun parsePveResourceObject(block: String): PveResourceMetric? {
        val id = stringValue(block, "id") ?: return null
        val type = stringValue(block, "type") ?: return null
        return PveResourceMetric(
            id = cleanMetricLabel(id),
            name = cleanMetricLabel(stringValue(block, "name") ?: stringValue(block, "storage") ?: id.substringAfterLast('/')),
            type = cleanMetricLabel(type),
            status = cleanMetricLabel(stringValue(block, "status") ?: "--"),
            node = cleanMetricLabel(stringValue(block, "node") ?: "--"),
            vmId = intValue(block, "vmid"),
            cpuUsagePercent = floatValue(block, "cpu")?.let { (it * 100f).toInt().coerceIn(0, 100) },
            memoryUsedBytes = longValue(block, "mem"),
            memoryMaxBytes = longValue(block, "maxmem"),
            diskUsedBytes = longValue(block, "disk"),
            diskMaxBytes = longValue(block, "maxdisk"),
            uptimeSeconds = longValue(block, "uptime")
        )
    }

    private fun parseContainerLine(line: String): ContainerMetric? {
        val trimmed = line.trim().replace("\\t", "\t")
        if (trimmed.isBlank() || trimmed.startsWith("CONTAINER ID", ignoreCase = true) || trimmed.startsWith("ID ", ignoreCase = true)) return null
        val tabParts = trimmed.split('\t')
        val fields = if (tabParts.size >= 6) {
            tabParts
        } else {
            val parts = trimmed.split(Regex("\\s{2,}"))
            if (parts.size < 4) return null
            listOf("docker", parts[0], parts[2], parts[3], parts[1], parts[1])
        }
        val engine = fields[0].ifBlank { "docker" }
        val id = fields[1].ifBlank { return null }
        return ContainerMetric(
            id = cleanMetricLabel(id),
            name = cleanMetricLabel(fields.getOrNull(2).orEmpty().ifBlank { id.take(12) }),
            image = cleanMetricLabel(fields.getOrNull(3).orEmpty().ifBlank { "--" }),
            status = cleanMetricLabel(fields.getOrNull(4).orEmpty().ifBlank { "--" }),
            state = cleanMetricLabel(fields.getOrNull(5).orEmpty().ifBlank { fields.getOrNull(4).orEmpty().ifBlank { "--" } }),
            engine = cleanMetricLabel(engine),
            cpuPercent = percentFloat(fields.getOrNull(6)),
            memoryPercent = percentFloat(fields.getOrNull(8)),
            memoryUsedBytes = fields.getOrNull(7)?.substringBefore('/')?.trim()?.memoryBytes(),
            memoryMaxBytes = fields.getOrNull(7)?.substringAfter('/', "")?.trim()?.memoryBytes(),
            networkIo = cleanMetricLabel(fields.getOrNull(9).orEmpty()),
            blockIo = cleanMetricLabel(fields.getOrNull(10).orEmpty())
        )
    }

    private fun parseContainerImageLine(line: String): ContainerImageMetric? {
        val trimmed = line.trim().replace("\\t", "\t")
        if (trimmed.isBlank() || trimmed.startsWith("REPOSITORY", ignoreCase = true)) return null
        val tabParts = trimmed.split('\t')
        val fields = if (tabParts.size >= 6) {
            tabParts
        } else {
            val parts = trimmed.split(Regex("\\s{2,}"))
            if (parts.size < 5) return null
            listOf("docker", parts[0], parts[1], parts[2], parts[3], parts[4])
        }
        val id = fields[3].ifBlank { return null }
        return ContainerImageMetric(
            engine = cleanMetricLabel(fields[0].ifBlank { "docker" }),
            repository = cleanMetricLabel(fields[1].ifBlank { "<none>" }),
            tag = cleanMetricLabel(fields[2].ifBlank { "<none>" }),
            id = cleanMetricLabel(id),
            created = cleanMetricLabel(fields.getOrNull(4).orEmpty().ifBlank { "--" }),
            size = cleanMetricLabel(fields.getOrNull(5).orEmpty().ifBlank { "--" })
        )
    }

    private fun ContainerMetric.hasContainerStats(): Boolean {
        return cpuPercent != null || memoryPercent != null || memoryUsedBytes != null || memoryMaxBytes != null
    }

    private fun ContainerMetric.isStatsOnlyContainer(): Boolean {
        return hasContainerStats() && image == "--" && status == "--" && state == "--"
    }

    private fun parseNvidiaGpuBlock(block: String): GpuMetric? {
        val name = xmlValue(block, "product_name") ?: return null
        return GpuMetric(
            id = cleanMetricLabel(xmlValue(block, "uuid") ?: Regex("""<gpu\s+id="([^"]+)"""").find(block)?.groupValues?.getOrNull(1) ?: name),
            name = cleanMetricLabel(name),
            vendor = "NVIDIA",
            utilizationPercent = percentValue(xmlValue(block, "gpu_util")),
            memoryUsedMiB = intWithUnit(xmlValue(block, "used")),
            memoryTotalMiB = intWithUnit(xmlValue(block, "total")),
            temperatureCelsius = intWithUnit(xmlValue(block, "gpu_temp")),
            powerDrawWatts = floatWithUnit(xmlValue(block, "power_draw")),
            powerLimitWatts = floatWithUnit(xmlValue(block, "current_power_limit")),
            fanSpeed = cleanMetricLabel(xmlValue(block, "fan_speed") ?: "--"),
            clockMhz = intWithUnit(xmlValue(block, "graphics_clock"))
        )
    }

    private fun parseAmdGpuBlock(block: String): GpuMetric? {
        val name = stringValue(block, "name")
            ?: stringValue(block, "card_model")
            ?: stringValue(block, "device_name")
            ?: return null
        val memoryBlock = block.substringAfter("\"memory\"", block.substringAfter("\"vram\"", ""))
        return GpuMetric(
            id = cleanMetricLabel(stringValue(block, "device_id") ?: stringValue(block, "gpu_id") ?: name),
            name = cleanMetricLabel(name),
            vendor = "AMD",
            utilizationPercent = intValue(block, "utilization")
                ?: percentValue(stringValue(block, "gpu_util"))
                ?: intValue(block, "activity"),
            memoryUsedMiB = intValue(memoryBlock, "used")
                ?: intValue(memoryBlock, "used_memory")
                ?: intWithUnit(stringValue(memoryBlock, "used")),
            memoryTotalMiB = intValue(memoryBlock, "total")
                ?: intValue(memoryBlock, "total_memory")
                ?: intWithUnit(stringValue(memoryBlock, "total")),
            temperatureCelsius = intValue(block, "temp")
                ?: intWithUnit(stringValue(block, "temperature"))
                ?: intValue(block, "gpu_temp"),
            powerDrawWatts = floatValue(block, "power_draw")
                ?: floatValue(block, "current_power")
                ?: floatWithUnit(stringValue(block, "power")),
            powerLimitWatts = floatValue(block, "power_cap")
                ?: floatValue(block, "power_limit"),
            fanSpeed = stringValue(block, "fan_rpm")
                ?: intValue(block, "fan_speed")?.let { "$it RPM" },
            clockMhz = intValue(block, "clock_speed")
                ?: intWithUnit(stringValue(block, "sclk"))
                ?: intValue(block, "gpu_clock")
        )
    }

    private fun splitJsonObjects(output: String): List<String> {
        val blocks = mutableListOf<String>()
        var depth = 0
        var start = -1
        output.forEachIndexed { index, char ->
            when (char) {
                '{' -> {
                    if (depth == 0) start = index
                    depth++
                }
                '}' -> {
                    if (depth > 0) depth--
                    if (depth == 0 && start >= 0) {
                        blocks += output.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return blocks
    }

    private fun stringValue(input: String, key: String): String? {
        return Regex(""""${Regex.escape(key)}"\s*:\s*"([^"]*)"""")
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun intValue(input: String, key: String): Int? {
        return Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+)""")
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun longValue(input: String, key: String): Long? {
        val raw = Regex(""""${Regex.escape(key)}"\s*:\s*(?:"(\d+)"|(-?\d+))""")
            .find(input)
            ?.groupValues
            ?: return null
        return raw.getOrNull(1)?.ifBlank { raw.getOrNull(2).orEmpty() }?.toLongOrNull()
    }

    private fun boolValue(input: String, key: String): Boolean? {
        return Regex(""""${Regex.escape(key)}"\s*:\s*(true|false)""", RegexOption.IGNORE_CASE)
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.toBooleanStrictOrNull()
    }

    private fun floatValue(input: String, key: String): Float? {
        return Regex(""""${Regex.escape(key)}"\s*:\s*(-?\d+(?:\.\d+)?)""")
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
    }

    private fun xmlValue(input: String, tag: String): String? {
        return Regex("""<${Regex.escape(tag)}>([\s\S]*?)</${Regex.escape(tag)}>""")
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
    }

    private fun intWithUnit(value: String?): Int? = value?.let { Regex("""-?\d+""").find(it)?.value?.toIntOrNull() }

    private fun floatWithUnit(value: String?): Float? = value?.let { Regex("""-?\d+(?:\.\d+)?""").find(it)?.value?.toFloatOrNull() }

    private fun percentValue(value: String?): Int? = intWithUnit(value)?.coerceIn(0, 100)

    private fun percentFloat(value: String?): Float? = floatWithUnit(value)?.coerceIn(0f, 100f)

    private fun String.memoryBytes(): Long? {
        val match = Regex("""(-?\d+(?:\.\d+)?)\s*([KMGT]?i?B?)?""", RegexOption.IGNORE_CASE).find(this) ?: return null
        val amount = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null
        val unit = match.groupValues.getOrNull(2).orEmpty().lowercase()
        val multiplier = when (unit) {
            "k", "kb", "kib" -> 1024.0
            "m", "mb", "mib" -> 1024.0 * 1024.0
            "g", "gb", "gib" -> 1024.0 * 1024.0 * 1024.0
            "t", "tb", "tib" -> 1024.0 * 1024.0 * 1024.0 * 1024.0
            else -> 1.0
        }
        return (amount.coerceAtLeast(0.0) * multiplier).toLong()
    }

    private fun String.isLikelyDiskDevice(): Boolean {
        val lower = lowercase()
        if (lower.any { it == '/' }) return false
        if (Regex(".*p\\d+$").matches(lower)) return false
        if (Regex("dasd[a-z]+\\d+$").matches(lower)) return false
        if (lower.startsWith("dm-") || lower.startsWith("md")) return true
        if (lower.startsWith("nbd") || lower.startsWith("rbd")) return true
        if (Regex("zd\\d+$").matches(lower) ||
            Regex("dasd[a-z]+$").matches(lower) ||
            Regex("bcache\\d+$").matches(lower)
        ) return true
        if (Regex(".*\\d+$").matches(lower) && !lower.startsWith("nvme") && !lower.startsWith("mmcblk")) return false
        return lower.startsWith("sd") ||
            lower.startsWith("hd") ||
            lower.startsWith("vd") ||
            lower.startsWith("xvd") ||
            (lower.startsWith("nvme") && !Regex("nvme\\d+n\\d+p\\d+").matches(lower)) ||
            (lower.startsWith("mmcblk") && !Regex("mmcblk\\d+p\\d+").matches(lower))
    }

    private fun String.isLikelyDiskPartition(): Boolean {
        val lower = lowercase()
        if (lower.any { it == '/' }) return false
        return Regex("sd[a-z]+\\d+$").matches(lower) ||
            Regex("hd[a-z]+\\d+$").matches(lower) ||
            Regex("vd[a-z]+\\d+$").matches(lower) ||
            Regex("xvd[a-z]+\\d+$").matches(lower) ||
            Regex("nvme\\d+n\\d+p\\d+$").matches(lower) ||
            Regex("mmcblk\\d+p\\d+$").matches(lower) ||
            Regex("nbd\\d+p\\d+$").matches(lower) ||
            Regex("rbd\\d+p\\d+$").matches(lower) ||
            Regex("zd\\d+p\\d+$").matches(lower) ||
            Regex("dasd[a-z]+\\d+$").matches(lower) ||
            Regex("bcache\\d+p\\d+$").matches(lower)
    }

    private data class DiskStatCandidate(
        val sample: DiskStatSample,
        val wholeDevice: Boolean
    )
}
