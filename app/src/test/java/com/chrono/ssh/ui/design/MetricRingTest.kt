package com.chrono.ssh.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricRingTest {
    @Test
    fun metricRingSweepClampsPercent() {
        assertEquals(0f, metricRingSweep(-5f), 0.001f)
        assertEquals(90f, metricRingSweep(25f), 0.001f)
        assertEquals(360f, metricRingSweep(140f), 0.001f)
    }
}
