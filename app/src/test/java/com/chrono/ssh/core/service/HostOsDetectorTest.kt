package com.chrono.ssh.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HostOsDetectorTest {
    @Test
    fun parseUsesPrettyNameAndVersionIdFromOsRelease() {
        val parsed = HostOsDetector.parse(
            osRelease = """
            NAME="Ubuntu"
            VERSION_ID="24.04"
            PRETTY_NAME="Ubuntu 24.04.2 LTS"
            ID=ubuntu
            """.trimIndent(),
            uname = "Linux 6.8.0-60-generic x86_64 GNU/Linux"
        )

        assertNotNull(parsed)
        assertEquals("Ubuntu 24.04.2 LTS", parsed!!.name)
        assertEquals("24.04", parsed.version)
        assertEquals("Linux 6.8.0-60-generic x86_64 GNU/Linux", parsed.kernel)
    }

    @Test
    fun parseDecodesQuotedValuesAndFallsBackToNameVersion() {
        val parsed = HostOsDetector.parse(
            osRelease = """
            NAME="Debian GNU/Linux"
            VERSION="12 (bookworm)"
            ID=debian
            """.trimIndent(),
            uname = "Linux 6.1.0 x86_64 GNU/Linux"
        )

        assertNotNull(parsed)
        assertEquals("Debian GNU/Linux 12 (bookworm)", parsed!!.name)
        assertEquals("12 (bookworm)", parsed.version)
    }

    @Test
    fun parseFallsBackToIdAndUnameWhenNameIsMissing() {
        val parsed = HostOsDetector.parse(
            osRelease = """
            ID=almalinux
            """.trimIndent(),
            uname = "Linux 5.14.0 x86_64 GNU/Linux"
        )

        assertNotNull(parsed)
        assertEquals("AlmaLinux", parsed!!.name)
        assertEquals("Linux 5.14.0 x86_64 GNU/Linux", parsed.version)
    }

    @Test
    fun parseUsesFriendlyNamesForReferenceDistroIds() {
        assertEquals("iStoreOS", HostOsDetector.parse("ID=istoreos", "Linux")!!.name)
        assertEquals("OpenWrt", HostOsDetector.parse("ID=openwrt", "Linux")!!.name)
        assertEquals("OpenWrt", HostOsDetector.parse("ID=lede", "Linux")!!.name)
        assertEquals("Armbian", HostOsDetector.parse("ID=armbian", "Linux")!!.name)
        assertEquals("CoreELEC", HostOsDetector.parse("ID=coreelec", "Linux")!!.name)
        assertEquals("Rocky Linux", HostOsDetector.parse("ID=rocky", "Linux")!!.name)
        assertEquals("Red Hat Enterprise Linux", HostOsDetector.parse("ID=rhel", "Linux")!!.name)
        assertEquals("openSUSE Tumbleweed", HostOsDetector.parse("ID=opensuse-tumbleweed", "Linux")!!.name)
        assertEquals("FreeBSD", HostOsDetector.parse("ID=freebsd", "FreeBSD 14 amd64")!!.name)
    }

    @Test
    fun parseUsesOpenWrtAndLsbReleaseFallbackKeys() {
        val openWrt = HostOsDetector.parse(
            osRelease = """
            DISTRIB_ID='OpenWrt'
            DISTRIB_RELEASE='23.05.3'
            DISTRIB_DESCRIPTION='OpenWrt 23.05.3'
            """.trimIndent(),
            uname = "Linux OpenWrt 5.15.150 mips GNU/Linux"
        )
        val lsb = HostOsDetector.parse(
            osRelease = """
            DISTRIB_ID=Ubuntu
            DISTRIB_RELEASE=22.04
            """.trimIndent(),
            uname = "Linux 6.8.0 x86_64 GNU/Linux"
        )

        assertEquals("OpenWrt 23.05.3", openWrt!!.name)
        assertEquals("23.05.3", openWrt.version)
        assertEquals("Ubuntu 22.04", lsb!!.name)
        assertEquals("22.04", lsb.version)
    }

    @Test
    fun parseFallsBackToUnameForNonOsReleaseSystems() {
        assertEquals("FreeBSD", HostOsDetector.parse("", "FreeBSD 14.1-RELEASE amd64")!!.name)
        assertEquals("14.1-RELEASE amd64", HostOsDetector.parse("", "FreeBSD 14.1-RELEASE amd64")!!.version)
        assertEquals("OpenBSD", HostOsDetector.parse("", "OpenBSD 7.5 GENERIC.MP#82 amd64")!!.name)
        assertEquals("macOS", HostOsDetector.parse("", "Darwin 23.5.0 arm64")!!.name)
    }

    @Test
    fun parseOsReleaseIgnoresCommentsAndUnescapesQuotes() {
        val parsed = HostOsDetector.parseOsRelease(
            """
            # comment
            NAME="Rocky \"Blue\""
            ID='rocky'
            BROKEN
            """.trimIndent()
        )

        assertEquals("Rocky \"Blue\"", parsed["NAME"])
        assertEquals("rocky", parsed["ID"])
        assertEquals(2, parsed.size)
    }
}
