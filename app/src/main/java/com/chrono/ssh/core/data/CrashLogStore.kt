package com.chrono.ssh.core.data

import android.content.Context
import com.chrono.ssh.core.model.CrashLogEntry
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.UUID
import kotlin.system.exitProcess

object CrashLogStore {
    private const val FILE_NAME = "crash_logs.v1.txt"
    private const val MAX_LOGS = 40
    private const val MAX_STACK_CHARS = 12000
    private const val MAX_THREAD_CHARS = 120
    private const val MAX_CLASS_CHARS = 200
    private const val MAX_MESSAGE_CHARS = 500

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CrashLoggingExceptionHandler) return
        Thread.setDefaultUncaughtExceptionHandler(CrashLoggingExceptionHandler(appContext, previous))
    }

    fun append(context: Context, throwable: Throwable, threadName: String = Thread.currentThread().name) {
        val entry = CrashLogEntry(
            id = UUID.randomUUID().toString(),
            atEpochMillis = System.currentTimeMillis(),
            threadName = cleanField(threadName, MAX_THREAD_CHARS),
            throwableClass = cleanField(throwable::class.java.name, MAX_CLASS_CHARS),
            message = cleanField(throwable.message.orEmpty(), MAX_MESSAGE_CHARS),
            stackTrace = cleanField(throwable.stackTraceText(), MAX_STACK_CHARS)
        )
        val next = (load(context) + entry).takeLast(MAX_LOGS)
        file(context).writeText(next.joinToString("\n", transform = CrashLogCodec::encode))
    }

    fun appendMessage(context: Context, message: String) {
        append(context, IllegalStateException(message), Thread.currentThread().name)
    }

    fun load(context: Context): List<CrashLogEntry> {
        val file = file(context)
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull(CrashLogCodec::decode)
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    internal fun cleanField(value: String, maxChars: Int): String {
        return value.map { if (it.isISOControl() && it != '\n' && it != '\r' && it != '\t') ' ' else it }
            .joinToString("")
            .take(maxChars)
    }

    private fun Throwable.stackTraceText(): String {
        val writer = StringWriter()
        printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private class CrashLoggingExceptionHandler(
        private val context: Context,
        private val previous: Thread.UncaughtExceptionHandler?
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            runCatching { append(context, throwable, thread.name) }
            previous?.uncaughtException(thread, throwable) ?: exitProcess(10)
        }
    }
}

internal object CrashLogCodec {
    fun encode(entry: CrashLogEntry): String {
        val normalized = normalize(entry)
        return listOf(
            normalized.id,
            normalized.atEpochMillis.toString(),
            normalized.threadName,
            normalized.throwableClass,
            normalized.message,
            normalized.stackTrace
        ).joinToString("|", transform = ::escape)
    }

    fun decode(line: String): CrashLogEntry? {
        val fields = splitEscaped(line, '|')
        if (fields.size != 6) return null
        return normalize(CrashLogEntry(
            id = fields[0],
            atEpochMillis = fields[1].toLongOrNull() ?: 0L,
            threadName = fields[2],
            throwableClass = fields[3],
            message = fields[4],
            stackTrace = fields[5]
        ))
    }

    private fun normalize(entry: CrashLogEntry): CrashLogEntry {
        return entry.copy(
            threadName = CrashLogStore.cleanField(entry.threadName, 120),
            throwableClass = CrashLogStore.cleanField(entry.throwableClass, 200),
            message = CrashLogStore.cleanField(entry.message, 500),
            stackTrace = CrashLogStore.cleanField(entry.stackTrace, 12000)
        )
    }

    private fun escape(value: String): String {
        return buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '|' -> append("\\p")
                    else -> append(char)
                }
            }
        }
    }

    private fun unescape(value: String): String {
        val result = StringBuilder()
        var escaped = false
        value.forEach { char ->
            if (escaped) {
                result.append(
                    when (char) {
                        'n' -> '\n'
                        'r' -> '\r'
                        'p' -> '|'
                        '\\' -> '\\'
                        else -> char
                    }
                )
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else {
                result.append(char)
            }
        }
        if (escaped) result.append('\\')
        return result.toString()
    }

    private fun splitEscaped(value: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        value.forEach { char ->
            when {
                escaped -> {
                    current.append('\\')
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == separator -> {
                    parts.add(unescape(current.toString()))
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        if (escaped) current.append('\\')
        parts.add(unescape(current.toString()))
        return parts
    }
}
