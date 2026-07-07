package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.WakeOnLanConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakeOnLanSenderTest {
    @Test
    fun buildsMagicPacketWithOptionalSecureOnPassword() {
        val packet = WakeOnLanSender().magicPacket(
            WakeOnLanConfig(
                macAddress = "01:23:45:67:89:ab",
                secureOnPassword = "AA-BB-CC-DD-EE-FF"
            )
        )

        assertEquals(108, packet.size)
        assertArrayEquals(ByteArray(6) { 0xFF.toByte() }, packet.copyOfRange(0, 6))
        assertArrayEquals(byteArrayOf(1, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte()), packet.copyOfRange(6, 12))
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte()), packet.copyOfRange(102, 108))
    }

    @Test
    fun normalizesWakeOnLanFields() {
        assertEquals("01:23:45:67:89:AB", WakeOnLanPolicy.normalizeMac("01-23-45-67-89-ab"))
        assertEquals("255.255.255.255", WakeOnLanPolicy.normalizeBroadcast(""))
        assertNull(WakeOnLanPolicy.errorFor("01:23:45:67:89:ab", "", null))
        assertEquals("Wake-on-LAN MAC must be 6 hex bytes.", WakeOnLanPolicy.errorFor("bad", "", null))
    }
}
