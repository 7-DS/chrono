package com.chrono.ssh.ui.screens

import com.chrono.ssh.core.model.ConnectionProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostEditorScreenTest {
    @Test
    fun fileAdvancedControlsOnlyShowForRclone() {
        assertTrue(fileAdvancedControlsVisible(ConnectionProtocol.Rclone))
        assertFalse(fileAdvancedControlsVisible(ConnectionProtocol.Smb))
        assertFalse(fileAdvancedControlsVisible(ConnectionProtocol.Ssh))
    }

    @Test
    fun moshColorsRequireSupportedRange() {
        assertFalse(hostEditorMoshColorsValid(null))
        assertFalse(hostEditorMoshColorsValid(7))
        assertTrue(hostEditorMoshColorsValid(8))
        assertTrue(hostEditorMoshColorsValid(256))
        assertFalse(hostEditorMoshColorsValid(257))
    }

    @Test
    fun moshPortsAllowBlankSingleOrRange() {
        assertTrue(hostEditorMoshPortsValid(""))
        assertTrue(hostEditorMoshPortsValid("60001"))
        assertTrue(hostEditorMoshPortsValid("60000:61000"))
        assertFalse(hostEditorMoshPortsValid("61000:60000"))
        assertFalse(hostEditorMoshPortsValid("60000:"))
        assertFalse(hostEditorMoshPortsValid("65536"))
    }

    @Test
    fun portValidationRequiresTcpRange() {
        assertFalse(hostEditorPortValid(null))
        assertFalse(hostEditorPortValid(0))
        assertTrue(hostEditorPortValid(1))
        assertTrue(hostEditorPortValid(65535))
        assertFalse(hostEditorPortValid(65536))
    }
}
