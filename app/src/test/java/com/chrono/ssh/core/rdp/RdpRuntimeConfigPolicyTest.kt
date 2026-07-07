package com.chrono.ssh.core.rdp

import org.junit.Test

class RdpRuntimeConfigPolicyTest {
    @Test
    fun validConfigPasses() {
        RdpRuntimeConfigPolicy.validate(width = 1600, height = 900, colorDepth = 16, port = 3389)
    }

    @Test(expected = IllegalArgumentException::class)
    fun undersizedWidthIsRejected() {
        RdpRuntimeConfigPolicy.validate(width = 320, height = 900, colorDepth = 16, port = 3389)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidColorDepthIsRejected() {
        RdpRuntimeConfigPolicy.validate(width = 1600, height = 900, colorDepth = 12, port = 3389)
    }

    @Test(expected = IllegalArgumentException::class)
    fun overflowPortIsRejectedBeforeUnsignedCast() {
        RdpRuntimeConfigPolicy.validate(width = 1600, height = 900, colorDepth = 16, port = 65_536)
    }
}
