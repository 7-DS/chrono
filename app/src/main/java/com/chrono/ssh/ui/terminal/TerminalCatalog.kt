package com.chrono.ssh.ui.terminal

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.FontRes
import com.chrono.ssh.R
import java.text.Normalizer

data class TerminalThemeSpec(
    val name: String,
    val foregroundHex: String,
    val backgroundHex: String,
    val cursorHex: String,
    val selectionHex: String,
    val ansiColorsHex: List<String>
)

data class TerminalFontSpec(
    val name: String,
    @FontRes val fontRes: Int
)

object TerminalCatalog {
    val themes = listOf(
        dark("Termius Dark", "#E7EAF4", "#1B1F31", "#FFFFFF", "#2F3A56", "#161A29", "#EF5B5B", "#7ACB8B", "#F3C969", "#6EA8FE", "#C792EA", "#58D5E8", "#E7EAF4", "#5B6380", "#FF7A7A", "#9EE6A8", "#FFE08A", "#8BB9FF", "#DDB6F2", "#7CE6F7", "#FFFFFF"),
        light("Termius Light", "#1F2937", "#F7F8FB", "#2563EB", "#D8E2F3", "#EEF2F7", "#C2410C", "#15803D", "#B45309", "#2563EB", "#7C3AED", "#0891B2", "#1F2937", "#6B7280", "#DC2626", "#16A34A", "#CA8A04", "#1D4ED8", "#9333EA", "#0E7490", "#111827"),
        dark("Tokyo Night", "#C0CAF5", "#0F111A", "#7AA2F7", "#283457", "#15161E", "#F7768E", "#9ECE6A", "#E0AF68", "#7AA2F7", "#BB9AF7", "#7DCFFF", "#A9B1D6", "#414868", "#F7768E", "#9ECE6A", "#E0AF68", "#7AA2F7", "#BB9AF7", "#7DCFFF", "#C0CAF5"),
        dark("Tokyo Night Storm", "#C0CAF5", "#24283B", "#7AA2F7", "#364A82", "#1D202F", "#F7768E", "#9ECE6A", "#E0AF68", "#7AA2F7", "#BB9AF7", "#7DCFFF", "#A9B1D6", "#414868", "#F7768E", "#9ECE6A", "#E0AF68", "#7AA2F7", "#BB9AF7", "#7DCFFF", "#C0CAF5"),
        light("Tokyo Night Day", "#3760BF", "#E1E2E7", "#2E7DE9", "#C5CBDD", "#E9E9ED", "#F52A65", "#587539", "#8C6C3E", "#2E7DE9", "#9854F1", "#007197", "#3760BF", "#A1A6C5", "#F52A65", "#587539", "#8C6C3E", "#2E7DE9", "#9854F1", "#007197", "#1F2335"),
        light("Catppuccin Latte", "#4C4F69", "#EFF1F5", "#1E66F5", "#CCD0DA", "#5C5F77", "#D20F39", "#40A02B", "#DF8E1D", "#1E66F5", "#8839EF", "#179299", "#4C4F69", "#6C6F85", "#D20F39", "#40A02B", "#DF8E1D", "#1E66F5", "#8839EF", "#179299", "#BCC0CC"),
        dark("Catppuccin Frappe", "#C6D0F5", "#303446", "#8CAAEE", "#51576D", "#51576D", "#E78284", "#A6D189", "#E5C890", "#8CAAEE", "#CA9EE6", "#81C8BE", "#C6D0F5", "#626880", "#E78284", "#A6D189", "#E5C890", "#8CAAEE", "#CA9EE6", "#81C8BE", "#F2D5CF"),
        dark("Catppuccin Macchiato", "#CAD3F5", "#24273A", "#8AADF4", "#494D64", "#494D64", "#ED8796", "#A6DA95", "#EED49F", "#8AADF4", "#C6A0F6", "#8BD5CA", "#CAD3F5", "#5B6078", "#ED8796", "#A6DA95", "#EED49F", "#8AADF4", "#C6A0F6", "#8BD5CA", "#F4DBD6"),
        dark("Catppuccin Mocha", "#CDD6F4", "#11111B", "#89B4FA", "#45475A", "#45475A", "#F38BA8", "#A6E3A1", "#F9E2AF", "#89B4FA", "#CBA6F7", "#94E2D5", "#CDD6F4", "#585B70", "#F38BA8", "#A6E3A1", "#F9E2AF", "#89B4FA", "#CBA6F7", "#94E2D5", "#F5E0DC"),
        dark("Rose Pine", "#E0DEF4", "#191724", "#C4A7E7", "#403D52", "#26233A", "#EB6F92", "#31748F", "#F6C177", "#9CCFD8", "#C4A7E7", "#EBBCBA", "#E0DEF4", "#6E6A86", "#EB6F92", "#31748F", "#F6C177", "#9CCFD8", "#C4A7E7", "#EBBCBA", "#F2E9E1"),
        dark("Rose Pine Moon", "#E0DEF4", "#232136", "#C4A7E7", "#44415A", "#393552", "#EB6F92", "#3E8FB0", "#F6C177", "#9CCFD8", "#C4A7E7", "#EA9A97", "#E0DEF4", "#6E6A86", "#EB6F92", "#3E8FB0", "#F6C177", "#9CCFD8", "#C4A7E7", "#EA9A97", "#F2E9E1"),
        light("Rose Pine Dawn", "#575279", "#FAF4ED", "#907AA9", "#DFDAD9", "#F2E9E1", "#B4637A", "#286983", "#EA9D34", "#56949F", "#907AA9", "#D7827E", "#575279", "#9893A5", "#B4637A", "#286983", "#EA9D34", "#56949F", "#907AA9", "#D7827E", "#575279"),
        dark("Solarized Dark", "#839496", "#002B36", "#B58900", "#073642", "#073642", "#DC322F", "#859900", "#B58900", "#268BD2", "#D33682", "#2AA198", "#EEE8D5", "#586E75", "#CB4B16", "#586E75", "#657B83", "#839496", "#6C71C4", "#93A1A1", "#FDF6E3"),
        light("Solarized Light", "#657B83", "#FDF6E3", "#268BD2", "#EEE8D5", "#073642", "#DC322F", "#859900", "#B58900", "#268BD2", "#D33682", "#2AA198", "#EEE8D5", "#002B36", "#CB4B16", "#586E75", "#657B83", "#839496", "#6C71C4", "#93A1A1", "#FDF6E3"),
        dark("Gruvbox Dark", "#EBDBB2", "#282828", "#FABD2F", "#504945", "#282828", "#CC241D", "#98971A", "#D79921", "#458588", "#B16286", "#689D6A", "#A89984", "#928374", "#FB4934", "#B8BB26", "#FABD2F", "#83A598", "#D3869B", "#8EC07C", "#EBDBB2"),
        light("Gruvbox Light", "#3C3836", "#FBF1C7", "#D79921", "#D5C4A1", "#FBF1C7", "#CC241D", "#98971A", "#D79921", "#458588", "#B16286", "#689D6A", "#7C6F64", "#928374", "#9D0006", "#79740E", "#B57614", "#076678", "#8F3F71", "#427B58", "#3C3836"),
        dark("Gruvbox Material Dark", "#D4BE98", "#1D2021", "#D8A657", "#3C3836", "#1D2021", "#EA6962", "#A9B665", "#D8A657", "#7DAEA3", "#D3869B", "#89B482", "#D4BE98", "#665C54", "#EA6962", "#A9B665", "#D8A657", "#7DAEA3", "#D3869B", "#89B482", "#D4BE98"),
        light("Gruvbox Material Light", "#654735", "#FBF1C7", "#B57614", "#D5C4A1", "#FBF1C7", "#C14A4A", "#6C782E", "#B47109", "#45707A", "#945E80", "#4C7A5D", "#654735", "#928374", "#C14A4A", "#6C782E", "#B47109", "#45707A", "#945E80", "#4C7A5D", "#3C3836"),
        dark("Dracula", "#F8F8F2", "#282A36", "#BD93F9", "#44475A", "#21222C", "#FF5555", "#50FA7B", "#F1FA8C", "#BD93F9", "#FF79C6", "#8BE9FD", "#F8F8F2", "#6272A4", "#FF6E6E", "#69FF94", "#FFFFA5", "#D6ACFF", "#FF92DF", "#A4FFFF", "#FFFFFF"),
        dark("Nord", "#D8DEE9", "#2E3440", "#88C0D0", "#434C5E", "#3B4252", "#BF616A", "#A3BE8C", "#EBCB8B", "#81A1C1", "#B48EAD", "#88C0D0", "#E5E9F0", "#4C566A", "#BF616A", "#A3BE8C", "#EBCB8B", "#81A1C1", "#B48EAD", "#8FBCBB", "#ECEFF4"),
        dark("One Dark", "#ABB2BF", "#1E2127", "#61AFEF", "#3E4451", "#1E2127", "#E06C75", "#98C379", "#E5C07B", "#61AFEF", "#C678DD", "#56B6C2", "#ABB2BF", "#5C6370", "#E06C75", "#98C379", "#E5C07B", "#61AFEF", "#C678DD", "#56B6C2", "#FFFFFF"),
        light("One Light", "#383A42", "#FAFAFA", "#4078F2", "#D7DAE0", "#FAFAFA", "#E45649", "#50A14F", "#C18401", "#4078F2", "#A626A4", "#0184BC", "#383A42", "#A0A1A7", "#E45649", "#50A14F", "#C18401", "#4078F2", "#A626A4", "#0184BC", "#090A0B"),
        dark("Monokai", "#F8F8F2", "#272822", "#F8F8F0", "#49483E", "#272822", "#F92672", "#A6E22E", "#F4BF75", "#66D9EF", "#AE81FF", "#A1EFE4", "#F8F8F2", "#75715E", "#F92672", "#A6E22E", "#F4BF75", "#66D9EF", "#AE81FF", "#A1EFE4", "#F9F8F5"),
        dark("Monokai Pro", "#FCFCFA", "#2D2A2E", "#FFD866", "#5B595C", "#403E41", "#FF6188", "#A9DC76", "#FFD866", "#FC9867", "#AB9DF2", "#78DCE8", "#FCFCFA", "#727072", "#FF6188", "#A9DC76", "#FFD866", "#FC9867", "#AB9DF2", "#78DCE8", "#FCFCFA"),
        dark("Material Ocean", "#A6ACCD", "#0F111A", "#FFCC00", "#2F3549", "#000000", "#F07178", "#C3E88D", "#FFCB6B", "#82AAFF", "#C792EA", "#89DDFF", "#EEFFFF", "#546E7A", "#F07178", "#C3E88D", "#FFCB6B", "#82AAFF", "#C792EA", "#89DDFF", "#FFFFFF"),
        dark("Material Palenight", "#A6ACCD", "#292D3E", "#FFCC00", "#3C435E", "#292D3E", "#F07178", "#C3E88D", "#FFCB6B", "#82AAFF", "#C792EA", "#89DDFF", "#EEFFFF", "#676E95", "#F07178", "#C3E88D", "#FFCB6B", "#82AAFF", "#C792EA", "#89DDFF", "#FFFFFF"),
        dark("Ayu Dark", "#B3B1AD", "#0A0E14", "#E6B450", "#253340", "#01060E", "#F07178", "#C2D94C", "#FFB454", "#59C2FF", "#D2A6FF", "#95E6CB", "#B3B1AD", "#4D5566", "#F07178", "#C2D94C", "#FFB454", "#59C2FF", "#D2A6FF", "#95E6CB", "#FFFFFF"),
        light("Ayu Light", "#5C6773", "#FAFAFA", "#FF9940", "#E6E9EF", "#FAFAFA", "#F07171", "#86B300", "#F2AE49", "#55B4D4", "#A37ACC", "#4CBF99", "#5C6773", "#ABB0B6", "#F07171", "#86B300", "#F2AE49", "#55B4D4", "#A37ACC", "#4CBF99", "#1A1F29"),
        dark("Ayu Mirage", "#CCCAC2", "#1F2430", "#FFCC66", "#34455A", "#1F2430", "#F28779", "#BAE67E", "#FFD580", "#73D0FF", "#D4BFFF", "#95E6CB", "#CCCAC2", "#707A8C", "#F28779", "#BAE67E", "#FFD580", "#73D0FF", "#D4BFFF", "#95E6CB", "#FFFFFF"),
        dark("Everforest Dark", "#D3C6AA", "#2D353B", "#A7C080", "#475258", "#343F44", "#E67E80", "#A7C080", "#DBBC7F", "#7FBBB3", "#D699B6", "#83C092", "#D3C6AA", "#859289", "#E67E80", "#A7C080", "#DBBC7F", "#7FBBB3", "#D699B6", "#83C092", "#D3C6AA"),
        light("Everforest Light", "#5C6A72", "#FDF6E3", "#8DA101", "#E6E2CC", "#FDF6E3", "#F85552", "#8DA101", "#DFA000", "#3A94C5", "#DF69BA", "#35A77C", "#5C6A72", "#A6B0A0", "#F85552", "#8DA101", "#DFA000", "#3A94C5", "#DF69BA", "#35A77C", "#1F2325"),
        dark("Kanagawa Wave", "#DCD7BA", "#1F1F28", "#7E9CD8", "#2D4F67", "#16161D", "#C34043", "#76946A", "#C0A36E", "#7E9CD8", "#957FB8", "#6A9589", "#C8C093", "#727169", "#E82424", "#98BB6C", "#E6C384", "#7FB4CA", "#938AA9", "#7AA89F", "#DCD7BA"),
        dark("Kanagawa Dragon", "#C5C9C5", "#181616", "#8BA4B0", "#282727", "#0D0C0C", "#C4746E", "#8A9A7B", "#C4B28A", "#8BA4B0", "#A292A3", "#8EA4A2", "#C5C9C5", "#625E5A", "#E46876", "#87A987", "#E6C384", "#7FB4CA", "#938AA9", "#7AA89F", "#C5C9C5"),
        light("Kanagawa Lotus", "#545464", "#F2ECBC", "#4D699B", "#D8D0A7", "#F2ECBC", "#C84053", "#6F894E", "#77713F", "#4D699B", "#B35B79", "#597B75", "#545464", "#8A8980", "#D7474B", "#6E915F", "#836F4A", "#6693BF", "#624C83", "#5E857A", "#1F1F28"),
        dark("Night Owl", "#D6DEEB", "#011627", "#80A4C2", "#1D3B53", "#011627", "#EF5350", "#22DA6E", "#C5E478", "#82AAFF", "#C792EA", "#21C7A8", "#D6DEEB", "#575656", "#EF5350", "#22DA6E", "#FFEB95", "#82AAFF", "#C792EA", "#7FDBCA", "#FFFFFF"),
        dark("GitHub Dark", "#C9D1D9", "#0D1117", "#58A6FF", "#264F78", "#0D1117", "#FF7B72", "#3FB950", "#D29922", "#58A6FF", "#BC8CFF", "#39C5CF", "#C9D1D9", "#484F58", "#FFA198", "#56D364", "#E3B341", "#79C0FF", "#D2A8FF", "#56D4DD", "#F0F6FC"),
        light("GitHub Light", "#24292F", "#FFFFFF", "#0969DA", "#D0D7DE", "#F6F8FA", "#CF222E", "#116329", "#4D2D00", "#0969DA", "#8250DF", "#1B7C83", "#24292F", "#6E7781", "#A40E26", "#1A7F37", "#633C01", "#218BFF", "#A475F9", "#3192AA", "#57606A"),
        dark("Cobalt2", "#FFFFFF", "#193549", "#FFC600", "#2F5D7C", "#000000", "#FF2600", "#3AD900", "#FFC600", "#1478DB", "#FF2C70", "#00C5C7", "#C7C7C7", "#0088FF", "#FF628C", "#3AD900", "#FFC600", "#1478DB", "#FF2C70", "#00C5C7", "#FFFFFF"),
        dark("Synthwave 84", "#F92AAD", "#262335", "#F97E72", "#4A3F73", "#262335", "#FE4450", "#72F1B8", "#F3E70F", "#03EDF9", "#FF7EDB", "#36F9F6", "#FDFDFD", "#848BBD", "#FE4450", "#72F1B8", "#F3E70F", "#03EDF9", "#FF7EDB", "#36F9F6", "#FFFFFF"),
        dark("Horizon", "#FDF0ED", "#1C1E26", "#E95678", "#2E303E", "#1C1E26", "#E95678", "#29D398", "#FAB795", "#26BBD9", "#EE64AC", "#59E1E3", "#FDF0ED", "#6C6F93", "#EC6A88", "#3FDAA4", "#FBC3A7", "#3FC6DE", "#F075B5", "#6BE4E6", "#FFFFFF"),
        dark("Fairyfloss", "#F8F8F2", "#5A5475", "#FFB8D1", "#8077A8", "#42395D", "#FF857F", "#C2FFDF", "#FFEA00", "#C5A3FF", "#FFB8D1", "#C2FFDF", "#F8F8F2", "#75507B", "#FF857F", "#C2FFDF", "#FFEA00", "#C5A3FF", "#FFB8D1", "#C2FFDF", "#FFFFFF"),
        dark("Alacritty Default", "#D8D8D8", "#181818", "#BBBBBB", "#404040", "#000000", "#D54E53", "#B9CA4A", "#E6C547", "#7AA6DA", "#C397D8", "#70C0B1", "#EAEAEA", "#666666", "#FF3334", "#9ECB4F", "#E7C547", "#7AA6DA", "#B77EE0", "#54CED6", "#FFFFFF"),
        dark("Xterm Classic", "#E5E5E5", "#000000", "#FFFFFF", "#333333", "#000000", "#CD0000", "#00CD00", "#CDCD00", "#0000EE", "#CD00CD", "#00CDCD", "#E5E5E5", "#7F7F7F", "#FF0000", "#00FF00", "#FFFF00", "#5C5CFF", "#FF00FF", "#00FFFF", "#FFFFFF"),
        dark("PaperColor Dark", "#D0D0D0", "#1C1C1C", "#D7AF5F", "#444444", "#1C1C1C", "#AF005F", "#5FAF00", "#D7AF5F", "#5FAFD7", "#8087AF", "#D7875F", "#D0D0D0", "#585858", "#5FAF5F", "#AFD700", "#AF87D7", "#FFAF00", "#FF5FAF", "#00AFAF", "#5F8787"),
        light("PaperColor Light", "#444444", "#EEEEEE", "#005FAF", "#DADADA", "#EEEEEE", "#AF0000", "#008700", "#5F8700", "#0087AF", "#878787", "#005F87", "#444444", "#BCBCBC", "#D70000", "#D70087", "#8700AF", "#D75F00", "#D75F00", "#005FAF", "#005F87"),
        dark("Snazzy", "#EFF0EB", "#282A36", "#FF5C57", "#43454F", "#282A36", "#FF5C57", "#5AF78E", "#F3F99D", "#57C7FF", "#FF6AC1", "#9AEDFE", "#F1F1F0", "#686868", "#FF5C57", "#5AF78E", "#F3F99D", "#57C7FF", "#FF6AC1", "#9AEDFE", "#EFF0EB"),
        dark("Oceanic Next", "#C0C5CE", "#1B2B34", "#6699CC", "#343D46", "#1B2B34", "#EC5F67", "#99C794", "#FAC863", "#6699CC", "#C594C5", "#5FB3B3", "#C0C5CE", "#65737E", "#EC5F67", "#99C794", "#FAC863", "#6699CC", "#C594C5", "#5FB3B3", "#D8DEE9"),
        dark("Tango Dark", "#D3D7CF", "#000000", "#D3D7CF", "#555753", "#2E3436", "#CC0000", "#4E9A06", "#C4A000", "#3465A4", "#75507B", "#06989A", "#D3D7CF", "#555753", "#EF2929", "#8AE234", "#FCE94F", "#729FCF", "#AD7FA8", "#34E2E2", "#EEEEEC"),
        light("Tango Light", "#2E3436", "#EEEEEC", "#3465A4", "#D3D7CF", "#2E3436", "#CC0000", "#4E9A06", "#C4A000", "#3465A4", "#75507B", "#06989A", "#D3D7CF", "#555753", "#EF2929", "#8AE234", "#FCE94F", "#729FCF", "#AD7FA8", "#34E2E2", "#EEEEEC"),
        dark("Tomorrow Night", "#C5C8C6", "#1D1F21", "#81A2BE", "#373B41", "#1D1F21", "#CC6666", "#B5BD68", "#F0C674", "#81A2BE", "#B294BB", "#8ABEB7", "#C5C8C6", "#666666", "#D54E53", "#B9CA4A", "#E7C547", "#7AA6DA", "#C397D8", "#70C0B1", "#EAEAEA"),
        light("Tomorrow", "#4D4D4C", "#FFFFFF", "#4271AE", "#D6D6D6", "#FFFFFF", "#C82829", "#718C00", "#EAB700", "#4271AE", "#8959A8", "#3E999F", "#4D4D4C", "#8E908C", "#C82829", "#718C00", "#EAB700", "#4271AE", "#8959A8", "#3E999F", "#1D1F21"),
        dark("Base16 Default Dark", "#D8D8D8", "#181818", "#D8D8D8", "#585858", "#181818", "#AB4642", "#A1B56C", "#F7CA88", "#7CAFC2", "#BA8BAF", "#86C1B9", "#D8D8D8", "#585858", "#AB4642", "#A1B56C", "#F7CA88", "#7CAFC2", "#BA8BAF", "#86C1B9", "#F8F8F8"),
        light("Base16 Default Light", "#383838", "#F8F8F8", "#383838", "#D8D8D8", "#181818", "#AB4642", "#A1B56C", "#F7CA88", "#7CAFC2", "#BA8BAF", "#86C1B9", "#D8D8D8", "#585858", "#AB4642", "#A1B56C", "#F7CA88", "#7CAFC2", "#BA8BAF", "#86C1B9", "#F8F8F8")
    )

    val fonts = listOf(
        TerminalFontSpec("JetBrains Mono", R.font.jetbrains_mono_regular),
        TerminalFontSpec("Fira Code", R.font.fira_code_regular),
        TerminalFontSpec("Hack", R.font.hack_regular),
        TerminalFontSpec("Geist Mono", R.font.geist_mono_regular),
        TerminalFontSpec("Atkinson Mono Nerd", R.font.atkinson_mono_nerd_regular)
    )

    fun theme(name: String): TerminalThemeSpec {
        val requested = name.catalogKey()
        return themes.firstOrNull { it.name.catalogKey() == requested } ?: themes.first()
    }

    fun nextTheme(name: String): TerminalThemeSpec {
        val index = themes.indexOfFirst { it.name == name }.takeIf { it >= 0 } ?: 0
        return themes[(index + 1) % themes.size]
    }

    fun nextFont(name: String): TerminalFontSpec {
        val requested = name.catalogKey()
        val index = fonts.indexOfFirst { it.name.catalogKey() == requested }.takeIf { it >= 0 } ?: 0
        return fonts[(index + 1) % fonts.size]
    }

    fun font(name: String): TerminalFontSpec {
        val requested = name.catalogKey()
        return fonts.firstOrNull { it.name.catalogKey() == requested } ?: fonts.first()
    }

    fun typeface(context: Context, name: String): Typeface {
        val spec = font(name)
        return runCatching {
            context.resources.getFont(spec.fontRes)
        }.getOrDefault(Typeface.MONOSPACE)
    }

    private fun dark(
        name: String,
        foreground: String,
        background: String,
        cursor: String,
        selection: String,
        black: String,
        red: String,
        green: String,
        yellow: String,
        blue: String,
        magenta: String,
        cyan: String,
        white: String,
        brightBlack: String,
        brightRed: String,
        brightGreen: String,
        brightYellow: String,
        brightBlue: String,
        brightMagenta: String,
        brightCyan: String,
        brightWhite: String
    ) = TerminalThemeSpec(
        name,
        foreground,
        background,
        cursor,
        selection,
        listOf(black, red, green, yellow, blue, magenta, cyan, white, brightBlack, brightRed, brightGreen, brightYellow, brightBlue, brightMagenta, brightCyan, brightWhite)
    )

    private fun light(
        name: String,
        foreground: String,
        background: String,
        cursor: String,
        selection: String,
        black: String,
        red: String,
        green: String,
        yellow: String,
        blue: String,
        magenta: String,
        cyan: String,
        white: String,
        brightBlack: String,
        brightRed: String,
        brightGreen: String,
        brightYellow: String,
        brightBlue: String,
        brightMagenta: String,
        brightCyan: String,
        brightWhite: String
    ) = dark(name, foreground, background, cursor, selection, black, red, green, yellow, blue, magenta, cyan, white, brightBlack, brightRed, brightGreen, brightYellow, brightBlue, brightMagenta, brightCyan, brightWhite)

    private fun String.catalogKey(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .filter { it.isLetterOrDigit() }
    }
}
