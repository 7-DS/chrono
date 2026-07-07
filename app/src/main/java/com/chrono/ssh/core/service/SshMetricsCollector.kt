package com.chrono.ssh.core.service

import com.chrono.ssh.core.metrics.LinuxMetricParsers
import com.chrono.ssh.core.metrics.ProcStatCpu
import com.chrono.ssh.core.model.CpuCoreMetrics
import com.chrono.ssh.core.model.CpuMetrics
import com.chrono.ssh.core.model.DiskMetrics
import com.chrono.ssh.core.model.DockerSummary
import com.chrono.ssh.core.model.MemoryMetrics
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.NetworkHistory
import com.chrono.ssh.core.model.NetworkInterfaceMetric
import com.chrono.ssh.core.model.NetworkMetrics
import com.chrono.ssh.core.model.ProcessSummary
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.model.ServiceSummary
import kotlinx.coroutines.withTimeoutOrNull

class SshMetricsCollector : MetricsCollector {
    private companion object {
        const val FAST_COMMAND_TIMEOUT_SECONDS = 3L
        const val OPTIONAL_COMMAND_TIMEOUT_SECONDS = 5L
        const val OPTIONAL_COMMAND_TIMEOUT_MS = 6_000L
        const val FAST_SAMPLE_SECONDS = 0.25f
        const val EXTENDED_VNSTAT_INTERFACE_LIMIT = 1
        const val HOST_TOOL_PATH = "PATH=\$PATH:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    }

    var lastVnStatDiagnostic: String = "vnStat: not collected"
        private set
    var lastCollectionDiagnostic: String = ""
        private set

    override suspend fun collect(profile: ServerProfile, session: SshSession): MetricSnapshot {
        val commandFailures = mutableListOf<String>()

        suspend fun execRequired(command: String): String = session.execOk(command, FAST_COMMAND_TIMEOUT_SECONDS)

        val bundle = execRequired(fastMetricsCommand())
        val sections = mutableMapOf<String, String>()
        fun section(name: String): String = sections.getOrPut(name) { bundleSection(bundle, name) }
        commandFailures += listOf("STAT1", "STAT2", "MEMINFO", "NET1", "NET2")
            .filter { section(it).isBlank() }
            .map { "fast:$it missing" }
        if (section("DF").isBlank() && section("STATFS").isBlank()) commandFailures += "fast:disk capacity missing"

        val firstProcStat = section("STAT1")
        val firstCpu = firstProcStat.let(LinuxMetricParsers::parseProcStatCpu)
        val firstCores = firstProcStat.let(LinuxMetricParsers::parseProcStatCores)
        val firstNet = section("NET1")
        val firstDiskStats = section("DISKSTATS1")
        val sampleSeconds = FAST_SAMPLE_SECONDS
        val secondProcStat = section("STAT2")
        val secondCpu = secondProcStat.let(LinuxMetricParsers::parseProcStatCpu)
        val secondCores = secondProcStat.let(LinuxMetricParsers::parseProcStatCores)
        val secondNet = section("NET2")
        val secondDiskStats = section("DISKSTATS2")
        val mem = section("MEMINFO").let(LinuxMetricParsers::parseMemInfo)
        val uptime = LinuxMetricParsers.parseUptime(section("UPTIME"))
        val load = LinuxMetricParsers.parseLoadAverage(section("LOADAVG"))
        val cpuInfo = LinuxMetricParsers.parseCpuModel(section("CPUINFO"))
        val disk = LinuxMetricParsers.parseDf(section("DF"))
            ?: LinuxMetricParsers.parseStatFilesystem(section("STATFS"))
        val filesystems = LinuxMetricParsers.parseLsblkFilesystems(section("LSBLK"))
            .ifEmpty { LinuxMetricParsers.parseDfFilesystems(section("DFALL")) }
        val diskRate = parseDiskRates(firstDiskStats, secondDiskStats, sampleSeconds)
        val addresses = parseAddresses(section("ADDR4"))
        val vnStat = null
        lastVnStatDiagnostic = "vnStat: skipped during fast refresh"
        val network = parseNetwork(firstNet, secondNet, addresses, sampleSeconds, vnStat)
        val processCounts = parseCountPair(section("PSCOUNT"))
        val serviceCounts = parseCountPair(section("SYSTEMDCOUNT"))
        val containerCounts = parseCountPair(section("CONTAINERCOUNT"))
        val processes = ProcessSummary(processCounts.first, processCounts.second, "--")
        val services = ServiceSummary(serviceCounts.first, serviceCounts.second)
        val docker = DockerSummary(containerCounts.first, containerCounts.second)
        val cpuMetrics = cpuMetrics(firstCpu, secondCpu, firstCores, secondCores, cpuInfo, load)
        val memoryMetrics = mem?.let {
            MemoryMetrics(
                usedMb = (it.usedKb / 1024).toInt(),
                totalMb = (it.totalKb / 1024).toInt(),
                swapUsedMb = (it.swapUsedKb / 1024).toInt(),
                swapTotalMb = (it.swapTotalKb / 1024).toInt()
            )
        } ?: MemoryMetrics(0, 0, 0, 0)
        lastCollectionDiagnostic = collectionDiagnostic(commandFailures)

        return MetricSnapshot(
            serverId = profile.id,
            status = ServerStatus.Online,
            latencyMs = null,
            uptime = uptime,
            cpu = cpuMetrics,
            memory = memoryMetrics,
            disk = disk?.let {
                DiskMetrics(it.usedGb, it.totalGb, diskRate.read, diskRate.write, diskRate.readTotal, diskRate.writeTotal, filesystems)
            } ?: DiskMetrics(0f, 0f, diskRate.read, diskRate.write, diskRate.readTotal, diskRate.writeTotal, filesystems),
            network = network,
            processes = processes,
            services = services,
            docker = docker,
            collectedAtEpochMillis = System.currentTimeMillis(),
            sensors = emptyList(),
            batteries = emptyList(),
            smartDisks = emptyList(),
            pveResources = emptyList(),
            gpus = emptyList()
        )
    }

    private fun fastMetricsCommand(): String = """
        chrono_timeout() { if command -v timeout >/dev/null 2>&1; then timeout "$@"; else seconds="$1"; shift; "$@" & pid="$!"; ( sleep "${'$'}seconds"; kill "${'$'}pid" 2>/dev/null ) & guard="$!"; wait "${'$'}pid"; status="$?"; kill "${'$'}guard" 2>/dev/null; return "${'$'}status"; fi; }
        printf '\n__CHRONO_STAT1__\n'; chrono_timeout 1 cat /proc/stat 2>/dev/null || true
        printf '\n__CHRONO_NET1__\n'; chrono_timeout 1 cat /proc/net/dev 2>/dev/null || true
        printf '\n__CHRONO_DISKSTATS1__\n'; chrono_timeout 1 cat /proc/diskstats 2>/dev/null || true
        sleep $FAST_SAMPLE_SECONDS
        printf '\n__CHRONO_STAT2__\n'; chrono_timeout 1 cat /proc/stat 2>/dev/null || true
        printf '\n__CHRONO_NET2__\n'; chrono_timeout 1 cat /proc/net/dev 2>/dev/null || true
        printf '\n__CHRONO_DISKSTATS2__\n'; chrono_timeout 1 cat /proc/diskstats 2>/dev/null || true
        printf '\n__CHRONO_MEMINFO__\n'; chrono_timeout 1 cat /proc/meminfo 2>/dev/null || true
        printf '\n__CHRONO_UPTIME__\n'; chrono_timeout 1 cat /proc/uptime 2>/dev/null || true
        printf '\n__CHRONO_LOADAVG__\n'; chrono_timeout 1 cat /proc/loadavg 2>/dev/null || true
        printf '\n__CHRONO_CPUINFO__\n'; chrono_timeout 1 cat /proc/cpuinfo 2>/dev/null || true
        printf '\n__CHRONO_DF__\n'; chrono_timeout 1 df -kP / 2>/dev/null || true
        printf '\n__CHRONO_DFALL__\n'; chrono_timeout 1 df -kP -T 2>/dev/null || true
        printf '\n__CHRONO_STATFS__\n'; chrono_timeout 1 stat -f -c '%b %a %S' / 2>/dev/null || true
        printf '\n__CHRONO_LSBLK__\n'; if command -v lsblk >/dev/null 2>&1; then chrono_timeout 1 lsblk -bJ -o NAME,KNAME,PATH,FSTYPE,MOUNTPOINT,FSSIZE,FSUSED,FSAVAIL,FSUSE%,UUID 2>/dev/null; fi
        printf '\n__CHRONO_ADDR4__\n'; chrono_timeout 1 ip -o -4 addr show scope global 2>/dev/null || true
        printf '\n__CHRONO_PSCOUNT__\n'; chrono_timeout 1 sh -c "ps -e -o stat= 2>/dev/null | awk 'NF{total++} /^[[:space:]]*R/{running++} END{printf \"%d\t%d\n\", total, running}'" || true
        printf '\n__CHRONO_SYSTEMDCOUNT__\n'; chrono_timeout 1 sh -c "if command -v systemctl >/dev/null 2>&1; then total=${'$'}(systemctl list-units --type=service --all --no-legend --plain 2>/dev/null | wc -l); failed=${'$'}(systemctl --failed --no-legend --plain 2>/dev/null | wc -l); printf '%s\t%s\n' \"${'$'}total\" \"${'$'}failed\"; fi" || true
        printf '\n__CHRONO_CONTAINERCOUNT__\n'; chrono_timeout 1 sh -c "total=0; running=0; if command -v docker >/dev/null 2>&1; then total=${'$'}((total + ${'$'}(docker ps -a -q 2>/dev/null | wc -l))); running=${'$'}((running + ${'$'}(docker ps -q 2>/dev/null | wc -l))); fi; if command -v podman >/dev/null 2>&1; then total=${'$'}((total + ${'$'}(podman ps -a -q 2>/dev/null | wc -l))); running=${'$'}((running + ${'$'}(podman ps -q 2>/dev/null | wc -l))); fi; printf '%s\t%s\n' \"${'$'}total\" \"${'$'}running\"" || true
        printf '\n__CHRONO_END__\n'
    """.trimIndent()

    private fun parseCountPair(output: String): Pair<Int, Int> {
        val parts = output.trim().split(Regex("\\s+"))
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) to (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    internal fun fastMetricsCommandForTest(): String = fastMetricsCommand()

    private fun bundleSection(output: String, name: String): String {
        val start = "__CHRONO_${name}__"
        val startIndex = output.indexOf(start)
        if (startIndex < 0) return ""
        val contentStart = startIndex + start.length
        val endIndex = output.indexOf("\n__CHRONO_", contentStart).takeIf { it >= 0 } ?: output.length
        return output.substring(contentStart, endIndex).trim('\n', '\r')
    }

    suspend fun collectDetails(profile: ServerProfile, session: SshSession): MetricSnapshot {
        val commandFailures = mutableListOf<String>()

        suspend fun execOptional(command: String, label: String): String {
            return runCatching { withTimeoutOrNull(OPTIONAL_COMMAND_TIMEOUT_MS) { session.execOk(command, OPTIONAL_COMMAND_TIMEOUT_SECONDS) } }
                .onSuccess { if (it == null) commandFailures += "$label timeout" }
                .onFailure { commandFailures += label }
                .getOrNull()
                .orEmpty()
        }

        suspend fun execDiagnosticOptional(command: String, label: String): String {
            return runCatching { withTimeoutOrNull(OPTIONAL_COMMAND_TIMEOUT_MS) { session.execDiagnostic(command, OPTIONAL_COMMAND_TIMEOUT_SECONDS) } }
                .onSuccess { if (it == null) commandFailures += "$label timeout" }
                .onFailure { commandFailures += label }
                .getOrNull()
                .orEmpty()
        }

        val netDev = execOptional("cat /proc/net/dev", "network interfaces")
        val vnStatInterfaces = parseNetDev(netDev).keys
            .filterNot { it == "lo" }
            .distinct()
        val vnStatJson = execDiagnosticOptional(vnStatCommand(vnStatInterfaces, includeExtendedFallback = false), "vnStat primary")
        val primaryVnStat = VnStatParser.parse(vnStatJson)
        val fallbackVnStatJson = if (primaryVnStat.hasAllMainRanges()) {
            ""
        } else {
            execDiagnosticOptional(vnStatCommand(vnStatInterfaces, includeExtendedFallback = true), "vnStat fallback")
        }
        val mergedVnStatJson = listOf(vnStatJson, fallbackVnStatJson).filter { it.isNotBlank() }.joinToString("\n")
        val vnStat = VnStatParser.parse(mergedVnStatJson)
        lastVnStatDiagnostic = vnStatDiagnosticForTest(vnStatInterfaces, mergedVnStatJson, vnStat)
        val processes = parseProcesses(execOptional(processListCommand(), "process list"))
        val services = parseServices(
            allServices = execOptional(systemdListCommand(failedOnly = false), "service list"),
            failedServices = execOptional(systemdListCommand(failedOnly = true), "failed services")
        )
        val docker = parseDocker(
            listOutput = execOptional(containerListCommand(), "container list"),
            statsOutput = execOptional(containerStatsCommand(), "container stats"),
            imageOutput = execOptional(containerImagesCommand(), "container images")
        )
        val sensors = LinuxMetricParsers.parseSensors(execOptional("if command -v sensors >/dev/null 2>&1; then sensors; fi", "sensors"))
        val batteries = LinuxMetricParsers.parsePowerSupplies(execOptional("for f in /sys/class/power_supply/*/uevent; do [ -r \"\$f\" ] && cat \"\$f\" && printf '\\n'; done 2>/dev/null || true", "battery info"))
        val smartDisks = LinuxMetricParsers.parseSmartDisks(execOptional("if command -v smartctl >/dev/null 2>&1 && command -v lsblk >/dev/null 2>&1; then lsblk -ndo NAME,TYPE | awk '\$2==\"disk\"{print \"/dev/\"\$1}' | head -n 8 | while read -r d; do smartctl -A -j \"\$d\" 2>/dev/null; printf '\\n'; done; fi", "SMART disks"))
        val pveResources = LinuxMetricParsers.parsePveResources(execOptional("if command -v pvesh >/dev/null 2>&1; then pvesh get /cluster/resources --output-format json; fi", "Proxmox resources"))
        val gpus = LinuxMetricParsers.parseNvidiaGpus(execOptional("if command -v nvidia-smi >/dev/null 2>&1; then nvidia-smi -q -x; fi", "NVIDIA GPU")) +
            LinuxMetricParsers.parseAmdGpus(execOptional("if command -v amd-smi >/dev/null 2>&1; then amd-smi list --json; fi", "AMD GPU"))
        lastCollectionDiagnostic = collectionDiagnostic(commandFailures)
        return MetricSnapshot(
            serverId = profile.id,
            status = ServerStatus.Online,
            latencyMs = null,
            uptime = "--",
            cpu = CpuMetrics(0, 0, "Linux CPU", 0, 0, 0, 0, 0, 0f, 0f, 0f, emptyList()),
            memory = MemoryMetrics(0, 0, 0, 0),
            disk = DiskMetrics(0f, 0f, "--", "--"),
            network = parseNetwork(netDev, netDev, emptyMap(), 1f, vnStat),
            processes = processes,
            services = services,
            docker = docker,
            collectedAtEpochMillis = System.currentTimeMillis(),
            sensors = sensors,
            batteries = batteries,
            smartDisks = smartDisks,
            pveResources = pveResources,
            gpus = gpus
        )
    }

    private suspend fun SshSession.execOk(command: String, timeoutSeconds: Long): String {
        val result = execute(command, timeoutSeconds)
        return if (result.exitCode == 0 || result.stdout.isNotBlank()) result.stdout else ""
    }

    internal fun processListCommandForTest(): String = processListCommand()
    internal fun systemdListCommandForTest(failedOnly: Boolean): String = systemdListCommand(failedOnly)
    internal fun containerListCommandForTest(): String = containerListCommand()
    internal fun containerStatsCommandForTest(): String = containerStatsCommand()
    internal fun containerImagesCommandForTest(): String = containerImagesCommand()

    private fun processListCommand(): String = """
        $HOST_TOOL_PATH
        if ps -eo pid=,ppid=,user=,stat=,pcpu=,pmem=,rss=,vsz=,etimes=,args= --sort=-pcpu >/dev/null 2>&1; then
          ps -eo pid=,ppid=,user=,stat=,pcpu=,pmem=,rss=,vsz=,etimes=,args= --sort=-pcpu 2>/dev/null |
            awk '{pid=${'$'}1;ppid=${'$'}2;user=${'$'}3;stat=${'$'}4;pcpu=${'$'}5;pmem=${'$'}6;rss=${'$'}7;vsz=${'$'}8;etime=${'$'}9; sub(/^[^ ]+[ ]+[^ ]+[ ]+[^ ]+[ ]+[^ ]+[ ]+[^ ]+[ ]+[^ ]+[ ]+[^ ]+[ ]+[^ ]+[ ]+[^ ]+[ ]+/, ""); printf "%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n", pid, ppid, user, stat, pcpu, pmem, rss, vsz, etime, ${'$'}0}' |
            head -n 65
        elif ps auxww >/dev/null 2>&1; then
          ps auxww 2>/dev/null |
            awk 'NR>1 {user=${'$'}1;pid=${'$'}2;pcpu=${'$'}3;pmem=${'$'}4;vsz=${'$'}5;rss=${'$'}6;stat=${'$'}8;cmd=${'$'}11; for(i=12;i<=NF;i++) cmd=cmd " " ${'$'}i; printf "%s\t\t%s\t%s\t%s\t%s\t%s\t%s\t\t%s\n", pid, user, stat, pcpu, pmem, rss, vsz, cmd}' |
            head -n 65
        elif ps -eo pid=,stat=,pcpu=,pmem=,args= >/dev/null 2>&1; then
          ps -eo pid=,stat=,pcpu=,pmem=,args= 2>/dev/null | head -n 65
        elif ps -eo stat=,pcpu=,pmem=,comm= >/dev/null 2>&1; then
          ps -eo stat=,pcpu=,pmem=,comm= 2>/dev/null | head -n 65
        else
          ps 2>/dev/null | awk 'NR>1 {pid=${'$'}1;stat=${'$'}3;cmd=${'$'}4; for(i=5;i<=NF;i++) cmd=cmd " " ${'$'}i; printf "%s\t\t\t%s\t\t\t\t\t\t%s\n", pid, stat, cmd}' | head -n 65
        fi
    """.trimIndent()

    private fun systemdListCommand(failedOnly: Boolean): String {
        val base = if (failedOnly) "systemctl --failed --no-legend --plain" else "systemctl list-units --type=service --all --no-legend --plain"
        return """
            $HOST_TOOL_PATH
            if command -v systemctl >/dev/null 2>&1; then
              $base 2>/dev/null || $base --no-pager 2>/dev/null || true
            fi
        """.trimIndent()
    }

    private fun containerListCommand(): String = """
        $HOST_TOOL_PATH
        if command -v docker >/dev/null 2>&1; then
          docker ps -a --format 'docker\t{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.State}}' 2>/dev/null || sudo -n docker ps -a --format 'docker\t{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.State}}' 2>/dev/null || docker ps -a 2>/dev/null || sudo -n docker ps -a 2>/dev/null || true
        elif command -v sudo >/dev/null 2>&1 && sudo -n command -v docker >/dev/null 2>&1; then
          sudo -n docker ps -a --format 'docker\t{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.State}}' 2>/dev/null || sudo -n docker ps -a 2>/dev/null || true
        fi
        if command -v podman >/dev/null 2>&1; then
          podman ps -a --format 'podman\t{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.State}}' 2>/dev/null || sudo -n podman ps -a --format 'podman\t{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.State}}' 2>/dev/null || podman ps -a 2>/dev/null || sudo -n podman ps -a 2>/dev/null || true
        elif command -v sudo >/dev/null 2>&1 && sudo -n command -v podman >/dev/null 2>&1; then
          sudo -n podman ps -a --format 'podman\t{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.State}}' 2>/dev/null || sudo -n podman ps -a 2>/dev/null || true
        fi
    """.trimIndent()

    private fun containerStatsCommand(): String = """
        $HOST_TOOL_PATH
        if command -v docker >/dev/null 2>&1; then
          docker stats --no-stream --format 'docker\t{{.ID}}\t{{.Name}}\t--\t--\t--\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}' 2>/dev/null || sudo -n docker stats --no-stream --format 'docker\t{{.ID}}\t{{.Name}}\t--\t--\t--\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}' 2>/dev/null || true
        elif command -v sudo >/dev/null 2>&1 && sudo -n command -v docker >/dev/null 2>&1; then
          sudo -n docker stats --no-stream --format 'docker\t{{.ID}}\t{{.Name}}\t--\t--\t--\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}' 2>/dev/null || true
        fi
        if command -v podman >/dev/null 2>&1; then
          podman stats --no-stream --format 'podman\t{{.ID}}\t{{.Name}}\t--\t--\t--\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}' 2>/dev/null || sudo -n podman stats --no-stream --format 'podman\t{{.ID}}\t{{.Name}}\t--\t--\t--\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}' 2>/dev/null || true
        elif command -v sudo >/dev/null 2>&1 && sudo -n command -v podman >/dev/null 2>&1; then
          sudo -n podman stats --no-stream --format 'podman\t{{.ID}}\t{{.Name}}\t--\t--\t--\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}' 2>/dev/null || true
        fi
    """.trimIndent()

    private fun containerImagesCommand(): String = """
        $HOST_TOOL_PATH
        if command -v docker >/dev/null 2>&1; then
          docker images --format 'docker\t{{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}\t{{.Size}}' 2>/dev/null || sudo -n docker images --format 'docker\t{{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}\t{{.Size}}' 2>/dev/null || docker images 2>/dev/null || sudo -n docker images 2>/dev/null || true
        elif command -v sudo >/dev/null 2>&1 && sudo -n command -v docker >/dev/null 2>&1; then
          sudo -n docker images --format 'docker\t{{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}\t{{.Size}}' 2>/dev/null || sudo -n docker images 2>/dev/null || true
        fi
        if command -v podman >/dev/null 2>&1; then
          podman images --format 'podman\t{{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}\t{{.Size}}' 2>/dev/null || sudo -n podman images --format 'podman\t{{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}\t{{.Size}}' 2>/dev/null || podman images 2>/dev/null || sudo -n podman images 2>/dev/null || true
        elif command -v sudo >/dev/null 2>&1 && sudo -n command -v podman >/dev/null 2>&1; then
          sudo -n podman images --format 'podman\t{{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}\t{{.Size}}' 2>/dev/null || sudo -n podman images 2>/dev/null || true
        fi
    """.trimIndent()

    private suspend fun SshSession.execDiagnostic(command: String, timeoutSeconds: Long): String {
        val result = execute(command, timeoutSeconds)
        if (result.stdout.isNotBlank()) return result.stdout
        return result.stderr.take(240)
    }

    private fun collectionDiagnostic(failures: List<String>): String {
        if (failures.isEmpty()) return ""
        val labels = failures.map(::diagnosticLabel).distinct().take(4).joinToString(", ")
        return "Metrics partial: failed=${failures.size} optional=$labels"
    }

    internal fun collectionDiagnosticForTest(failures: List<String>): String {
        return collectionDiagnostic(failures)
    }

    private fun diagnosticLabel(failure: String): String {
        val trimmed = failure.trim()
        return when {
            trimmed.startsWith("PATH=") || trimmed.contains("vnstat --json") -> "vnStat command"
            trimmed.length > 80 -> trimmed.take(77) + "..."
            else -> trimmed
        }
    }

    private fun com.chrono.ssh.core.model.VnStatUsage?.hasAllMainRanges(): Boolean {
        return this?.day != null && week != null && month != null && year != null
    }

    private fun cpuMetrics(
        firstCpu: ProcStatCpu?,
        secondCpu: ProcStatCpu?,
        firstCores: List<ProcStatCpu>,
        secondCores: List<ProcStatCpu>,
        cpuInfo: com.chrono.ssh.core.metrics.CpuModelInfo,
        load: com.chrono.ssh.core.metrics.LoadAverage
    ): CpuMetrics {
        val usage = if (firstCpu != null && secondCpu != null) LinuxMetricParsers.cpuUsagePercent(firstCpu, secondCpu) else 0
        val user = if (firstCpu != null && secondCpu != null) LinuxMetricParsers.cpuFieldPercent(firstCpu, secondCpu) { it.user } else 0
        val system = if (firstCpu != null && secondCpu != null) LinuxMetricParsers.cpuFieldPercent(firstCpu, secondCpu) { it.system } else 0
        val nice = if (firstCpu != null && secondCpu != null) LinuxMetricParsers.cpuFieldPercent(firstCpu, secondCpu) { it.nice } else 0
        val ioWait = if (firstCpu != null && secondCpu != null) LinuxMetricParsers.cpuFieldPercent(firstCpu, secondCpu) { it.ioWait } else 0
        val steal = if (firstCpu != null && secondCpu != null) LinuxMetricParsers.cpuFieldPercent(firstCpu, secondCpu) { it.steal } else 0
        val perCore = perCoreMetrics(firstCores, secondCores)
        return CpuMetrics(
            usagePercent = usage,
            cores = perCore.size.takeIf { it > 0 } ?: cpuInfo.cores,
            model = cpuInfo.model,
            userPercent = user,
            systemPercent = system,
            nicePercent = nice,
            ioWaitPercent = ioWait,
            stealPercent = steal,
            load1 = load.load1,
            load5 = load.load5,
            load15 = load.load15,
            recentLoad = listOf(load.load15, load.load5, load.load1).map { it.coerceAtLeast(0f) },
            perCore = perCore
        )
    }

    private fun perCoreMetrics(firstCores: List<ProcStatCpu>, secondCores: List<ProcStatCpu>): List<CpuCoreMetrics> {
        val firstByIndex = firstCores.mapNotNull { core -> core.index?.let { it to core } }.toMap()
        return secondCores.mapNotNull { second ->
            val index = second.index ?: return@mapNotNull null
            val first = firstByIndex[index] ?: return@mapNotNull null
            CpuCoreMetrics(
                index = index,
                usagePercent = LinuxMetricParsers.cpuUsagePercent(first, second),
                userPercent = LinuxMetricParsers.cpuFieldPercent(first, second) { it.user },
                systemPercent = LinuxMetricParsers.cpuFieldPercent(first, second) { it.system },
                nicePercent = LinuxMetricParsers.cpuFieldPercent(first, second) { it.nice },
                ioWaitPercent = LinuxMetricParsers.cpuFieldPercent(first, second) { it.ioWait },
                stealPercent = LinuxMetricParsers.cpuFieldPercent(first, second) { it.steal }
            )
        }.sortedBy { it.index }
    }

    private fun parseAddresses(output: String): Map<String, String> {
        return output.lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                val name = parts.getOrNull(1)
                    ?.normalizedInterfaceName()
                    ?: return@mapNotNull null
                val address = parts.getOrNull(parts.indexOf("inet") + 1).orEmpty().ifBlank { "--" }
                name to address
            }
            .toMap()
    }

    private fun parseNetwork(
        first: String,
        second: String,
        addresses: Map<String, String>,
        elapsedSeconds: Float,
        vnStat: com.chrono.ssh.core.model.VnStatUsage?
    ): NetworkMetrics {
        val before = parseNetDev(first)
        val after = parseNetDev(second)
        val interfaces = after.values.mapNotNull { current ->
            val previous = before[current.name] ?: return@mapNotNull null
            val rxRate = ((current.rxBytes - previous.rxBytes).coerceAtLeast(0) / elapsedSeconds).toLong()
            val txRate = ((current.txBytes - previous.txBytes).coerceAtLeast(0) / elapsedSeconds).toLong()
            NetworkInterfaceMetric(
                name = current.name,
                address = addresses[current.name] ?: "--",
                uploadRate = txRate.bytesPerSecondLabel(),
                downloadRate = rxRate.bytesPerSecondLabel(),
                uploadTotal = current.txBytes.bytesLabel(),
                downloadTotal = current.rxBytes.bytesLabel(),
                uploadShare = if (txRate + rxRate <= 0) 0.5f else txRate.toFloat() / (txRate + rxRate).toFloat()
            )
        }
        val allInterfaces = interfaces.ifEmpty {
            after.values.map { current ->
                NetworkInterfaceMetric(
                    name = current.name,
                    address = addresses[current.name] ?: "--",
                    uploadRate = "--",
                    downloadRate = "--",
                    uploadTotal = current.txBytes.bytesLabel(),
                    downloadTotal = current.rxBytes.bytesLabel(),
                    uploadShare = 0.5f
                )
            }
        }
        val aggregateSamples = after.values.filterNot { it.name == "lo" }
            .ifEmpty { after.values.toList() }
        val aggregateRx = aggregateSamples.sumOf { current ->
            val previous = before[current.name] ?: current
            (current.rxBytes - previous.rxBytes).coerceAtLeast(0)
        }
        val aggregateTx = aggregateSamples.sumOf { current ->
            val previous = before[current.name] ?: current
            (current.txBytes - previous.txBytes).coerceAtLeast(0)
        }
        val aggregateRxRate = (aggregateRx / elapsedSeconds).toLong()
        val aggregateTxRate = (aggregateTx / elapsedSeconds).toLong()
        val aggregateRxTotal = aggregateSamples.sumOf { it.rxBytes.coerceAtLeast(0) }
        val aggregateTxTotal = aggregateSamples.sumOf { it.txBytes.coerceAtLeast(0) }
        val pickedPrimary = allInterfaces.firstOrNull { it.name.isLikelyPhysicalInterface() }
            ?: allInterfaces.firstOrNull()
            ?: NetworkInterfaceMetric("eth0", "--", "--", "--", "--", "--", 0.5f)
        val primary = pickedPrimary.copy(
            uploadRate = aggregateTxRate.bytesPerSecondLabel(),
            downloadRate = aggregateRxRate.bytesPerSecondLabel(),
            uploadTotal = aggregateTxTotal.bytesLabel(),
            downloadTotal = aggregateRxTotal.bytesLabel(),
            uploadShare = if (aggregateTxRate + aggregateRxRate <= 0) 0.5f else aggregateTxRate.toFloat() / (aggregateTxRate + aggregateRxRate).toFloat()
        )
        val uploadBars = allInterfaces.take(8).map { it.uploadRate.rateMagnitude() }
        val downloadBars = allInterfaces.take(8).map { it.downloadRate.rateMagnitude() }
        return NetworkMetrics(
            primary,
            allInterfaces,
            NetworkHistory(primary.uploadTotal, primary.downloadTotal, uploadBars, downloadBars, allInterfaces.take(8).map { it.name }, vnStat)
        )
    }

    private fun parseNetDev(output: String): Map<String, NetDevSample> {
        return output.lineSequence()
            .drop(2)
            .mapNotNull { line ->
                val parts = line.trim().split(':', limit = 2)
                val name = parts.getOrNull(0)?.normalizedInterfaceName().orEmpty()
                val values = parts.getOrNull(1)?.trim()?.split(Regex("\\s+")).orEmpty()
                val rx = values.getOrNull(0)?.toLongOrNull()
                val tx = values.getOrNull(8)?.toLongOrNull()
                if (name.isBlank() || rx == null || tx == null) null else NetDevSample(name, rx, tx)
            }
            .associateBy { it.name }
    }

    internal fun networkMetricsForTest(
        first: String,
        second: String,
        addresses: String,
        elapsedSeconds: Float = 1f
    ): NetworkMetrics {
        return parseNetwork(first, second, parseAddresses(addresses), elapsedSeconds, vnStat = null)
    }

    internal fun vnStatCommandForTest(interfaceNames: List<String>, includeExtendedFallback: Boolean): String {
        return vnStatCommand(interfaceNames, includeExtendedFallback)
    }

    internal fun vnStatDiagnosticForTest(
        interfaceNames: List<String>,
        output: String,
        parsed: com.chrono.ssh.core.model.VnStatUsage?
    ): String {
        val names = interfaceNames.filter { it.isNotBlank() }
        val interfaces = names.joinToString(",").ifBlank { "auto" }
        return "${VnStatParser.describe(output, parsed)}; interfaces=${names.size}:$interfaces"
    }

    private fun vnStatCommand(interfaceNames: List<String>, includeExtendedFallback: Boolean): String {
        val globalCommands = mutableListOf("vnstat --json --all")
        if (includeExtendedFallback) {
            globalCommands += listOf(
                "vnstat --json",
                "vnstat --json d",
                "vnstat --json w",
                "vnstat --json m",
                "vnstat --json y",
                "vnstat --json --days",
                "vnstat --json --weeks",
                "vnstat --json --months",
                "vnstat --json --years",
                "vnstat -d --json",
                "vnstat -w --json",
                "vnstat -m --json",
                "vnstat -y --json"
            )
        }
        val selectedInterfaces = interfaceNames.distinct().let { names ->
            if (includeExtendedFallback) names.take(EXTENDED_VNSTAT_INTERFACE_LIMIT) else names
        }
        val interfaceCommands = selectedInterfaces.flatMap { iface ->
            val baseCommands = mutableListOf("vnstat --json -i ${iface.shellQuote()}")
            if (includeExtendedFallback) {
                baseCommands += listOf(
                    "vnstat --json d -i ${iface.shellQuote()}",
                    "vnstat --json w -i ${iface.shellQuote()}",
                    "vnstat --json m -i ${iface.shellQuote()}",
                    "vnstat --json y -i ${iface.shellQuote()}",
                    "vnstat --json --days -i ${iface.shellQuote()}",
                    "vnstat --json --weeks -i ${iface.shellQuote()}",
                    "vnstat --json --months -i ${iface.shellQuote()}",
                    "vnstat --json --years -i ${iface.shellQuote()}",
                    "vnstat -i ${iface.shellQuote()} --json",
                    "vnstat -i ${iface.shellQuote()} --json d",
                    "vnstat -i ${iface.shellQuote()} --json w",
                    "vnstat -i ${iface.shellQuote()} --json m",
                    "vnstat -i ${iface.shellQuote()} --json y",
                    "vnstat -d --json -i ${iface.shellQuote()}",
                    "vnstat -w --json -i ${iface.shellQuote()}",
                    "vnstat -m --json -i ${iface.shellQuote()}",
                    "vnstat -y --json -i ${iface.shellQuote()}"
                )
            }
            baseCommands
        }
        val timeoutFunction = "chrono_timeout() { if command -v timeout >/dev/null 2>&1; then timeout \"\$@\"; else seconds=\"\$1\"; shift; \"\$@\" & pid=\"\$!\"; ( sleep \"\$seconds\"; kill \"\$pid\" 2>/dev/null ) & guard=\"\$!\"; wait \"\$pid\"; status=\"\$?\"; kill \"\$guard\" 2>/dev/null; return \"\$status\"; fi; }"
        val commands = (globalCommands + interfaceCommands).joinToString("; ") { "chrono_timeout 1 $it 2>/dev/null || true" }
        return "$HOST_TOOL_PATH; $timeoutFunction; if command -v vnstat >/dev/null 2>&1; then { $commands; }; else echo 'vnstat-not-found' >&2; fi"
    }

    private fun parseProcesses(output: String): ProcessSummary {
        val parsed = LinuxMetricParsers.parseProcessSummary(output)
        return ProcessSummary(parsed.total, parsed.running, parsed.topProcess, parsed.items)
    }

    private fun parseServices(allServices: String, failedServices: String): ServiceSummary {
        val parsed = LinuxMetricParsers.parseSystemdServiceSummary(allServices, failedServices)
        return ServiceSummary(total = parsed.total, failed = parsed.failed, failedItems = parsed.failedItems, items = parsed.items)
    }

    private fun parseDocker(listOutput: String, statsOutput: String, imageOutput: String): DockerSummary {
        val containers = LinuxMetricParsers.parseContainers(listOf(listOutput, statsOutput).filter { it.isNotBlank() }.joinToString("\n"))
        val images = LinuxMetricParsers.parseContainerImages(imageOutput)
        return DockerSummary(
            containers = containers.size,
            running = containers.count { it.state.equals("running", ignoreCase = true) || it.status.startsWith("Up ", ignoreCase = true) },
            items = containers,
            images = images
        )
    }

    private data class NetDevSample(
        val name: String,
        val rxBytes: Long,
        val txBytes: Long
    )

    private data class DiskRate(
        val read: String,
        val write: String,
        val readTotal: String,
        val writeTotal: String
    )

    private fun parseDiskRates(first: String, second: String, elapsedSeconds: Float): DiskRate {
        val before = LinuxMetricParsers.parseDiskStats(first)
        val after = LinuxMetricParsers.parseDiskStats(second)
        var totalReadBytes = 0L
        var totalWriteBytes = 0L
        var cumulativeReadBytes = 0L
        var cumulativeWriteBytes = 0L
        val selectedAfter = selectDiskSamplesForRate(before, after)
        val rates = selectedAfter.mapNotNull { current ->
            val previous = before[current.name] ?: return@mapNotNull null
            val readBytes = (current.sectorsRead - previous.sectorsRead).coerceAtLeast(0) * 512L
            val writeBytes = (current.sectorsWritten - previous.sectorsWritten).coerceAtLeast(0) * 512L
            totalReadBytes += readBytes
            totalWriteBytes += writeBytes
            cumulativeReadBytes += current.sectorsRead.coerceAtLeast(0) * 512L
            cumulativeWriteBytes += current.sectorsWritten.coerceAtLeast(0) * 512L
            DiskRate(
                read = (readBytes / elapsedSeconds).toLong().bytesPerSecondLabel(),
                write = (writeBytes / elapsedSeconds).toLong().bytesPerSecondLabel(),
                readTotal = (current.sectorsRead.coerceAtLeast(0) * 512L).bytesLabel(),
                writeTotal = (current.sectorsWritten.coerceAtLeast(0) * 512L).bytesLabel()
            )
        }
        val aggregate = DiskRate(
            read = (totalReadBytes / elapsedSeconds).toLong().bytesPerSecondLabel(),
            write = (totalWriteBytes / elapsedSeconds).toLong().bytesPerSecondLabel(),
            readTotal = cumulativeReadBytes.bytesLabel(),
            writeTotal = cumulativeWriteBytes.bytesLabel()
        )
        return aggregate.takeIf { !MetricFormatters.isZeroRateLabel(it.read) || !MetricFormatters.isZeroRateLabel(it.write) }
            ?: rates.firstOrNull { !MetricFormatters.isZeroRateLabel(it.read) || !MetricFormatters.isZeroRateLabel(it.write) }
            ?: rates.firstOrNull()
            ?: DiskRate("--", "--", "--", "--")
    }

    internal fun diskSampleNamesForTest(names: List<String>): List<String> {
        return selectDiskSamplesForRate(names.associateWith { name ->
            com.chrono.ssh.core.metrics.DiskStatSample(name, 0, 0)
        }).map { it.name }
    }

    internal fun diskRateLabelsForTest(first: String, second: String, elapsedSeconds: Float): List<String> {
        val rate = parseDiskRates(first, second, elapsedSeconds)
        return listOf(rate.read, rate.write, rate.readTotal, rate.writeTotal)
    }

    private fun selectDiskSamplesForRate(
        before: Map<String, com.chrono.ssh.core.metrics.DiskStatSample>,
        after: Map<String, com.chrono.ssh.core.metrics.DiskStatSample>
    ): List<com.chrono.ssh.core.metrics.DiskStatSample> {
        val preferred = selectDiskSamplesForRate(after)
        val preferredHasActivity = preferred.any { current ->
            val previous = before[current.name] ?: return@any false
            current.sectorsRead > previous.sectorsRead || current.sectorsWritten > previous.sectorsWritten
        }
        if (preferredHasActivity) return preferred
        val activeWholeDevices = after.values
            .filterNot { it.name.isLogicalAggregateDisk() }
            .filter { current ->
                val previous = before[current.name] ?: return@filter false
                current.sectorsRead > previous.sectorsRead || current.sectorsWritten > previous.sectorsWritten
            }
        return activeWholeDevices
            .takeIf { it.isNotEmpty() }
            ?.sortedWith(compareBy({ it.name.diskRank() }, { it.name }))
            ?: preferred
    }

    private fun selectDiskSamplesForRate(
        samples: Map<String, com.chrono.ssh.core.metrics.DiskStatSample>
    ): List<com.chrono.ssh.core.metrics.DiskStatSample> {
        val logical = samples.values.filter { it.name.isLogicalAggregateDisk() }
        val source = logical.takeIf { it.isNotEmpty() } ?: samples.values.toList()
        return source.sortedWith(compareBy({ it.name.diskRank() }, { it.name }))
    }

    private fun String.isLogicalAggregateDisk(): Boolean {
        val lower = lowercase()
        return lower.startsWith("dm-") || lower.startsWith("md") || lower.startsWith("bcache")
    }

    private fun String.diskRank(): Int {
        val lower = lowercase()
        return when {
            lower.startsWith("dm-") -> 0
            lower.startsWith("md") -> 1
            lower.startsWith("bcache") -> 2
            else -> 10
        }
    }

    private fun String.isLikelyPhysicalInterface(): Boolean {
        val lower = lowercase()
        return lower.startsWith("eth") || lower.startsWith("en") || lower.startsWith("wl") || lower.startsWith("ww")
    }

    private fun String.normalizedInterfaceName(): String {
        return trim().removeSuffix(":").substringBefore('@')
    }

    private fun Long.bytesPerSecondLabel(): String = MetricFormatters.bytesPerSecondLabel(this)

    private fun Long.bytesLabel(): String = MetricFormatters.bytesLabel(this)

    private fun String.rateMagnitude(): Float = MetricFormatters.rateMagnitude(this)

    private fun String.shellQuote(): String {
        return "'" + replace("'", "'\\''") + "'"
    }
}
