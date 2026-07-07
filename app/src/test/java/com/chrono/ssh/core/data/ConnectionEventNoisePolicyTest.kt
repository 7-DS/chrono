package com.chrono.ssh.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionEventNoisePolicyTest {
    @Test
    fun noisyConnectionEventsAreBucketedByKind() {
        assertEquals("metrics-collecting", "Collecting Linux metrics over SSH.".noisyConnectionEventBucket())
        assertEquals("metrics-updated", "Metrics updated from SSH exec.".noisyConnectionEventBucket())
        assertEquals("vnstat-fast-skip", "vnStat: skipped during fast refresh".noisyConnectionEventBucket())
        assertEquals("tcp-probe-failed", "TCP probe failed for 1.2.3.4:22 from port 40000".noisyConnectionEventBucket())
        assertEquals("metrics-connection-failed", "Metrics connection failed: timeout".noisyConnectionEventBucket())
        assertNull("Terminal connected with Password credential.".noisyConnectionEventBucket())
    }
}
