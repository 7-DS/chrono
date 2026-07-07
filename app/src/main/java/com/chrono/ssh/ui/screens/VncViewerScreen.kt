package com.chrono.ssh.ui.screens

import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrono.ssh.core.model.ConnectionEventLevel
import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.PortForwardType
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.service.SshTransport
import com.chrono.ssh.core.vnc.ColorDepth
import com.chrono.ssh.core.vnc.VncClient
import com.chrono.ssh.core.vnc.VncConfig
import com.chrono.ssh.ui.design.CircleIconButton
import com.chrono.ssh.ui.design.DeckCard
import com.chrono.ssh.ui.design.DeckColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VncViewerScreen(
    server: ServerProfile,
    credentials: List<Credential>,
    sshTransport: SshTransport,
    onBack: () -> Unit,
    onConnectionEvent: (ConnectionEventLevel, String) -> Unit,
    onLoadCredentialPayload: suspend (Credential) -> String
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val touchSlop = ViewConfiguration.get(LocalView.current.context).scaledTouchSlop
    var client by remember(server.id) { mutableStateOf<VncClient?>(null) }
    var frame by remember(server.id) { mutableStateOf<Bitmap?>(null) }
    var status by remember(server.id) { mutableStateOf("Ready") }
    var remoteText by remember(server.id) { mutableStateOf("") }
    var bandwidthHint by remember(server.id) { mutableStateOf<String?>(null) }
    var cursorBitmap by remember(server.id) { mutableStateOf<Bitmap?>(null) }
    var cursorHotspot by remember(server.id) { mutableStateOf(0 to 0) }
    var lastRemotePoint by remember(server.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var viewerSize by remember { mutableStateOf(IntSize.Zero) }
    var activeForwardId by remember(server.id) { mutableStateOf<String?>(null) }
    var dragActive by remember(server.id) { mutableStateOf(false) }
    var downPoint by remember(server.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    var downAtMillis by remember(server.id) { mutableStateOf(0L) }
    val credential = server.credentialId?.let { id -> credentials.firstOrNull { it.id == id } }

    fun remotePoint(screenX: Float, screenY: Float): Pair<Int, Int>? {
        val bitmap = frame ?: return null
        val size = viewerSize.takeIf { it.width > 0 && it.height > 0 } ?: return null
        val scale = minOf(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        val left = (size.width - drawnWidth) / 2f
        val top = (size.height - drawnHeight) / 2f
        val x = ((screenX - left) / scale).toInt().coerceIn(0, bitmap.width - 1)
        val y = ((screenY - top) / scale).toInt().coerceIn(0, bitmap.height - 1)
        return x to y
    }

    fun screenPoint(remote: Pair<Int, Int>, hotspot: Pair<Int, Int>): IntOffset? {
        val bitmap = frame ?: return null
        val size = viewerSize.takeIf { it.width > 0 && it.height > 0 } ?: return null
        val scale = minOf(size.width / bitmap.width.toFloat(), size.height / bitmap.height.toFloat())
        val drawnWidth = bitmap.width * scale
        val drawnHeight = bitmap.height * scale
        val left = (size.width - drawnWidth) / 2f
        val top = (size.height - drawnHeight) / 2f
        return IntOffset(
            x = (left + ((remote.first - hotspot.first) * scale)).toInt(),
            y = (top + ((remote.second - hotspot.second) * scale)).toInt()
        )
    }

    fun disconnect() {
        client?.close()
        client = null
        activeForwardId?.let { forwardId ->
            activeForwardId = null
            scope.launch(Dispatchers.IO) { sshTransport.stopForward(forwardId) }
        }
        status = "Disconnected"
    }

    fun connect() {
        if (client?.running == true) return
        val passwordCredential = credential?.takeIf { it.type == CredentialType.Password && it.secretBacked }
        if (passwordCredential == null) {
            status = "Saved password required"
            onConnectionEvent(ConnectionEventLevel.Warning, "VNC requires a saved password credential.")
            return
        }
        status = "Connecting"
        scope.launch(Dispatchers.IO) {
            runCatching {
                val password = onLoadCredentialPayload(passwordCredential)
                val nextClient = VncClient(VncConfig().apply {
                    passwordSupplier = { password }
                    usernameSupplier = { server.username }
                    shared = server.vncConfig.shared
                    targetFps = server.vncConfig.targetFps
                    colorDepth = when (server.vncConfig.colorDepthBits) {
                        8 -> ColorDepth.BPP_8_TRUE
                        16 -> ColorDepth.BPP_16_TRUE
                        else -> ColorDepth.BPP_24_TRUE
                    }
                    onScreenUpdate = { bitmap -> frame = bitmap }
                    onCursorUpdate = { bitmap, hotX, hotY ->
                        cursorBitmap = bitmap
                        cursorHotspot = hotX to hotY
                    }
                    onBandwidthSuggestion = { depth ->
                        val hint = vncBandwidthHint(depth)
                        bandwidthHint = hint
                        onConnectionEvent(ConnectionEventLevel.Warning, hint)
                    }
                    onError = { error ->
                        status = error.message ?: error::class.java.simpleName
                        onConnectionEvent(ConnectionEventLevel.Error, "VNC failed: $status")
                    }
                })
                client = nextClient
                val (targetHost, targetPort) = if (server.vncConfig.tunnelOverSsh) {
                    val forwardId = "viewer-${server.id}-${UUID.randomUUID()}"
                    val forwardStatus = sshTransport.startLocalForward(
                        profile = server.copy(port = server.vncConfig.sshBootstrapPort),
                        credential = passwordCredential,
                        rule = PortForwardRule(
                            id = forwardId,
                            serverId = server.id,
                            type = PortForwardType.Local,
                            localHost = "127.0.0.1",
                            localPort = 0,
                            remoteHost = server.host,
                            remotePort = server.port,
                            enabled = false,
                            autoStart = false,
                            label = "VNC ${server.name}"
                        )
                    )
                    activeForwardId = forwardId
                    "127.0.0.1" to (forwardStatus.boundAddress.substringAfterLast(":").toIntOrNull() ?: error("Tunnel did not return a local port."))
                } else {
                    server.host to server.port
                }
                nextClient.start(targetHost, targetPort)
                status = "Connected"
                onConnectionEvent(ConnectionEventLevel.Success, "VNC connected to ${server.host}:${server.port}.")
            }.onFailure { error ->
                status = error.message ?: error::class.java.simpleName
                onConnectionEvent(ConnectionEventLevel.Error, "VNC failed: $status")
                disconnect()
            }
        }
    }

    LaunchedEffect(server.id) { connect() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton("<", "Back", modifier = Modifier.size(50.dp), onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, color = DeckColors.PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("VNC ${server.host}:${server.port} · $status", color = DeckColors.SecondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CircleIconButton(if (client?.running == true) "x" else "play", if (client?.running == true) "Disconnect" else "Connect", modifier = Modifier.size(50.dp)) {
                if (client?.running == true) disconnect() else connect()
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DeckColors.SurfaceMuted)
                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
                .onSizeChanged { viewerSize = it }
                .pointerInteropFilter { event ->
                    if (server.vncConfig.viewOnly) return@pointerInteropFilter false
                    val remote = remotePoint(event.x, event.y) ?: return@pointerInteropFilter false
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downPoint = remote
                            downAtMillis = event.eventTime
                            dragActive = false
                            client?.moveMouse(remote.first, remote.second)
                            lastRemotePoint = remote
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val start = downPoint
                            if (!dragActive && start != null && (kotlin.math.abs(remote.first - start.first) > touchSlop || kotlin.math.abs(remote.second - start.second) > touchSlop)) {
                                dragActive = true
                                client?.updateMouseButton(1, true)
                            }
                            client?.moveMouse(remote.first, remote.second)
                            lastRemotePoint = remote
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            client?.moveMouse(remote.first, remote.second)
                            lastRemotePoint = remote
                            if (dragActive) {
                                client?.updateMouseButton(1, false)
                            } else if (event.eventTime - downAtMillis >= ViewConfiguration.getLongPressTimeout()) {
                                client?.click(3)
                            } else {
                                client?.click(1)
                            }
                            dragActive = false
                            downPoint = null
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            if (dragActive) client?.updateMouseButton(1, false)
                            dragActive = false
                            downPoint = null
                            true
                        }
                        MotionEvent.ACTION_SCROLL -> {
                            val wheelButton = if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) > 0f) 4 else 5
                            client?.moveMouse(remote.first, remote.second)
                            lastRemotePoint = remote
                            client?.click(wheelButton)
                            true
                        }
                        MotionEvent.ACTION_BUTTON_PRESS -> {
                            if (event.actionButton == MotionEvent.BUTTON_SECONDARY) {
                                client?.moveMouse(remote.first, remote.second)
                                lastRemotePoint = remote
                                client?.click(3)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val bitmap = frame
            if (bitmap == null) {
                Text(status, color = DeckColors.SecondaryText, fontSize = 14.sp)
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "VNC framebuffer",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                val cursor = cursorBitmap
                val cursorAt = lastRemotePoint?.let { screenPoint(it, cursorHotspot) }
                if (cursor != null && cursorAt != null) {
                    Image(
                        bitmap = cursor.asImageBitmap(),
                        contentDescription = "Remote cursor",
                        modifier = Modifier
                            .offset { cursorAt }
                            .size(
                                width = with(density) { cursor.width.toDp() },
                                height = with(density) { cursor.height.toDp() }
                            )
                    )
                }
            }
        }

        DeckCard(padding = androidx.compose.foundation.layout.PaddingValues(12.dp), radius = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bandwidthHint?.let { hint ->
                    Text(hint, color = DeckColors.SecondaryText, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = remoteText,
                    onValueChange = { remoteText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Send text") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = DeckColors.PrimaryText,
                        unfocusedTextColor = DeckColors.PrimaryText,
                        focusedBorderColor = DeckColors.Cyan,
                        unfocusedBorderColor = DeckColors.CardStroke,
                        focusedLabelColor = DeckColors.SecondaryText,
                        unfocusedLabelColor = DeckColors.SecondaryText,
                        cursorColor = DeckColors.Cyan
                    )
                )
                CircleIconButton("send", "Type text", modifier = Modifier.size(48.dp)) {
                    if (!server.vncConfig.viewOnly) client?.typeText(remoteText)
                }
                CircleIconButton("clip", "Set remote clipboard", modifier = Modifier.size(48.dp)) {
                    if (!server.vncConfig.viewOnly) client?.copyText(remoteText)
                }
                }
            }
        }
    }
}

internal fun vncBandwidthHint(depth: ColorDepth): String {
    return "Slow VNC link detected. Try ${depth.depth}-bit colour."
}
