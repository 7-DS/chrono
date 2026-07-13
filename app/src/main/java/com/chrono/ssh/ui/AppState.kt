package com.chrono.ssh.ui

import android.content.Context
import com.chrono.ssh.ui.design.InputFontChoice

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

/**
 * Lightweight persistence for the configurable input/text-box font.
 *
 * The main [com.chrono.ssh.core.model.AppSettings] data class and its file serializer live in
 * modules outside this feature's editable scope, so this preference is stored on its own in a
 * small SharedPreferences entry. Value is one of [InputFontChoice.id]; defaults to Nunito.
 */
object InputFontPreference {
    private const val PREFS_NAME = "chrono_ui_prefs"
    private const val KEY_INPUT_FONT = "input_font_id"

    val DEFAULT_ID: String = InputFontChoice.DEFAULT.id

    fun load(context: Context): String {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_INPUT_FONT, null)
        // Sanitize against the known catalog so a stale/invalid id falls back to the default.
        return InputFontChoice.fromId(stored).id
    }

    fun save(context: Context, id: String) {
        val sanitized = InputFontChoice.fromId(id).id
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_INPUT_FONT, sanitized)
            .apply()
    }
}
