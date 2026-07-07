package com.chrono.ssh.core.data

import com.chrono.ssh.core.model.CrashLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrashLogStoreTest {
    @Test
    fun crashLogCodecRoundTripsPipesAndNewlines() {
        val entry = CrashLogEntry(
            id = "crash|1",
            atEpochMillis = 1234L,
            threadName = "main",
            throwableClass = "java.lang.IllegalStateException",
            message = "terminal|open\nfailed",
            stackTrace = "line1\nline2|tail"
        )

        val decoded = CrashLogCodec.decode(CrashLogCodec.encode(entry))

        assertEquals(entry, decoded)
    }

    @Test
    fun crashLogCodecRejectsCorruptExtraFields() {
        assertNull(CrashLogCodec.decode("id|1|main|java.lang.Error|message|stack|truncated"))
    }

    @Test
    fun crashLogCodecNormalizesOversizedFields() {
        val entry = CrashLogEntry(
            id = "crash-1",
            atEpochMillis = 1234L,
            threadName = "worker\u0000${"x".repeat(200)}",
            throwableClass = "com.example.${"Type".repeat(80)}",
            message = "bad\u0000${"m".repeat(800)}",
            stackTrace = "line\u0000${"s".repeat(13_000)}"
        )

        val decoded = checkNotNull(CrashLogCodec.decode(CrashLogCodec.encode(entry)))

        assertEquals(120, decoded.threadName.length)
        assertEquals(200, decoded.throwableClass.length)
        assertEquals(500, decoded.message.length)
        assertEquals(12_000, decoded.stackTrace.length)
        assertEquals(false, decoded.message.contains('\u0000'))
    }
}
