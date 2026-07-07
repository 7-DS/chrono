package com.chrono.ssh.ui

sealed interface AppSurface {
    data object Home : AppSurface
    data object Connections : AppSurface
    data object Terminal : AppSurface
    data object Files : AppSurface
    data object Vault : AppSurface
    data object Settings : AppSurface
    data object Uptime : AppSurface
    data class ServerDetail(val serverId: String) : AppSurface
    data class ServerActivity(val serverId: String) : AppSurface
    data class Interfaces(val serverId: String) : AppSurface
    data class PortForward(val serverId: String) : AppSurface
    data class VncViewer(val serverId: String) : AppSurface
    data class RdpViewer(val serverId: String) : AppSurface
    data class SftpBrowser(val serverId: String?, val workspaceKey: String?) : AppSurface
    data class HostEditor(val serverId: String?) : AppSurface
}
