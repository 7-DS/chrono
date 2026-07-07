package com.chrono.ssh.ui.screens

import com.chrono.ssh.core.model.NetworkHistory
import com.chrono.ssh.core.model.NetworkInterfaceMetric
import com.chrono.ssh.core.model.NetworkMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

class InterfacesScreenTest {
    @Test
    fun interfaceMetricsFallbackToCollectedPrimaryInterface() {
        val primary = NetworkInterfaceMetric("eth0", "10.0.0.5/24", "0.00 B/s", "0.00 B/s", "10.00 MB", "20.00 MB", 0.5f)
        val network = NetworkMetrics(primary, emptyList(), history())

        assertEquals(listOf(primary), interfaceMetricsForDisplay(network))
    }

    @Test
    fun interfaceMetricsHideUnavailablePrimaryInterface() {
        val primary = NetworkInterfaceMetric("eth0", "--", "--", "--", "--", "--", 0.5f)
        val network = NetworkMetrics(primary, emptyList(), history())

        assertEquals(emptyList<NetworkInterfaceMetric>(), interfaceMetricsForDisplay(network))
    }

    @Test
    fun interfaceMetricsPreferCollectedInterfaceList() {
        val primary = NetworkInterfaceMetric("eth0", "10.0.0.5/24", "0.00 B/s", "0.00 B/s", "10.00 MB", "20.00 MB", 0.5f)
        val secondary = NetworkInterfaceMetric("wlan0", "10.0.1.5/24", "1.00 KB/s", "2.00 KB/s", "1.00 MB", "2.00 MB", 0.25f)
        val network = NetworkMetrics(primary, listOf(secondary), history())

        assertEquals(listOf(secondary), interfaceMetricsForDisplay(network))
    }

    private fun history() = NetworkHistory(
        uploadLabel = "--",
        downloadLabel = "--",
        uploadBars = emptyList(),
        downloadBars = emptyList(),
        labels = emptyList()
    )
}
