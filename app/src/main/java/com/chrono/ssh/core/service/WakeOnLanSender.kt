package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.WakeOnLanConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class WakeOnLanSender {
    suspend fun wake(config: WakeOnLanConfig, port: Int = 9, repeats: Int = 3) = withContext(Dispatchers.IO) {
        val packetBytes = magicPacket(config)
        val address = InetAddress.getByName(config.broadcastAddress)
        DatagramSocket().use { socket ->
            socket.broadcast = true
            repeat(repeats.coerceAtLeast(1)) { index ->
                socket.send(DatagramPacket(packetBytes, packetBytes.size, address, port))
                if (index + 1 < repeats) delay(500)
            }
        }
    }

    fun magicPacket(config: WakeOnLanConfig): ByteArray {
        val mac = parseSixBytes(config.macAddress)
        val secureOn = config.secureOnPassword?.let(::parseSixBytes)
        return ByteArray(6 + 16 * mac.size + (secureOn?.size ?: 0)).also { packet ->
            repeat(6) { packet[it] = 0xFF.toByte() }
            var offset = 6
            repeat(16) {
                mac.copyInto(packet, offset)
                offset += mac.size
            }
            secureOn?.copyInto(packet, offset)
        }
    }

    private fun parseSixBytes(value: String): ByteArray {
        val hex = value.filter { it != ':' && it != '-' }
        require(hex.length == 12) { "Expected 6 hex bytes." }
        return ByteArray(6) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }
}
