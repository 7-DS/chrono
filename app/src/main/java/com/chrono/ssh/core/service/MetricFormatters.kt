package com.chrono.ssh.core.service

import java.util.Locale

object MetricFormatters {
    private const val BYTES_PER_KIB = 1024L
    private const val BYTES_PER_MIB = BYTES_PER_KIB * 1024L
    private const val BYTES_PER_GIB = BYTES_PER_MIB * 1024L

    fun bytesLabel(bytes: Long): String {
        if (bytes <= 0L) return "0.00 B"
        val safeBytes = bytes.coerceAtLeast(0L).toDouble()
        if (safeBytes < 1024.0) return "%.2f B".formatUs(safeBytes)
        val kiloBytes = safeBytes / 1024.0
        if (kiloBytes < 1024.0) return "%.2f K".formatUs(kiloBytes)
        val megaBytes = kiloBytes / 1024.0
        val (scaled, unit) = when {
            megaBytes >= 1_048_576.0 -> megaBytes / 1_048_576.0 to "T"
            megaBytes >= 1024.0 -> megaBytes / 1024.0 to "G"
            else -> megaBytes to "M"
        }
        return "%.2f %s".formatUs(scaled, unit)
    }

    fun megaBytesLabel(megaBytes: Float): String {
        return bytesLabel((megaBytes.coerceAtLeast(0f) * BYTES_PER_MIB).toLong())
    }

    fun gigaBytesLabel(gigaBytes: Float): String {
        return bytesLabel((gigaBytes.coerceAtLeast(0f) * BYTES_PER_GIB).toLong())
    }

    fun megaBytesPairLabel(usedMegaBytes: Float, totalMegaBytes: Float): String {
        return pairLabel(
            usedBytes = (usedMegaBytes.coerceAtLeast(0f) * BYTES_PER_MIB).toLong(),
            totalBytes = (totalMegaBytes.coerceAtLeast(0f) * BYTES_PER_MIB).toLong()
        )
    }

    fun gigaBytesPairLabel(usedGigaBytes: Float, totalGigaBytes: Float): String {
        return pairLabel(
            usedBytes = (usedGigaBytes.coerceAtLeast(0f) * BYTES_PER_GIB).toLong(),
            totalBytes = (totalGigaBytes.coerceAtLeast(0f) * BYTES_PER_GIB).toLong()
        )
    }

    fun bytesPerSecondLabel(bytesPerSecond: Long): String = "${bytesLabel(bytesPerSecond)}/s"

    fun amountAndUnitLabel(label: String): Pair<String, String> {
        if (label.isBlank() || label == "--") return "--" to ""
        val number = label.substringBefore(' ').toDoubleOrNull() ?: return label.take(4) to ""
        val bytes = number * when {
            label.contains("TB", ignoreCase = true) || label.contains(" T", ignoreCase = true) -> BYTES_PER_GIB.toDouble() * 1024.0
            label.contains("GB", ignoreCase = true) || label.contains(" G", ignoreCase = true) -> BYTES_PER_GIB.toDouble()
            label.contains("MB", ignoreCase = true) || label.contains(" M", ignoreCase = true) -> BYTES_PER_MIB.toDouble()
            label.contains("KB", ignoreCase = true) || label.contains(" K", ignoreCase = true) -> BYTES_PER_KIB.toDouble()
            else -> 1.0
        }
        val perSecond = label.contains("/s")
        val (scaled, unit) = when {
            bytes >= BYTES_PER_GIB.toDouble() * 1024.0 -> bytes / (BYTES_PER_GIB.toDouble() * 1024.0) to "T"
            bytes >= BYTES_PER_GIB.toDouble() -> bytes / BYTES_PER_GIB.toDouble() to "G"
            bytes >= BYTES_PER_MIB.toDouble() -> bytes / BYTES_PER_MIB.toDouble() to "M"
            bytes >= BYTES_PER_KIB.toDouble() -> bytes / BYTES_PER_KIB.toDouble() to "K"
            else -> bytes to "B"
        }
        return "%.2f".formatUs(scaled.coerceAtLeast(0.0)) to if (perSecond) "$unit/s" else unit
    }

    fun compactAmountAndUnitLabel(label: String): Pair<String, String> {
        val (amount, unit) = amountAndUnitLabel(label)
        if (amount == "--" || unit.isBlank()) return amount to unit
        val numeric = amount.toDoubleOrNull() ?: return amount to unit
        return "%.2f".formatUs(numeric) to unit
    }

    fun isZeroRateLabel(label: String): Boolean {
        val parsed = parseByteLabel(label) ?: return false
        return parsed.perSecond && parsed.amount <= 0.0
    }

    fun isTinyRateLabel(label: String): Boolean {
        val parsed = parseByteLabel(label) ?: return false
        if (!parsed.perSecond) return false
        val bytesPerSecond = parsed.amount * parsed.unitMultiplier
        return bytesPerSecond < BYTES_PER_KIB.toDouble()
    }

    fun homeUsageOrRateLabel(rateLabel: String, totalLabel: String): String {
        return rateLabel
            .takeUnless { isTinyRateLabel(it) }
            ?: totalLabel.takeUnless { it == "--" }.orEmpty().ifBlank { "--" }
    }

    fun homeTotalOrRateLabel(rateLabel: String, totalLabel: String): String {
        return totalLabel
            .takeUnless { it == "--" || it == "0.00 B" }
            ?: rateLabel.takeUnless { isTinyRateLabel(it) }.orEmpty().ifBlank { "--" }
    }

    fun rateMagnitude(label: String): Float {
        if (label == "--") return 0f
        val number = label.substringBefore(' ').toFloatOrNull() ?: return 0f
        val multiplier = when {
            label.contains("TB", ignoreCase = true) -> 1024f * 1024f
            label.contains(" T", ignoreCase = true) -> 1024f * 1024f
            label.contains("GB", ignoreCase = true) -> 1024f
            label.contains(" G", ignoreCase = true) -> 1024f
            label.contains("MB", ignoreCase = true) -> 1f
            label.contains(" M", ignoreCase = true) -> 1f
            label.contains("KB", ignoreCase = true) -> 1f / 1024f
            label.contains(" K", ignoreCase = true) -> 1f / 1024f
            label.contains(" B", ignoreCase = true) || label.endsWith("B", ignoreCase = true) -> 1f / (1024f * 1024f)
            else -> 0f
        }
        return (number * multiplier).coerceIn(0f, 1f)
    }

    private fun pairLabel(usedBytes: Long, totalBytes: Long): String {
        val unitBytes = when {
            maxOf(usedBytes, totalBytes) >= BYTES_PER_GIB * 1024L -> BYTES_PER_GIB * 1024L to "T"
            maxOf(usedBytes, totalBytes) >= BYTES_PER_GIB -> BYTES_PER_GIB to "G"
            maxOf(usedBytes, totalBytes) >= BYTES_PER_MIB -> BYTES_PER_MIB to "M"
            maxOf(usedBytes, totalBytes) >= BYTES_PER_KIB -> BYTES_PER_KIB to "K"
            else -> 1L to "B"
        }
        val divisor = unitBytes.first.toDouble()
        return "%.2f / %.2f %s".formatUs(usedBytes / divisor, totalBytes / divisor, unitBytes.second)
    }

    private fun parseByteLabel(label: String): ParsedByteLabel? {
        val normalized = label.trim()
        if (normalized.isBlank() || normalized == "--") return null
        val match = Regex("""^([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?B?|[KMGT])(?:/s)?$""", RegexOption.IGNORE_CASE)
            .find(normalized)
            ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase(Locale.US).ifBlank { "B" }.removeSuffix("B")
        val multiplier = when (unit) {
            "T" -> BYTES_PER_GIB.toDouble() * 1024.0
            "G" -> BYTES_PER_GIB.toDouble()
            "M" -> BYTES_PER_MIB.toDouble()
            "K" -> BYTES_PER_KIB.toDouble()
            else -> 1.0
        }
        return ParsedByteLabel(
            amount = amount,
            unitMultiplier = multiplier,
            perSecond = normalized.endsWith("/s", ignoreCase = true)
        )
    }

    private data class ParsedByteLabel(
        val amount: Double,
        val unitMultiplier: Double,
        val perSecond: Boolean
    )

    private fun String.formatUs(vararg args: Any): String = format(Locale.US, *args)
}
