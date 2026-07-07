package com.chrono.ssh.core.service

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

class HostShareQrMatrix(
    val width: Int,
    val height: Int,
    private val rows: List<BooleanArray>
) {
    fun dark(x: Int, y: Int): Boolean = rows.getOrNull(y)?.getOrNull(x) == true
}

object HostShareQrCodec {
    private const val MaxPixels = 4_000_000L

    fun encode(payload: String, size: Int = 256): HostShareQrMatrix? {
        val clean = payload.trim()
        if (clean.isBlank()) return null
        val px = size.coerceIn(128, 768)
        return runCatching {
            val matrix = QRCodeWriter().encode(clean, BarcodeFormat.QR_CODE, px, px)
            HostShareQrMatrix(
                width = matrix.width,
                height = matrix.height,
                rows = List(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix[x, y] } }
            )
        }.getOrNull()
    }

    fun decodePixels(width: Int, height: Int, pixels: IntArray): String? {
        val requiredPixels = width.toLong() * height.toLong()
        if (width <= 0 || height <= 0 || requiredPixels <= 0L || requiredPixels > MaxPixels || pixels.size.toLong() < requiredPixels) {
            return null
        }
        val source = RGBLuminanceSource(width, height, pixels)
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            null
        } catch (_: RuntimeException) {
            null
        } finally {
            reader.reset()
        }
    }
}
