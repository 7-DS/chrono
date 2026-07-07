package com.chrono.ssh.core.vnc

import org.junit.Assert.assertEquals
import org.junit.Test

class VncRuntimeConfigPolicyTest {
    @Test
    fun fpsIsBoundedForRuntimeLoop() {
        assertEquals(5, VncRuntimeConfigPolicy.normalizeFps(0))
        assertEquals(5, VncRuntimeConfigPolicy.normalizeFps(-10))
        assertEquals(30, VncRuntimeConfigPolicy.normalizeFps(30))
        assertEquals(60, VncRuntimeConfigPolicy.normalizeFps(120))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroPortIsRejected() {
        VncRuntimeConfigPolicy.validatePort(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun overflowPortIsRejected() {
        VncRuntimeConfigPolicy.validatePort(65_536)
    }
}
