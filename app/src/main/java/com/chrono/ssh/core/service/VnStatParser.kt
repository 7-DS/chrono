package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.VnStatPeriodUsage
import com.chrono.ssh.core.model.VnStatUsage

object VnStatParser {
    fun describe(json: String, parsed: VnStatUsage?): String {
        if (json.isBlank()) return "vnStat: no output from vnstat command"
        val firstSignal = json.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("{") }
            .orEmpty()
            .sanitizeDiagnostic()
        if (!json.contains("\"interfaces\"")) {
            return "vnStat: no usable JSON; first output=${firstSignal.ifBlank { json.take(120).sanitizeDiagnostic() }}"
        }
        val keys = listOf("day", "days", "month", "months", "year", "years", "fiveminute", "hour", "hours", "total")
            .filter { json.contains("\"$it\"") }
            .ifEmpty { listOf("no traffic period keys") }
        val ranges = listOfNotNull(
            parsed?.day?.let { "day=${it.totalBytes}" },
            parsed?.week?.let { "week=${it.totalBytes}" },
            parsed?.month?.let { "month=${it.totalBytes}" },
            parsed?.year?.let { "year=${it.totalBytes}" }
        ).ifEmpty { listOf("no parsed totals") }
        val status = if (parsed == null) {
            "installed or callable, but no day/week/month/year usage totals parsed (${ranges.joinToString(", ")})"
        } else {
            "${ranges.joinToString(", ")}"
        }
        val hint = firstSignal.takeIf { parsed == null && it.isNotBlank() }?.let { ", first output=$it" }.orEmpty()
        return "vnStat: output=${json.length} bytes, keys=${keys.joinToString("/")}, $status$hint"
    }

    fun parse(json: String): VnStatUsage? {
        val fragments = usableJsonFragments(json) ?: return null
        val days = parsePeriods(fragments, "day", "days")
        val explicitWeeks = parsePeriods(fragments, "week", "weeks")
        return VnStatUsage(
            day = days.lastOrNull(),
            week = explicitWeeks.lastOrNull() ?: aggregateWeek(days),
            month = parsePeriod(fragments, "month", "months", "This month"),
            year = parsePeriod(fragments, "year", "years", "This year")
        ).takeIf { it.day != null || it.week != null || it.month != null || it.year != null }
    }

    private fun usableJsonFragments(output: String): List<String>? {
        if (output.isBlank()) return null
        val fragments = extractJsonObjects(output)
            .filter { it.contains("\"interfaces\"") || it.contains("\"traffic\"") }
        return fragments.takeIf { it.isNotEmpty() }
            ?: output.takeIf { it.contains("\"interfaces\"") }?.let(::listOf)
    }

    private fun extractJsonObjects(output: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        output.forEachIndexed { index, char ->
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                !inString && char == '{' -> {
                    if (depth == 0) start = index
                    depth += 1
                }
                !inString && char == '}' -> {
                    depth -= 1
                    if (depth == 0 && start >= 0) {
                        objects += output.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun parsePeriod(fragments: List<String>, key: String, legacyKey: String, fallbackLabel: String): VnStatPeriodUsage? {
        return parsePeriods(fragments, key, legacyKey, fallbackLabel).lastOrNull()
    }

    private fun parsePeriods(
        fragments: List<String>,
        key: String,
        legacyKey: String? = null,
        fallbackLabel: String = ""
    ): List<VnStatPeriodUsage> {
        return fragments
            .flatMap { fragment -> parsePeriodEntries(fragment, key, legacyKey, fallbackLabel) }
            .distinctBy { "${it.source}|${it.usage.label}|${it.usage.receivedBytes}|${it.usage.transmittedBytes}" }
            .map { it.usage }
            .groupBy { it.label }
            .map { (label, items) ->
                val rx = items.sumOf { it.receivedBytes }
                val tx = items.sumOf { it.transmittedBytes }
                VnStatPeriodUsage(
                    receivedBytes = rx,
                    transmittedBytes = tx,
                    totalBytes = rx + tx,
                    label = label
                )
            }
    }

    private fun parsePeriodEntries(json: String, key: String, legacyKey: String? = null, fallbackLabel: String = ""): List<ParsedPeriodEntry> {
        val rootPlainCountersAreKib = json.plainVnStatCountersAreKib()
        val interfaceObjects = extractArrays(json, "interfaces").flatMap(::extractObjects)
        if (interfaceObjects.isEmpty()) {
            return parsePeriods(json, source = "global", key = key, legacyKey = legacyKey, fallbackLabel = fallbackLabel, plainCountersAreKib = rootPlainCountersAreKib)
        }
        return interfaceObjects.flatMapIndexed { index, interfaceJson ->
            parsePeriods(
                json = interfaceJson,
                source = interfaceJson.interfaceName() ?: "interface-$index",
                key = key,
                legacyKey = legacyKey,
                fallbackLabel = fallbackLabel,
                plainCountersAreKib = rootPlainCountersAreKib
            )
        }
    }

    private fun parsePeriods(
        json: String,
        source: String,
        key: String,
        legacyKey: String? = null,
        fallbackLabel: String = "",
        plainCountersAreKib: Boolean = json.plainVnStatCountersAreKib()
    ): List<ParsedPeriodEntry> {
        val keys = listOfNotNull(key, legacyKey).distinct()
        val objects = keys.flatMap { periodKey ->
            extractArrays(json, periodKey).flatMap(::extractObjects) + extractObjectsForKey(json, periodKey)
        }.distinct()
        if (objects.isEmpty()) return emptyList()
        val periods = objects.mapNotNull { item ->
            val rx = item.trafficNumber("rx", plainCountersAreKib)
            val tx = item.trafficNumber("tx", plainCountersAreKib)
            if (rx <= 0L && tx <= 0L) {
                null
            } else {
                val label = item.dateLabel() ?: fallbackLabel.ifBlank { key.replaceFirstChar { it.titlecase() } }
                ParsedPeriodEntry(
                    source = source,
                    usage = VnStatPeriodUsage(
                        receivedBytes = rx,
                        transmittedBytes = tx,
                        totalBytes = rx + tx,
                        label = label
                    )
                )
            }
        }
        return periods
            .groupBy { "${it.source}|${it.usage.label}" }
            .map { (_, items) ->
                val first = items.first()
                val rx = items.sumOf { it.usage.receivedBytes }
                val tx = items.sumOf { it.usage.transmittedBytes }
                ParsedPeriodEntry(
                    source = first.source,
                    usage = VnStatPeriodUsage(
                        receivedBytes = rx,
                        transmittedBytes = tx,
                        totalBytes = rx + tx,
                        label = first.usage.label
                    )
                )
            }
    }

    private fun aggregateWeek(days: List<VnStatPeriodUsage>): VnStatPeriodUsage? {
        val recentDays = days.takeLast(7)
        if (recentDays.isEmpty()) return null
        val rx = recentDays.sumOf { it.receivedBytes }
        val tx = recentDays.sumOf { it.transmittedBytes }
        if (rx <= 0L && tx <= 0L) return null
        val first = recentDays.first().label
        val last = recentDays.last().label
        return VnStatPeriodUsage(
            receivedBytes = rx,
            transmittedBytes = tx,
            totalBytes = rx + tx,
            label = if (first == last) last else "$first - $last"
        )
    }

    private fun extractArrays(json: String, key: String): List<String> {
        val arrays = mutableListOf<String>()
        val startKey = "\"$key\""
        var searchFrom = 0
        while (searchFrom < json.length) {
            val keyIndex = json.indexOf(startKey, searchFrom)
            if (keyIndex < 0) break
            val colon = json.indexOf(':', keyIndex + startKey.length)
            if (colon < 0) break
            val valueStart = json.indexOfFirstNonWhitespace(colon + 1)
            if (valueStart >= 0 && json[valueStart] == '[') {
                extractArrayAt(json, valueStart)?.let { arrays += it }
            }
            searchFrom = keyIndex + startKey.length
        }
        return arrays
    }

    private fun extractArrayAt(json: String, start: Int): String? {
        var depth = 0
        for (index in start until json.length) {
            when (json[index]) {
                '[' -> depth += 1
                ']' -> {
                    depth -= 1
                    if (depth == 0) return json.substring(start + 1, index)
                }
            }
        }
        return null
    }

    private fun String.indexOfFirstNonWhitespace(startIndex: Int): Int {
        for (index in startIndex until length) {
            if (!this[index].isWhitespace()) return index
        }
        return -1
    }

    private fun extractObjects(array: String): List<String> {
        val objects = mutableListOf<String>()
        var depth = 0
        var start = -1
        array.forEachIndexed { index, char ->
            when (char) {
                '{' -> {
                    if (depth == 0) start = index
                    depth += 1
                }
                '}' -> {
                    depth -= 1
                    if (depth == 0 && start >= 0) {
                        objects += array.substring(start, index + 1)
                        start = -1
                    }
                }
            }
        }
        return objects
    }

    private fun String.plainVnStatCountersAreKib(): Boolean {
        // vnStat's JSON output reports rx/tx traffic in BYTES. Per vnstat(1): "all traffic
        // values in the output are in bytes unless otherwise indicated by the name of the
        // key". This holds for vnStat v2 (jsonversion 1 and 2) which is what `vnstat --json`
        // emits today. The previous heuristic flagged plain counters as KiB whenever a
        // version field was present, so on real v2 hosts every rx/tx was multiplied by 1024,
        // inflating all day/week/month/year totals by 1024x. Plain scalar counters are
        // therefore treated as bytes; KiB/MiB/GiB scaling is still applied only when the JSON
        // explicitly names the unit (rxk/txk remainders or nested {unit,value} objects,
        // handled in trafficNumber before this fallback is reached).
        return false
    }

    private fun String.trafficNumber(key: String, plainCountersAreKib: Boolean): Long {
        val base = firstNumber(key)
        val remainder = firstNumber("${key}k")
        if (contains("\"${key}k\"")) return base * 1024L + remainder
        val explicitBytes = firstNumber("${key}_bytes")
        if (explicitBytes > 0L) return explicitBytes
        val nestedBytes = nestedTrafficNumber(key, "bytes", "byte", "b")
        if (nestedBytes > 0L) return nestedBytes
        val nestedKib = nestedTrafficNumber(key, "kib", "KiB", "kibibytes", "kibibyte")
        if (nestedKib > 0L) return nestedKib * 1024L
        val nestedMib = nestedTrafficNumber(key, "mib", "MiB", "mebibytes", "mebibyte")
        if (nestedMib > 0L) return nestedMib * 1024L * 1024L
        val nestedGib = nestedTrafficNumber(key, "gib", "GiB", "gibibytes", "gibibyte")
        if (nestedGib > 0L) return nestedGib * 1024L * 1024L * 1024L
        val valueWithUnit = nestedValueWithUnit(key)
        if (valueWithUnit > 0L) return valueWithUnit
        return if (plainCountersAreKib) base * 1024L else base
    }

    private fun String.firstNumber(key: String): Long {
        val match = Regex("\"$key\"\\s*:\\s*\"?(\\d+(?:\\.\\d+)?)\"?").find(this) ?: return 0L
        return match.groupValues.getOrNull(1)?.toDoubleOrNull()?.toLong() ?: 0L
    }

    private fun String.nestedTrafficNumber(key: String, vararg nestedKeys: String): Long {
        val objectStart = nestedObjectStart(key) ?: return 0L
        val objectText = extractObjectAt(this, objectStart) ?: return 0L
        return nestedKeys.firstNotNullOfOrNull { nestedKey ->
            objectText.firstNumber(nestedKey).takeIf { it > 0L }
        } ?: 0L
    }

    private fun String.nestedValueWithUnit(key: String): Long {
        val objectStart = nestedObjectStart(key) ?: return 0L
        val objectText = extractObjectAt(this, objectStart) ?: return 0L
        val value = objectText.firstNumber("value").takeIf { it > 0L }
            ?: objectText.firstNumber("amount").takeIf { it > 0L }
            ?: return 0L
        val unit = Regex("\"(?:unit|units)\"\\s*:\\s*\"([^\"]+)\"")
            .find(objectText)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
            .orEmpty()
        val multiplier = when (unit) {
            "b", "byte", "bytes" -> 1L
            "k", "kb", "kib", "kbyte", "kbytes", "kibibyte", "kibibytes" -> 1024L
            "m", "mb", "mib", "mbyte", "mbytes", "mebibyte", "mebibytes" -> 1024L * 1024L
            "g", "gb", "gib", "gbyte", "gbytes", "gibibyte", "gibibytes" -> 1024L * 1024L * 1024L
            "t", "tb", "tib", "tbyte", "tbytes", "tebibyte", "tebibytes" -> 1024L * 1024L * 1024L * 1024L
            else -> return 0L
        }
        return value * multiplier
    }

    private fun String.nestedObjectStart(key: String): Int? {
        val keyIndex = indexOf("\"$key\"")
        if (keyIndex < 0) return null
        val colon = indexOf(':', keyIndex)
        if (colon < 0) return null
        val valueStart = indexOfFirstNonWhitespace(colon + 1)
        return valueStart.takeIf { it >= 0 && this[it] == '{' }
    }

    private fun String.dateLabel(): String? {
        val year = Regex("\"year\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.getOrNull(1)
        val month = Regex("\"month\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.getOrNull(1)
        val day = Regex("\"day\"\\s*:\\s*(\\d+)").find(this)?.groupValues?.getOrNull(1)
        return listOfNotNull(year, month?.padStart(2, '0'), day?.padStart(2, '0')).joinToString("-").ifBlank { null }
    }

    private fun String.interfaceName(): String? {
        return Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractObjectAt(json: String, start: Int): String? {
        var depth = 0
        for (index in start until json.length) {
            when (json[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return json.substring(start, index + 1)
                }
            }
        }
        return null
    }

    private fun extractObjectsForKey(json: String, key: String): List<String> {
        val objects = mutableListOf<String>()
        val startKey = "\"$key\""
        var searchFrom = 0
        while (searchFrom < json.length) {
            val keyIndex = json.indexOf(startKey, searchFrom)
            if (keyIndex < 0) break
            val colon = json.indexOf(':', keyIndex + startKey.length)
            if (colon < 0) break
            val valueStart = json.indexOfFirstNonWhitespace(colon + 1)
            if (valueStart >= 0 && json[valueStart] == '{') {
                extractObjectAt(json, valueStart)?.let { objects += it }
            }
            searchFrom = keyIndex + startKey.length
        }
        return objects
    }

    private fun String.sanitizeDiagnostic(): String {
        return replace(Regex("\\s+"), " ").trim().take(160)
    }

    private data class ParsedPeriodEntry(
        val source: String,
        val usage: VnStatPeriodUsage
    )
}
