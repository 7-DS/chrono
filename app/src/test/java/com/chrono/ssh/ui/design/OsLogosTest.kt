package com.chrono.ssh.ui.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OsLogosTest {
    @Test
    fun termiusTerminalLogosCoverImportedOperatingSystems() {
        listOf(
            "AlmaLinux",
            "Alpine Linux",
            "Amazon Linux",
            "Android",
            "Arch Linux",
            "CentOS",
            "Debian",
            "Fedora",
            "FreeBSD",
            "Gentoo",
            "Linux",
            "macOS",
            "Mageia",
            "MikroTik RouterOS",
            "NetBSD",
            "OpenBSD",
            "Red Hat Enterprise Linux",
            "Rocky Linux",
            "SUSE",
            "Ubuntu",
            "Windows"
        ).forEach { osName ->
            assertNotNull(osName, termiusTerminalOsLogoDrawableOrNull(osName))
        }
    }

    @Test
    fun termiusTerminalLogosStayTerminalSpecific() {
        assertEquals(termiusTerminalOsLogoDrawableOrNull("Darwin"), termiusTerminalOsLogoDrawableOrNull("Apple"))
        assertNull(termiusTerminalOsLogoDrawableOrNull("Plan 9"))
    }
}
