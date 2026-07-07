package com.chrono.ssh.core.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LinuxMetricParsersTest {
    @Test
    fun parseProcStatCpuReadsAggregateLine() {
        val parsed = LinuxMetricParsers.parseProcStatCpu("cpu  422 8 199 15000 2 0 19 55 0 0\ncpu0 1 2 3")

        assertNotNull(parsed)
        assertEquals(422L, parsed?.user)
        assertEquals(199L, parsed?.system)
        assertEquals(55L, parsed?.steal)
    }

    @Test
    fun parseProcStatCoresReadsPerCoreLinesInOrder() {
        val parsed = LinuxMetricParsers.parseProcStatCores(
            """
            cpu  422 8 199 15000 2 0 19 55 0 0
            cpu1 200 0 30 1000 4 0 0 0 0 0
            cpu0 100 1 20 900 3 0 0 1 0 0
            intr 0
            """.trimIndent()
        )

        assertEquals(2, parsed.size)
        assertEquals(0, parsed[0].index)
        assertEquals(100L, parsed[0].user)
        assertEquals(1L, parsed[0].steal)
        assertEquals(1, parsed[1].index)
        assertEquals(30L, parsed[1].system)
    }

    @Test
    fun parseMemInfoUsesAvailableMemoryAndSwap() {
        val parsed = LinuxMetricParsers.parseMemInfo(
            """
            MemTotal:       4550000 kB
            MemAvailable:   3593000 kB
            SwapTotal:      1048576 kB
            SwapFree:       1000000 kB
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(957000L, parsed?.usedKb)
        assertEquals(48576L, parsed?.swapUsedKb)
    }

    @Test
    fun parseMemInfoFallsBackToMemFreeWhenAvailableIsMissing() {
        val parsed = LinuxMetricParsers.parseMemInfo(
            """
            MemTotal:       2048000 kB
            MemFree:         512000 kB
            SwapTotal:      1024000 kB
            SwapFree:        256000 kB
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(1536000L, parsed?.usedKb)
        assertEquals(768000L, parsed?.swapUsedKb)
    }

    @Test
    fun parseUptimeFormatsDaysHoursAndMinutes() {
        assertEquals("1 d 2 h", LinuxMetricParsers.parseUptime("93600.00 120000.00"))
        assertEquals("2 h 5 m", LinuxMetricParsers.parseUptime("7500.00 120000.00"))
        assertEquals("12 m", LinuxMetricParsers.parseUptime("720.00 120000.00"))
    }

    @Test
    fun parseDfReadsPortableKilobyteOutput() {
        val parsed = LinuxMetricParsers.parseDf(
            """
            Filesystem 1024-blocks Used Available Capacity Mounted on
            /dev/sda1 52428800 10485760 41943040 20% /
            """.trimIndent()
        )

        assertNotNull(parsed)
        assertEquals(10f, parsed!!.usedGb, 0.01f)
        assertEquals(50f, parsed.totalGb, 0.01f)
    }

    @Test
    fun parseDfFilesystemsReadsPortableTypedOutput() {
        val filesystems = LinuxMetricParsers.parseDfFilesystems(
            """
            Filesystem     Type 1024-blocks Used Available Capacity Mounted on
            /dev/sda1      ext4 52428800    10485760 41943040 20% /
            tmpfs          tmpfs 1048576    1024     1047552  1% /run
            """.trimIndent()
        )

        assertEquals(1, filesystems.size)
        assertEquals("/", filesystems[0].mountPoint)
        assertEquals("ext4", filesystems[0].filesystem)
        assertEquals("/dev/sda1", filesystems[0].sourcePath)
        assertEquals(10f, filesystems[0].usedGb, 0.01f)
        assertEquals(50f, filesystems[0].totalGb, 0.01f)
    }

    @Test
    fun parseDfFilesystemsPrioritizesRealRootMountsOverVirtualMounts() {
        val filesystems = LinuxMetricParsers.parseDfFilesystems(
            """
            Filesystem     Type    1024-blocks Used Available Capacity Mounted on
            tmpfs          tmpfs   1048576     1024 1047552   1% /run
            overlay        overlay 52428800    2048 52426752  1% /var/lib/docker/overlay2/abc/merged
            /dev/sda1      ext4    52428800    10485760 41943040 20% /
            /dev/mapper/vg-home xfs 104857600  5242880 99614720 5% /home
            """.trimIndent()
        )

        assertEquals("/", filesystems[0].mountPoint)
        assertEquals("/home", filesystems[1].mountPoint)
        assertEquals(2, filesystems.size)
    }

    @Test
    fun parseLsblkFilesystemsPrioritizesRealRootMountsOverVirtualMounts() {
        val filesystems = LinuxMetricParsers.parseLsblkFilesystems(
            """
            {"path":"/dev/loop0","fstype":"squashfs","mountpoint":"/snap/core","fssize":1048576,"fsused":1048576}
            {"path":"/dev/sda1","fstype":"ext4","mountpoint":"/","fssize":107374182400,"fsused":21474836480}
            {"path":"/dev/mapper/vg-home","fstype":"xfs","mountpoint":"/home","fssize":214748364800,"fsused":10737418240}
            """.trimIndent()
        )

        assertEquals("/", filesystems[0].mountPoint)
        assertEquals("/home", filesystems[1].mountPoint)
        assertEquals("/snap/core", filesystems[2].mountPoint)
    }

    @Test
    fun parseSensorsReadsDeviceSummaries() {
        val sensors = LinuxMetricParsers.parseSensors(
            """
            coretemp-isa-0000
            Adapter: ISA adapter
            Package id 0:  +56.0°C  (high = +105.0°C, crit = +105.0°C)
            Core 0:        +45.0°C  (high = +105.0°C, crit = +105.0°C)

            nct6798-isa-0290
            Adapter: ISA adapter
            fan2:                     1764 RPM  (min =    0 RPM)
            SYSTIN:                    +34.0°C  (high = +80.0°C)
            """.trimIndent()
        )

        assertEquals(2, sensors.size)
        assertEquals("coretemp-isa-0000", sensors[0].device)
        assertEquals("Package id 0", sensors[0].label)
        assertEquals("+56.0°C  (high = +105.0°C, crit = +105.0°C)", sensors[0].value)
        assertEquals("fan2", sensors[1].label)
        assertEquals("1764 RPM  (min =    0 RPM)", sensors[1].value)
    }

    @Test
    fun parsePowerSuppliesKeepsBatteryBlocks() {
        val batteries = LinuxMetricParsers.parsePowerSupplies(
            """
            POWER_SUPPLY_NAME=battery
            POWER_SUPPLY_STATUS=Discharging
            POWER_SUPPLY_HEALTH=Good
            POWER_SUPPLY_PRESENT=1
            POWER_SUPPLY_CAPACITY=73
            POWER_SUPPLY_TECHNOLOGY=Li-poly
            POWER_SUPPLY_TIME_TO_EMPTY_NOW=5400

            POWER_SUPPLY_NAME=usb
            POWER_SUPPLY_PRESENT=0
            POWER_SUPPLY_ONLINE=0
            POWER_SUPPLY_TYPE=USB_PD
            """.trimIndent()
        )

        assertEquals(1, batteries.size)
        assertEquals("battery", batteries[0].name)
        assertEquals("Discharging", batteries[0].status)
        assertEquals(73, batteries[0].capacityPercent)
        assertEquals("Good", batteries[0].health)
        assertEquals("Li-poly", batteries[0].technology)
        assertEquals(5400L, batteries[0].timeToEmptySeconds)
        assertEquals(null, batteries[0].timeToFullSeconds)
    }

    @Test
    fun parseProcessSummaryKeepsNoPidProcessRows() {
        val summary = LinuxMetricParsers.parseProcessSummary(
            """
            R 12.0 1.0 postgres --checkpointer
            S 1.0 0.5 sshd: user@pts/0
            """.trimIndent()
        )

        assertEquals(2, summary.items.size)
        assertEquals(1, summary.running)
        assertEquals("postgres --checkpointer", summary.items[0].command)
        assertEquals(null, summary.items[0].pid)
        assertEquals(12.0f, summary.items[0].cpuPercent ?: -1f, 0.01f)
    }

    @Test
    fun parseContainersMergesStatsWhenRuntimeUsesShortAndFullIds() {
        val containers = LinuxMetricParsers.parseContainers(
            """
            docker	0e9e2ef860d2	hbbs	rustdesk/rustdesk-server:latest	Up 2 hours	running
            docker	0e9e2ef860d2f2f1c0ffee	hbbs	--	--	--	7.5%	16MiB / 512MiB	3.1%
            podman	abcdef1234567890	api	ghcr.io/example/api	Exited	exited
            podman	abcdef123456	api	--	--	--	0.0%	2MiB / 256MiB	0.8%
            """.trimIndent()
        )

        assertEquals(2, containers.size)
        assertEquals(7.5f, containers.first { it.name == "hbbs" }.cpuPercent ?: -1f, 0.01f)
        assertEquals(3.1f, containers.first { it.name == "hbbs" }.memoryPercent ?: -1f, 0.01f)
        assertEquals(0.8f, containers.first { it.name == "api" }.memoryPercent ?: -1f, 0.01f)
    }

    @Test
    fun parseSmartDisksReadsServerBoxHealthFields() {
        val disks = LinuxMetricParsers.parseSmartDisks(
            """
            {
              "device": { "name": "/dev/sda", "type": "sat" },
              "model_name": "Samsung SSD 870",
              "serial_number": "S123",
              "smart_status": { "passed": true },
              "power_on_time": { "hours": 17472 },
              "power_cycle_count": 1948,
              "temperature": { "current": 35 },
              "ata_smart_self_test_log": { "standard": { "table": [
                { "status": { "string": "Completed without error" }, "remaining_percent": 0 }
              ] } },
              "ata_smart_attributes": {
                "table": [
                  { "id": 231, "name": "SSD_Life_Left", "value": 100, "raw": { "value": 93, "string": "93" } },
                  { "id": 241, "name": "Lifetime_Writes_GiB", "value": 100, "raw": { "value": 11520, "string": "11520" } },
                  { "id": 242, "name": "Lifetime_Reads_GiB", "value": 100, "raw": { "value": 12361, "string": "12361" } },
                  { "id": 192, "name": "Unsafe_Shutdown_Count", "value": 100, "raw": { "value": 141, "string": "141" } }
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(1, disks.size)
        assertEquals("/dev/sda", disks[0].device)
        assertEquals("Samsung SSD 870", disks[0].model)
        assertEquals("S123", disks[0].serial)
        assertEquals(true, disks[0].healthy)
        assertEquals("Completed without error", disks[0].selfTestStatus)
        assertEquals(35, disks[0].temperatureCelsius)
        assertEquals(17472, disks[0].powerOnHours)
        assertEquals(1948, disks[0].powerCycleCount)
        assertEquals(93, disks[0].lifeLeftPercent)
        assertEquals(11520L, disks[0].lifetimeWritesGiB)
        assertEquals(12361L, disks[0].lifetimeReadsGiB)
        assertEquals(141, disks[0].unsafeShutdowns)
    }

    @Test
    fun parseSmartDisksMarksFailedAttributesUnhealthy() {
        val disks = LinuxMetricParsers.parseSmartDisks(
            """
            {
              "device": { "name": "/dev/sdb" },
              "ata_smart_attributes": {
                "table": [
                  { "id": 5, "name": "Reallocated_Sector_Ct", "when_failed": "FAILING_NOW", "raw": { "string": "1" } }
                ]
              }
            }
            """.trimIndent()
        )

        assertEquals(false, disks.single().healthy)
    }

    @Test
    fun parseSmartDisksIgnoresMalformedAndReadsMultipleObjects() {
        val raw = """{ "device": { "name": "/dev/sda" }, "temperature": { "current": 35 } }"""
        val disks = LinuxMetricParsers.parseSmartDisks("{not json}\n$raw\n$raw")

        assertEquals(2, disks.size)
        assertEquals("/dev/sda", disks[0].device)
        assertEquals(35, disks[1].temperatureCelsius)
    }

    @Test
    fun parseLsblkFilesystemsKeepsBtrfsRaidMembersWithSameUuid() {
        val filesystems = LinuxMetricParsers.parseLsblkFilesystems(
            """
            {
              "blockdevices": [
                {
                  "name": "nvme1n1",
                  "children": [
                    {
                      "name": "nvme1n1p1",
                      "kname": "nvme1n1p1",
                      "path": "/dev/nvme1n1p1",
                      "fstype": "btrfs",
                      "mountpoint": "/mnt/raid",
                      "fssize": "500000000000",
                      "fsused": "100000000000",
                      "fsavail": "400000000000",
                      "fsuse%": "20%",
                      "uuid": "btrfs-raid-uuid-1234-5678"
                    }
                  ]
                },
                {
                  "name": "nvme2n1",
                  "children": [
                    {
                      "name": "nvme2n1p1",
                      "kname": "nvme2n1p1",
                      "path": "/dev/nvme2n1p1",
                      "fstype": "btrfs",
                      "mountpoint": "/mnt/raid",
                      "fssize": "500000000000",
                      "fsused": "100000000000",
                      "fsavail": "400000000000",
                      "fsuse%": "20%",
                      "uuid": "btrfs-raid-uuid-1234-5678"
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, filesystems.size)
        assertEquals(listOf("/dev/nvme1n1p1", "/dev/nvme2n1p1"), filesystems.map { it.sourcePath })
        assertEquals("/mnt/raid", filesystems[0].mountPoint)
        assertEquals("btrfs", filesystems[1].filesystem)
        assertEquals(186.26f, filesystems.sumOf { it.usedGb.toDouble() }.toFloat(), 0.1f)
    }

    @Test
    fun jsonObjectParsersDoNotUseEscapedBraceRegex() {
        assertEquals(
            "/",
            LinuxMetricParsers.parseLsblkFilesystems("""{"path":"/dev/vda1","fstype":"ext4","mountpoint":"/","fssize":"1000","fsused":"250"}""")
                .single()
                .mountPoint
        )
        assertEquals(
            "lxc/100",
            LinuxMetricParsers.parsePveResources("""{"id":"lxc/100","type":"lxc","status":"running","name":"app","node":"pve"}""")
                .single()
                .id
        )
    }

    @Test
    fun parseStatFilesystemProvidesDiskCapacityFallback() {
        val disk = LinuxMetricParsers.parseStatFilesystem("1000 250 1048576")!!

        assertEquals(750f / 1024f, disk.usedGb, 0.01f)
        assertEquals(1000f / 1024f, disk.totalGb, 0.01f)
    }

    @Test
    fun parsePveResourcesReadsClusterResources() {
        val resources = LinuxMetricParsers.parsePveResources(
            """
            {
              "data": [
                {
                  "maxmem": 12884901888,
                  "type": "lxc",
                  "cpu": 0.0544631947461575,
                  "disk": 29767077888,
                  "node": "pve",
                  "vmid": 100,
                  "mem": 5389254656,
                  "status": "running",
                  "uptime": 1204757,
                  "id": "lxc/100",
                  "maxdisk": 134145380352,
                  "name": "Jellyfin"
                },
                {
                  "vmid": 101,
                  "node": "pve",
                  "uptime": 0,
                  "status": "stopped",
                  "mem": 0,
                  "id": "qemu/101",
                  "name": "ubuntu",
                  "maxdisk": 137438953472,
                  "maxmem": 6442450944,
                  "cpu": 0,
                  "type": "qemu",
                  "disk": 0
                },
                {
                  "maxmem": 4294967296,
                  "type": "qemu",
                  "cpu": 0.0516426831961466,
                  "id": "qemu/102",
                  "maxdisk": 0,
                  "name": "win",
                  "node": "pve",
                  "vmid": 102,
                  "mem": 1791827968,
                  "status": "running",
                  "uptime": 1013075
                },
                {
                  "maxcpu": 12,
                  "id": "node/pve",
                  "disk": 358415503360,
                  "maxdisk": 998011547648,
                  "node": "pve",
                  "maxmem": 29287632896,
                  "type": "node",
                  "status": "online",
                  "mem": 11522887680,
                  "cpu": 0.0451634094268353,
                  "uptime": 1204771
                },
                {
                  "id": "storage/pve/DSM",
                  "disk": 1250082226176,
                  "maxdisk": 9909187887104,
                  "storage": "DSM",
                  "node": "pve",
                  "status": "available",
                  "type": "storage"
                },
                {
                  "type": "storage",
                  "status": "available",
                  "node": "pve",
                  "maxdisk": 1967847137280,
                  "storage": "hard",
                  "id": "storage/pve/hard",
                  "disk": 620950544384
                },
                {
                  "maxdisk": 998011547648,
                  "storage": "local",
                  "disk": 358415503360,
                  "id": "storage/pve/local",
                  "status": "available",
                  "type": "storage",
                  "node": "pve"
                },
                {
                  "id": "sdn/pve/localnetwork",
                  "node": "pve",
                  "status": "ok",
                  "type": "sdn"
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(8, resources.size)
        assertEquals("Jellyfin", resources[0].name)
        assertEquals("lxc", resources[0].type)
        assertEquals(100, resources[0].vmId)
        assertEquals(5, resources[0].cpuUsagePercent)
        assertEquals(5389254656L, resources[0].memoryUsedBytes)
        assertEquals(12884901888L, resources[0].memoryMaxBytes)
        assertEquals(29767077888L, resources[0].diskUsedBytes)
        assertEquals(134145380352L, resources[0].diskMaxBytes)
        assertEquals(1204757L, resources[0].uptimeSeconds)
        assertEquals("node", resources[3].type)
        assertEquals("online", resources[3].status)
        assertEquals("DSM", resources[4].name)
        assertEquals("storage", resources[4].type)
        assertEquals("sdn", resources[7].type)
    }

    @Test
    fun parseContainersReadsDockerTableAndFormattedRows() {
        val containers = LinuxMetricParsers.parseContainers(
            """
            CONTAINER ID    STATUS                         NAMES                                              IMAGE
            0e9e2ef860d2    Up 2 hours                     hbbs                                               rustdesk/rustdesk-server:latest
            9a4df3ed340c    Exited (0) 5 minutes ago       hbbr                                               rustdesk/rustdesk-server:latest
            podman	abc123	firefly	uusec/firefly:latest	Up 12 hours	running
            docker\tdef456\tweb\tnginx:latest\tUp 4 minutes\trunning
            """.trimIndent()
        )

        assertEquals(4, containers.size)
        assertEquals("0e9e2ef860d2", containers[0].id)
        assertEquals("hbbs", containers[0].name)
        assertEquals("rustdesk/rustdesk-server:latest", containers[0].image)
        assertEquals("Up 2 hours", containers[0].status)
        assertEquals("Up 2 hours", containers[0].state)
        assertEquals("docker", containers[0].engine)
        assertEquals("Exited (0) 5 minutes ago", containers[1].status)
        assertEquals("podman", containers[2].engine)
        assertEquals("running", containers[2].state)
        assertEquals("docker", containers[3].engine)
        assertEquals("web", containers[3].name)
        assertEquals("nginx:latest", containers[3].image)
    }

    @Test
    fun parseContainersReadsDockerTemplateRowsWithLiteralTabEscapes() {
        val containers = LinuxMetricParsers.parseContainers(
            """
            docker\t0e9e2ef860d2\thbbs\trustdesk/rustdesk-server:latest\tUp 2 hours\trunning
            podman\tabc123\tfirefly\tuusec/firefly:latest\tExited\texited
            """.trimIndent()
        )

        assertEquals(2, containers.size)
        assertEquals("hbbs", containers[0].name)
        assertEquals("docker", containers[0].engine)
        assertEquals("firefly", containers[1].name)
        assertEquals("podman", containers[1].engine)
    }

    @Test
    fun parseContainersMergesStatsRows() {
        val containers = LinuxMetricParsers.parseContainers(
            """
            docker	abc123	db	postgres:16	Up 2 hours	running
            docker	abc123	db	--	--	--	3.25%	128MiB / 1GiB	12.5%
            """.trimIndent()
        )

        assertEquals(1, containers.size)
        assertEquals(3.25f, containers[0].cpuPercent)
        assertEquals(12.5f, containers[0].memoryPercent)
        assertEquals(128L * 1024L * 1024L, containers[0].memoryUsedBytes)
        assertEquals(1024L * 1024L * 1024L, containers[0].memoryMaxBytes)
    }

    @Test
    fun parseContainerImagesReadsFormattedRows() {
        val images = LinuxMetricParsers.parseContainerImages(
            """
            docker	nginx	latest	sha256:abc123	2 weeks ago	187MB
            podman\tpostgres\t16\tdef456\t3 days ago\t432MB
            """.trimIndent()
        )

        assertEquals(2, images.size)
        assertEquals("docker", images[0].engine)
        assertEquals("nginx", images[0].repository)
        assertEquals("latest", images[0].tag)
        assertEquals("sha256:abc123", images[0].id)
        assertEquals("187MB", images[0].size)
        assertEquals("podman", images[1].engine)
        assertEquals("16", images[1].tag)
    }

    @Test
    fun parseNvidiaGpusReadsSmiXmlSummary() {
        val gpus = LinuxMetricParsers.parseNvidiaGpus(
            """
            <nvidia_smi_log>
              <gpu id="00000000:01:00.0">
                <product_name>NVIDIA GeForce RTX 3080 Ti</product_name>
                <uuid>GPU-71dd</uuid>
                <fb_memory_usage>
                  <total>12288 MiB</total>
                  <used>352 MiB</used>
                </fb_memory_usage>
                <utilization>
                  <gpu_util>3 %</gpu_util>
                </utilization>
                <temperature>
                  <gpu_temp>34 C</gpu_temp>
                </temperature>
                <gpu_power_readings>
                  <power_draw>24.55 W</power_draw>
                  <current_power_limit>350.00 W</current_power_limit>
                </gpu_power_readings>
                <fan_speed>0 %</fan_speed>
                <clocks>
                  <graphics_clock>210 MHz</graphics_clock>
                </clocks>
              </gpu>
            </nvidia_smi_log>
            """.trimIndent()
        )

        assertEquals(1, gpus.size)
        assertEquals("GPU-71dd", gpus[0].id)
        assertEquals("NVIDIA GeForce RTX 3080 Ti", gpus[0].name)
        assertEquals("NVIDIA", gpus[0].vendor)
        assertEquals(3, gpus[0].utilizationPercent)
        assertEquals(352, gpus[0].memoryUsedMiB)
        assertEquals(12288, gpus[0].memoryTotalMiB)
        assertEquals(34, gpus[0].temperatureCelsius)
        assertEquals(24.55f, gpus[0].powerDrawWatts ?: 0f, 0.001f)
        assertEquals(350f, gpus[0].powerLimitWatts ?: 0f, 0.001f)
        assertEquals("0 %", gpus[0].fanSpeed)
        assertEquals(210, gpus[0].clockMhz)
    }

    @Test
    fun parseAmdGpusReadsKnownJsonVariants() {
        val gpus = LinuxMetricParsers.parseAmdGpus(
            """
            [
              {
                "name": "AMD Radeon RX 7900 XTX",
                "device_id": "0",
                "temp": 45,
                "power_draw": 120,
                "power_cap": 355,
                "memory": { "total": 24576, "used": 1024, "unit": "MB" },
                "utilization": 75,
                "fan_speed": 1200,
                "clock_speed": 2400
              },
              {
                "card_model": "AMD Radeon RX 6700 XT",
                "gpu_id": "card0",
                "temperature": "42°C",
                "power": "95 W",
                "power_cap": "230",
                "vram": { "total_memory": 12288, "used_memory": 768, "unit": "MiB" },
                "gpu_util": "60%",
                "fan_rpm": "950 RPM",
                "sclk": "1800MHz"
              }
            ]
            """.trimIndent()
        )

        assertEquals(2, gpus.size)
        assertEquals("AMD Radeon RX 7900 XTX", gpus[0].name)
        assertEquals("AMD", gpus[0].vendor)
        assertEquals(75, gpus[0].utilizationPercent)
        assertEquals(1024, gpus[0].memoryUsedMiB)
        assertEquals(24576, gpus[0].memoryTotalMiB)
        assertEquals(45, gpus[0].temperatureCelsius)
        assertEquals(120f, gpus[0].powerDrawWatts ?: 0f, 0.001f)
        assertEquals(355f, gpus[0].powerLimitWatts ?: 0f, 0.001f)
        assertEquals("1200 RPM", gpus[0].fanSpeed)
        assertEquals(2400, gpus[0].clockMhz)
        assertEquals("card0", gpus[1].id)
        assertEquals(60, gpus[1].utilizationPercent)
        assertEquals(768, gpus[1].memoryUsedMiB)
        assertEquals(12288, gpus[1].memoryTotalMiB)
        assertEquals(42, gpus[1].temperatureCelsius)
        assertEquals(1800, gpus[1].clockMhz)
    }

    @Test
    fun parseDiskStatsIgnoresPartitionsAndKeepsWholeDevices() {
        val parsed = LinuxMetricParsers.parseDiskStats(
            """
               8       0 sda 157698 0 6801984 1234 82727 0 6512904 4321 0 0 0 0 0 0
               8       1 sda1 1200 0 3000 10 50 0 1000 20 0 0 0 0 0 0
             259       0 nvme0n1 100 0 2048 1 200 0 4096 2 0 0 0 0 0 0
             259       1 nvme0n1p1 100 0 2048 1 200 0 4096 2 0 0 0 0 0 0
            """.trimIndent()
        )

        assertEquals(setOf("sda", "nvme0n1"), parsed.keys)
        assertEquals(6801984L, parsed["sda"]?.sectorsRead)
        assertEquals(4096L, parsed["nvme0n1"]?.sectorsWritten)
    }

    @Test
    fun parseDiskStatsFallsBackToPartitionsWhenNoWholeDiskCountersExist() {
        val parsed = LinuxMetricParsers.parseDiskStats(
            """
               8       1 sda1 1200 0 3000 10 50 0 1000 20 0 0 0 0 0 0
             259       1 nvme0n1p1 100 0 2048 1 200 0 4096 2 0 0 0 0 0 0
             252       1 vda1 700 0 8192 3 400 0 16384 4 0 0 0 0 0 0
            """.trimIndent()
        )

        assertEquals(setOf("sda1", "nvme0n1p1", "vda1"), parsed.keys)
        assertEquals(3000L, parsed["sda1"]?.sectorsRead)
        assertEquals(16384L, parsed["vda1"]?.sectorsWritten)
    }

    @Test
    fun parseDiskStatsKeepsDeviceMapperAndMdDevices() {
        val parsed = LinuxMetricParsers.parseDiskStats(
            """
             253       0 dm-0 100 0 4096 1 200 0 8192 2 0 0 0 0 0 0
               9       0 md0 500 0 16384 3 700 0 32768 4 0 0 0 0 0 0
               7       0 loop0 1 0 1 0 1 0 1 0 0 0 0 0 0 0
            """.trimIndent()
        )

        assertEquals(setOf("dm-0", "md0"), parsed.keys)
        assertEquals(4096L, parsed["dm-0"]?.sectorsRead)
        assertEquals(32768L, parsed["md0"]?.sectorsWritten)
    }

    @Test
    fun parseDiskStatsKeepsCommonVirtualWholeDisks() {
        val parsed = LinuxMetricParsers.parseDiskStats(
            """
             252       0 vda 100 0 2048 1 200 0 4096 2 0 0 0 0 0 0
             252       1 vda1 100 0 2048 1 200 0 4096 2 0 0 0 0 0 0
             202       0 xvda 300 0 8192 3 400 0 16384 4 0 0 0 0 0 0
             202       1 xvda1 300 0 8192 3 400 0 16384 4 0 0 0 0 0 0
             179       0 mmcblk0 500 0 32768 5 600 0 65536 6 0 0 0 0 0 0
             179       1 mmcblk0p1 500 0 32768 5 600 0 65536 6 0 0 0 0 0 0
            """.trimIndent()
        )

        assertEquals(setOf("vda", "xvda", "mmcblk0"), parsed.keys)
        assertEquals(2048L, parsed["vda"]?.sectorsRead)
        assertEquals(16384L, parsed["xvda"]?.sectorsWritten)
        assertEquals(32768L, parsed["mmcblk0"]?.sectorsRead)
    }

    @Test
    fun parseDiskStatsKeepsCloudAndNetworkBlockDevices() {
        val parsed = LinuxMetricParsers.parseDiskStats(
            """
              43       0 nbd0 100 0 2048 1 200 0 4096 2 0 0 0 0 0 0
              43       1 nbd0p1 100 0 2048 1 200 0 4096 2 0 0 0 0 0 0
             251       0 rbd0 300 0 8192 3 400 0 16384 4 0 0 0 0 0 0
             251       1 rbd0p1 300 0 8192 3 400 0 16384 4 0 0 0 0 0 0
               3       0 hda 500 0 32768 5 600 0 65536 6 0 0 0 0 0 0
               3       1 hda1 500 0 32768 5 600 0 65536 6 0 0 0 0 0 0
            """.trimIndent()
        )

        assertEquals(setOf("nbd0", "rbd0", "hda"), parsed.keys)
        assertEquals(2048L, parsed["nbd0"]?.sectorsRead)
        assertEquals(16384L, parsed["rbd0"]?.sectorsWritten)
        assertEquals(32768L, parsed["hda"]?.sectorsRead)
    }

    @Test
    fun parseDiskStatsKeepsAdditionalHostedBlockDevices() {
        val parsed = LinuxMetricParsers.parseDiskStats(
            """
             230       0 zd0 100 0 2048 1 200 0 4096 2 0 0 0 0 0 0
             230       1 zd0p1 100 0 2048 1 200 0 4096 2 0 0 0 0 0 0
              94       0 dasda 300 0 8192 3 400 0 16384 4 0 0 0 0 0 0
              94       1 dasda1 300 0 8192 3 400 0 16384 4 0 0 0 0 0 0
             252       0 bcache0 500 0 32768 5 600 0 65536 6 0 0 0 0 0 0
             252       1 bcache0p1 500 0 32768 5 600 0 65536 6 0 0 0 0 0 0
            """.trimIndent()
        )

        assertEquals(setOf("zd0", "dasda", "bcache0"), parsed.keys)
        assertEquals(2048L, parsed["zd0"]?.sectorsRead)
        assertEquals(16384L, parsed["dasda"]?.sectorsWritten)
        assertEquals(32768L, parsed["bcache0"]?.sectorsRead)
    }

    @Test
    fun cpuDeltaCalculatesUsageAndFields() {
        val first = ProcStatCpu(100, 0, 50, 850, 0, 0, 0, 0)
        val second = ProcStatCpu(160, 0, 90, 950, 0, 0, 0, 0)

        assertEquals(50, LinuxMetricParsers.cpuUsagePercent(first, second))
        assertEquals(30, LinuxMetricParsers.cpuFieldPercent(first, second) { it.user })
        assertEquals(20, LinuxMetricParsers.cpuFieldPercent(first, second) { it.system })
    }

    @Test
    fun cpuDeltaCalculatesPerCoreUsage() {
        val before = LinuxMetricParsers.parseProcStatCores(
            """
            cpu0 100 0 50 850 0 0 0 0
            cpu1 50 0 50 900 0 0 0 0
            """.trimIndent()
        )
        val after = LinuxMetricParsers.parseProcStatCores(
            """
            cpu0 160 0 90 950 0 0 0 0
            cpu1 55 0 55 990 0 0 0 0
            """.trimIndent()
        )

        assertEquals(50, LinuxMetricParsers.cpuUsagePercent(before[0], after[0]))
        assertEquals(10, LinuxMetricParsers.cpuUsagePercent(before[1], after[1]))
    }

    @Test
    fun parseLoadAverageAndCpuInfo() {
        val load = LinuxMetricParsers.parseLoadAverage("0.12 0.34 0.56 1/100 42")
        val cpu = LinuxMetricParsers.parseCpuModel(
            """
            processor   : 0
            model name  : AMD EPYC
            processor   : 1
            model name  : AMD EPYC
            """.trimIndent()
        )

        assertEquals(0.12f, load.load1, 0.001f)
        assertEquals(0.56f, load.load15, 0.001f)
        assertEquals("AMD EPYC", cpu.model)
        assertEquals(2, cpu.cores)
    }

    @Test
    fun parseCpuModelCountsIndentedProcessorRows() {
        val cpu = LinuxMetricParsers.parseCpuModel(
            """
              processor   : 0
              model name  : Ampere Altra
              processor   : 1
              processor   : 2
            """.trimIndent()
        )

        assertEquals("Ampere Altra", cpu.model)
        assertEquals(3, cpu.cores)
    }

    @Test
    fun parseDisplayLabelsAreBoundedAndControlCleaned() {
        val cpu = LinuxMetricParsers.parseCpuModel("processor : 0\nmodel name : AMD\u0000${"x".repeat(120)}")
        val process = LinuxMetricParsers.parseProcessSummary("R 10.0 1.0 postgres\u0000${"y".repeat(120)}")

        assertEquals(80, cpu.model.length)
        assertEquals(false, cpu.model.contains('\u0000'))
        assertEquals(80, process.topProcess.length)
        assertEquals(false, process.topProcess.contains('\u0000'))
    }

    @Test
    fun parseProcessSummaryUsesSortedCommandColumn() {
        val parsed = LinuxMetricParsers.parseProcessSummary(
            """
            42 R 31.4 1.2 nginx
            81 S 10.0 0.4 postgres
            7 S 0.0 0.1 sshd
            """.trimIndent()
        )

        assertEquals(3, parsed.total)
        assertEquals(1, parsed.running)
        assertEquals("nginx", parsed.topProcess)
        assertEquals("nginx", parsed.items.first().command)
        assertEquals(42, parsed.items.first().pid)
        assertEquals(31.4f, parsed.items.first().cpuPercent ?: -1f, 0.001f)
        assertEquals(1.2f, parsed.items.first().memoryPercent ?: -1f, 0.001f)
    }

    @Test
    fun parseProcessSummaryReadsExtendedTabRows() {
        val parsed = LinuxMetricParsers.parseProcessSummary(
            "42\t1\tpostgres\tR\t31.4\t1.2\t65536\t262144\t3600\tpostgres -D /var/lib/postgresql"
        )

        val process = parsed.items.first()
        assertEquals(42, process.pid)
        assertEquals(1, process.parentPid)
        assertEquals("postgres", process.user)
        assertEquals("R", process.state)
        assertEquals(31.4f, process.cpuPercent ?: -1f, 0.001f)
        assertEquals(1.2f, process.memoryPercent ?: -1f, 0.001f)
        assertEquals(65536L, process.rssKb)
        assertEquals(262144L, process.virtualSizeKb)
        assertEquals("3600", process.elapsed)
        assertEquals("postgres -D /var/lib/postgresql", process.command)
    }

    @Test
    fun parseProcessSummaryReadsAuxFallbackRows() {
        val parsed = LinuxMetricParsers.parseProcessSummary(
            "42\t\troot\tS\t7.5\t0.3\t8192\t65536\t\t/usr/sbin/sshd -D"
        )

        val process = parsed.items.first()
        assertEquals(42, process.pid)
        assertEquals("root", process.user)
        assertEquals("S", process.state)
        assertEquals(7.5f, process.cpuPercent ?: -1f, 0.001f)
        assertEquals(8192L, process.rssKb)
        assertEquals(65536L, process.virtualSizeKb)
        assertEquals("/usr/sbin/sshd -D", process.command)
    }

    @Test
    fun parseProcessSummaryKeepsFullDetailPageBatch() {
        val parsed = LinuxMetricParsers.parseProcessSummary(
            (1..70).joinToString("\n") { pid -> "$pid S 0.1 0.2 process-$pid --flag value" }
        )

        assertEquals(70, parsed.total)
        assertEquals(64, parsed.items.size)
        assertEquals("process-1 --flag value", parsed.items.first().command)
    }

    @Test
    fun parseProcessSummaryKeepsLegacyRowsWithoutPid() {
        val parsed = LinuxMetricParsers.parseProcessSummary("R 31.4 1.2 nginx")

        assertEquals("nginx", parsed.items.first().command)
        assertEquals(null, parsed.items.first().pid)
        assertEquals(31.4f, parsed.items.first().cpuPercent ?: -1f, 0.001f)
    }

    @Test
    fun parseProcessSummaryHandlesFallbackStatCommandOutput() {
        val parsed = LinuxMetricParsers.parseProcessSummary(
            """
            R sshd
            S postgres
            S nginx
            """.trimIndent()
        )

        assertEquals(3, parsed.total)
        assertEquals(1, parsed.running)
        assertEquals("sshd", parsed.topProcess)
        assertEquals("sshd", parsed.items.first().command)
        assertEquals(null, parsed.items.first().cpuPercent)
    }

    @Test
    fun parseSystemdServicesKeepsTotalSeparateFromFailures() {
        val parsed = LinuxMetricParsers.parseSystemdServiceSummary(
            allServicesOutput = """
            UNIT           LOAD   ACTIVE SUB     DESCRIPTION
            ssh.service loaded active running OpenSSH server
            nginx.service loaded active running nginx
            broken.service loaded failed failed broken service
            0 loaded units listed.
            Legend: LOAD reflects whether the unit definition was properly loaded.
            """.trimIndent(),
            failedServicesOutput = """
            UNIT           LOAD   ACTIVE SUB    DESCRIPTION
            broken.service loaded failed failed broken service
            0 loaded units listed.
            """.trimIndent()
        )

        assertEquals(3, parsed.total)
        assertEquals(1, parsed.failed)
        assertEquals(3, parsed.items.size)
        assertEquals("ssh.service", parsed.items.first().unit)
        assertEquals("broken.service", parsed.failedItems.single().unit)
        assertEquals("broken service", parsed.failedItems.single().description)
    }
}
