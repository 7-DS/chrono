package com.chrono.ssh.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.chrono.ssh.R

@Composable
fun ChronoOsLogo(
    osName: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val drawable = remember(osName) { osLogoDrawable(osName) }
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale
    )
}

@DrawableRes
fun osLogoDrawable(osName: String): Int {
    return osLogoDrawableOrNull(osName) ?: R.drawable.logo_distro_linux
}

@DrawableRes
fun osLogoDrawableOrNull(osName: String): Int? {
    val normalized = osName.trim().lowercase()
    return when {
        normalized.isBlank() -> null
        "windows" in normalized -> R.drawable.logo_distro_windows
        "kubuntu" in normalized -> R.drawable.logo_distro_kubuntu
        "lubuntu" in normalized -> R.drawable.logo_distro_lubuntu
        "xubuntu" in normalized -> R.drawable.logo_distro_xubuntu
        "ubuntu mate" in normalized || "ubuntu-mate" in normalized -> R.drawable.logo_distro_ubuntu_mate
        "ubuntu" in normalized -> R.drawable.logo_distro_ubuntu
        "debian" in normalized -> R.drawable.logo_distro_debian
        "alma" in normalized || "almalinux" in normalized -> R.drawable.logo_distro_almalinux
        "centos" in normalized -> R.drawable.logo_distro_centos
        "oracle" in normalized -> R.drawable.logo_distro_oracle
        "red hat" in normalized || "rhel" in normalized -> R.drawable.logo_distro_redhat
        "fedora" in normalized -> R.drawable.logo_distro_fedora
        "manjaro" in normalized -> R.drawable.logo_distro_manjaro
        "arch" in normalized -> R.drawable.logo_distro_arch
        "alpine" in normalized -> R.drawable.logo_distro_alpine
        "suse" in normalized || "opensuse" in normalized -> R.drawable.logo_distro_suse
        "mint" in normalized -> R.drawable.logo_distro_mint
        "pop" in normalized -> R.drawable.logo_distro_pop
        "kali" in normalized -> R.drawable.logo_distro_kali
        "nixos" in normalized || "nix os" in normalized -> R.drawable.logo_distro_nixos
        "gentoo" in normalized -> R.drawable.logo_distro_gentoo
        "slackware" in normalized -> R.drawable.logo_distro_slackware
        "void" in normalized -> R.drawable.logo_distro_void
        "zorin" in normalized -> R.drawable.logo_distro_zorin
        "elementary" in normalized -> R.drawable.logo_distro_elementary
        "deepin" in normalized -> R.drawable.logo_distro_deepin
        "endeavour" in normalized || "endeavor" in normalized -> R.drawable.logo_distro_endeavour
        "garuda" in normalized -> R.drawable.logo_distro_garuda
        "mageia" in normalized -> R.drawable.logo_distro_mageia
        "mx linux" in normalized || normalized == "mx" -> R.drawable.logo_distro_mx
        "openmandriva" in normalized || "open mandriva" in normalized -> R.drawable.logo_distro_openmandriva
        "parrot" in normalized -> R.drawable.logo_distro_parrot
        "raspbian" in normalized || "raspios" in normalized || "raspberry pi os" in normalized -> R.drawable.logo_distro_raspios
        "solus" in normalized -> R.drawable.logo_distro_solus
        "tails" in normalized -> R.drawable.logo_distro_tails
        "clear linux" in normalized || normalized == "clear" -> R.drawable.logo_distro_clearlinux
        "freebsd" in normalized || "free bsd" in normalized -> R.drawable.logo_distro_freebsd
        "openbsd" in normalized || "open bsd" in normalized -> R.drawable.logo_distro_openbsd
        "netbsd" in normalized || "net bsd" in normalized -> R.drawable.logo_distro_netbsd
        "linux" in normalized -> R.drawable.logo_distro_linux
        else -> null
    }
}
