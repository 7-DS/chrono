package com.chrono.ssh.ui.design

import android.graphics.Typeface
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import java.io.File

enum class HeadingFontTarget(val label: String) {
    Home("Home"),
    Connections("Connections"),
    Files("Files"),
    Vault("Vault"),
    Settings("Settings")
}

data class HeadingFontFamilies(
    val fallback: FontFamily,
    val home: FontFamily? = null,
    val connections: FontFamily? = null,
    val files: FontFamily? = null,
    val vault: FontFamily? = null,
    val settings: FontFamily? = null
) {
    fun forTarget(target: HeadingFontTarget?): FontFamily = when (target) {
        HeadingFontTarget.Home -> home
        HeadingFontTarget.Connections -> connections
        HeadingFontTarget.Files -> files
        HeadingFontTarget.Vault -> vault
        HeadingFontTarget.Settings -> settings
        null -> null
    } ?: fallback
}

val LocalHeadingFontFamilies = compositionLocalOf {
    HeadingFontFamilies(FontFamily.Default)
}

fun headingFontFamilyFromPath(path: String?): FontFamily? {
    val clean = path?.takeIf { File(it).isFile } ?: return null
    return runCatching { FontFamily(Typeface.createFromFile(clean)) }.getOrNull()
}
