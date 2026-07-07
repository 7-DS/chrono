package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.ServerProfile
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReachabilityResult(
    val reachable: Boolean,
    val latencyMs: Int?,
    val message: String
)

class TcpReachabilityProbe(
    private val timeoutMs: Int = 1500
) {
    suspend fun probe(profile: ServerProfile): ReachabilityResult = withContext(Dispatchers.IO) {
        var connected = false
        var errorMessage: String? = null
        val elapsed = measureTimeMillis {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(profile.host, profile.port), timeoutMs)
                    connected = true
                }
            } catch (error: Exception) {
                errorMessage = error.message ?: error::class.java.simpleName
            }
        }.toInt()

        if (connected) {
            ReachabilityResult(
                reachable = true,
                latencyMs = elapsed.coerceAtLeast(1),
                message = "TCP reachable at ${profile.host}:${profile.port} in ${elapsed.coerceAtLeast(1)} ms"
            )
        } else {
            ReachabilityResult(
                reachable = false,
                latencyMs = null,
                message = "TCP probe failed for ${profile.host}:${profile.port}: ${errorMessage ?: "unreachable"}"
            )
        }
    }
}
