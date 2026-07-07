package com.chrono.ssh.core.service

import com.chrono.ssh.BuildConfig
import com.chrono.ssh.core.model.ConnectionProtocol

object BuildEditionPolicy {
    val isLite: Boolean = BuildConfig.IS_LITE

    fun supports(protocol: ConnectionProtocol): Boolean {
        return !isLite || protocol !in liteExcludedProtocols
    }

    private val liteExcludedProtocols = setOf(
        ConnectionProtocol.Mosh,
        ConnectionProtocol.EternalTerminal,
        ConnectionProtocol.Rdp,
        ConnectionProtocol.LocalProot
    )
}
