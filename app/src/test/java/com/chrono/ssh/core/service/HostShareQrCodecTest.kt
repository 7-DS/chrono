package com.chrono.ssh.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostShareQrCodecTest {
    @Test
    fun hostShareQrCodecGeneratesDeterministicBoundedMatrix() {
        val payload = "chronossh://host?host=ssh.example.test&user=deploy"
        val first = checkNotNull(HostShareQrCodec.encode(payload, size = 192))
        val second = checkNotNull(HostShareQrCodec.encode(payload, size = 192))

        assertEquals(192, first.width)
        assertEquals(192, first.height)
        assertEquals(matrixFingerprint(first), matrixFingerprint(second))
        assertTrue(matrixFingerprint(first) > 0)
    }

    @Test
    fun hostShareQrCodecChangesWithPayloadAndRejectsBlankInput() {
        val one = checkNotNull(HostShareQrCodec.encode("chronossh://host?host=one.example.test", size = 192))
        val two = checkNotNull(HostShareQrCodec.encode("chronossh://host?host=two.example.test", size = 192))

        assertNotEquals(matrixFingerprint(one), matrixFingerprint(two))
        assertNull(HostShareQrCodec.encode("  "))
    }

    @Test
    fun hostShareQrCodecDecodesGeneratedPixels() {
        val payload = "chronossh://host?host=ssh.example.test&user=deploy"
        val matrix = checkNotNull(HostShareQrCodec.encode(payload, size = 192))
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix.dark(x, y)) 0xff000000.toInt() else 0xffffffff.toInt()
        }

        assertEquals(payload, HostShareQrCodec.decodePixels(matrix.width, matrix.height, pixels))
        assertNull(HostShareQrCodec.decodePixels(0, matrix.height, pixels))
    }

    @Test
    fun hostShareQrCodecRejectsHostilePixelDimensions() {
        assertNull(HostShareQrCodec.decodePixels(Int.MAX_VALUE, Int.MAX_VALUE, IntArray(1)))
        assertNull(HostShareQrCodec.decodePixels(3000, 3000, IntArray(1)))
    }

    private fun matrixFingerprint(matrix: HostShareQrMatrix): Int {
        var hash = 1
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.dark(x, y)) hash = 31 * hash + (x + 1) * (y + 1)
            }
        }
        return hash
    }
}
