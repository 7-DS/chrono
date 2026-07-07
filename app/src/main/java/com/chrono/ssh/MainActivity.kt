package com.chrono.ssh

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.chrono.ssh.core.data.CrashLogStore
import com.chrono.ssh.ui.ChronoSSHApp

class MainActivity : ComponentActivity() {
    private val hostShareLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogStore.install(applicationContext)
        enableEdgeToEdge()
        hostShareLink.value = hostShareLinkFrom(intent)
        setContent {
            ChronoSSHApp(
                inboundHostShareLink = hostShareLink.value,
                onInboundHostShareLinkConsumed = { payload ->
                    if (hostShareLink.value == payload) hostShareLink.value = null
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        hostShareLink.value = hostShareLinkFrom(intent)
    }

    private fun hostShareLinkFrom(intent: Intent?): String? {
        return intent?.dataString?.takeIf { it.startsWith("chronossh://host", ignoreCase = true) }
    }
}
