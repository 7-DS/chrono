package com.chrono.ssh.core.service

import android.content.Context
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.HostKeyTrustState
import com.chrono.ssh.core.model.KnownHost
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.service.rclone.RcloneFileShareClient
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.PublicKey
import java.io.StringReader
import java.net.NoRouteToHostException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.connection.channel.forwarded.RemotePortForwarder
import net.schmizz.sshj.connection.channel.forwarded.SocketForwardingConnectListener
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.transport.compression.DelayedZlibCompression
import net.schmizz.sshj.transport.compression.NoneCompression
import net.schmizz.sshj.transport.compression.ZlibCompression
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import net.schmizz.sshj.xfer.FilePermission
import net.schmizz.sshj.xfer.FileSystemFile

internal fun sshjShellCanUseTerminal(clientConnected: Boolean, shellOpen: Boolean, shellEof: Boolean): Boolean {
    return clientConnected && shellOpen && !shellEof
}

private fun readSshjCommandResult(command: String, cmd: Session.Command, timeoutSeconds: Long = 12L): CommandResult {
    val executor = Executors.newSingleThreadExecutor()
    return try {
        val future = executor.submit<CommandResult> {
            val stdout = cmd.inputStream.readBytes().toString(Charsets.UTF_8)
            val stderr = cmd.errorStream.readBytes().toString(Charsets.UTF_8)
            cmd.join(timeoutSeconds, TimeUnit.SECONDS)
            CommandResult(command, cmd.exitStatus ?: -1, stdout, stderr)
        }
        try {
            future.get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: Exception) {
            runCatching { cmd.close() }
            future.cancel(true)
            CommandResult(command, -1, "", "Command timed out.")
        }
    } finally {
        executor.shutdownNow()
    }
}

class SshjTransport(
    context: Context,
    private val secretStore: SecretStore,
    private val knownHostLookup: (ServerProfile) -> KnownHost?,
    private val proxyJumpLookup: (ServerProfile) -> ProxyJumpTarget? = { null },
    private val onHostKeySeen: (ServerProfile, String, String, HostKeyTrustState) -> Unit = { _, _, _, _ -> }
) : SshTransport, MoshCapableTransport, EtCapableTransport {
    private val appContext = context.applicationContext
    private val forwards = ConcurrentHashMap<String, RunningForward>()

    override suspend fun connect(profile: ServerProfile, credential: Credential?): SshSession {
        return connectExec(profile, credential, { HostKeyDecision.Reject }, privateKeyPassphrase = null)
    }

    override suspend fun connectExec(
        profile: ServerProfile,
        credential: Credential?,
        hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision,
        privateKeyPassphrase: String?
    ): SshSession = withContext(Dispatchers.IO) {
        var connection: ConnectedClient? = null
        try {
            connection = connectClient(profile, hostKeyDecision)
            val active = connection
            authenticate(active.client, profile, credential, privateKeyPassphrase)
            SshjExecSession(profile.id, active)
        } catch (error: Exception) {
            connection?.close()
            throw mapSshError("SSH exec connect failed for ${profile.host}:${profile.port}", error)
        }
    }

    override suspend fun connectShell(
        profile: ServerProfile,
        credential: Credential?,
        hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision,
        privateKeyPassphrase: String?
    ): SshSession = withContext(Dispatchers.IO) {
        var connection: ConnectedClient? = null
        try {
            val active = connectClient(profile, hostKeyDecision)
            connection = active
            authenticate(active.client, profile, credential, privateKeyPassphrase)
            val session = active.client.startSession()
            session.allocatePTY("xterm-256color", 80, 24, 0, 0, emptyMap())
            profile.environment.forEach { env -> session.setEnvVar(env.key, env.value) }
            val shell = session.startShell()
            val sshSession = SshjShellSession(profile.id, active, session, shell)
            if (profile.startDirectory.isNotBlank()) {
                sshSession.writeTerminal("cd ${shellQuoteForTerminalStartup(profile.startDirectory)}\n")
            }
            if (profile.startupCommand.isNotBlank()) {
                sshSession.writeTerminal("${profile.startupCommand}\n")
            }
            sshSession
        } catch (error: Exception) {
            connection?.close()
            throw mapSshError("SSH connect failed for ${profile.host}:${profile.port}", error)
        }
    }

    override suspend fun connectMosh(
        profile: ServerProfile,
        credential: Credential?,
        hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision,
        privateKeyPassphrase: String?
    ): SshSession = withContext(Dispatchers.IO) {
        var connection: ConnectedClient? = null
        try {
            val active = connectClient(profile, hostKeyDecision)
            connection = active
            authenticate(active.client, profile, credential, privateKeyPassphrase)
            val bootstrap = runMoshServer(active.client, profile)
            connection = null
            MoshShellSession(
                serverId = profile.id,
                bootstrap = bootstrap,
                bootstrapConnection = active
            )
        } catch (error: Exception) {
            connection?.close()
            throw mapSshError("Mosh connect failed for ${profile.host}:${profile.port}", error)
        }
    }

    override suspend fun connectEt(
        profile: ServerProfile,
        credential: Credential?,
        hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision,
        privateKeyPassphrase: String?
    ): SshSession = withContext(Dispatchers.IO) {
        var connection: ConnectedClient? = null
        try {
            val sshProfile = etBootstrapProfile(profile)
            val active = connectClient(sshProfile, hostKeyDecision)
            connection = active
            authenticate(active.client, sshProfile, credential, privateKeyPassphrase)
            val bootstrap = runEtTerminal(active.client, profile)
            connection = null
            EtShellSession(
                serverId = profile.id,
                bootstrap = bootstrap,
                bootstrapConnection = active
            )
        } catch (error: Exception) {
            connection?.close()
            throw mapSshError("ET connect failed for ${profile.host}:${profile.port}", error)
        }
    }

    override suspend fun openSftp(
        profile: ServerProfile,
        credential: Credential?,
        privateKeyPassphrase: String?
    ): SftpClient = withContext(Dispatchers.IO) {
        if (profile.protocol == ConnectionProtocol.Smb) {
            return@withContext SmbFileShareClient.open(
                profile = profile,
                credential = credential,
                secretStore = secretStore,
                downloadDir = File(appContext.cacheDir, "smb-downloads")
            )
        }
        if (profile.protocol == ConnectionProtocol.Rclone) {
            return@withContext RcloneFileShareClient.open(
                context = appContext,
                profile = profile,
                downloadDir = File(appContext.cacheDir, "rclone-downloads")
            )
        }
        var connection: ConnectedClient? = null
        val hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision = {
            if (knownHostLookup(profile)?.trusted == true) HostKeyDecision.TrustAndRemember
            else HostKeyDecision.Reject
        }
        try {
            val active = connectClient(profile, hostKeyDecision)
            connection = active
            authenticate(active.client, profile, credential, privateKeyPassphrase = privateKeyPassphrase)
            SshjSftpClient(
                connection = active,
                sftp = active.client.newSFTPClient(),
                downloadDir = File(appContext.cacheDir, "sftp-downloads").apply { mkdirs() }
            )
        } catch (error: Exception) {
            connection?.close()
            throw mapSshError("SFTP connect failed for ${profile.host}:${profile.port}", error)
        }
    }

    override suspend fun openScp(
        profile: ServerProfile,
        credential: Credential?,
        privateKeyPassphrase: String?
    ): ScpClient = withContext(Dispatchers.IO) {
        var connection: ConnectedClient? = null
        val hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision = {
            if (knownHostLookup(profile)?.trusted == true) HostKeyDecision.TrustAndRemember
            else HostKeyDecision.Reject
        }
        try {
            val active = connectClient(profile, hostKeyDecision)
            connection = active
            authenticate(active.client, profile, credential, privateKeyPassphrase = privateKeyPassphrase)
            SshjScpClient(
                connection = active,
                cacheDir = File(appContext.cacheDir, "scp-transfers").apply { mkdirs() }
            )
        } catch (error: Exception) {
            connection?.close()
            throw mapSshError("SCP connect failed for ${profile.host}:${profile.port}", error)
        }
    }

    override suspend fun verifyHost(profile: ServerProfile): KnownHost = withContext(Dispatchers.IO) {
        var seen: KnownHost? = null
        var connection: ConnectedClient? = null
        val hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision = { prompt ->
            seen = KnownHost(
                id = "known-${profile.id}",
                host = prompt.host,
                port = prompt.port,
                algorithm = prompt.algorithm,
                fingerprint = prompt.fingerprint,
                trusted = false,
                firstSeenEpochMillis = System.currentTimeMillis(),
                lastSeenEpochMillis = System.currentTimeMillis(),
                trustState = prompt.state
            )
            HostKeyDecision.Reject
        }
        try {
            connection = connectClient(profile, hostKeyDecision)
        } catch (_: Exception) {
        } finally {
            connection?.close()
        }
        seen ?: knownHostLookup(profile) ?: KnownHost(
            id = "known-${profile.id}",
            host = profile.host,
            port = profile.port,
            algorithm = "unknown",
            fingerprint = "Unavailable until network handshake succeeds",
            trusted = false,
            firstSeenEpochMillis = System.currentTimeMillis(),
            lastSeenEpochMillis = System.currentTimeMillis(),
            trustState = HostKeyTrustState.Unknown
        )
    }

    override suspend fun startLocalForward(
        profile: ServerProfile,
        credential: Credential?,
        rule: PortForwardRule,
        onClosed: (ForwardStatus) -> Unit
    ): ForwardStatus = withContext(Dispatchers.IO) {
        stopForward(rule.id)
        var connection: ConnectedClient? = null
        val hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision = {
            if (knownHostLookup(profile)?.trusted == true) HostKeyDecision.TrustAndRemember
            else HostKeyDecision.Reject
        }
        try {
            val active = connectClient(profile, hostKeyDecision)
            connection = active
            authenticate(active.client, profile, credential, privateKeyPassphrase = savedForwardPassphrase(credential))
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(rule.localHost, rule.localPort))
            }
            val boundPort = socket.localPort
            val parameters = Parameters(rule.localHost, boundPort, rule.remoteHost, rule.remotePort)
            val forwarder = active.client.newLocalPortForwarder(parameters, socket)
            val activeConnection = active
            val worker = Thread({
                val listenError = runCatching { forwarder.listen() }.exceptionOrNull()
                runCatching { socket.close() }
                activeConnection.close()
                val removed = forwards.remove(rule.id)
                if (removed != null) {
                    val status = if (listenError == null) {
                        ForwardRuntimePolicy.stopped(rule)
                    } else {
                        ForwardRuntimePolicy.failed(
                            rule,
                            listenError.message ?: listenError::class.java.simpleName
                        )
                    }
                    onClosed(status)
                }
            }, "chrono-forward-${rule.id}").apply {
                isDaemon = true
                start()
            }
            forwards[rule.id] = RunningForward(
                connection = active,
                socket = socket,
                localForwarder = forwarder,
                remoteForwarder = null,
                remoteForward = null,
                worker = worker
            )
            ForwardRuntimePolicy.running(
                rule,
                boundAddress = "${rule.localHost}:$boundPort",
                lastMessage = "Forwarding ${rule.localHost}:$boundPort to ${rule.remoteHost}:${rule.remotePort}"
            )
        } catch (error: Exception) {
            connection?.close()
            throw mapSshError("Forward start failed for ${rule.localHost}:${rule.localPort}", error)
        }
    }

    override suspend fun startRemoteForward(
        profile: ServerProfile,
        credential: Credential?,
        rule: PortForwardRule,
        onClosed: (ForwardStatus) -> Unit
    ): ForwardStatus = withContext(Dispatchers.IO) {
        stopForward(rule.id)
        var connection: ConnectedClient? = null
        val hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision = {
            if (knownHostLookup(profile)?.trusted == true) HostKeyDecision.TrustAndRemember
            else HostKeyDecision.Reject
        }
        try {
            val active = connectClient(profile, hostKeyDecision)
            connection = active
            authenticate(active.client, profile, credential, privateKeyPassphrase = savedForwardPassphrase(credential))
            val remoteForwarder = active.client.remotePortForwarder
            val requested = RemotePortForwarder.Forward(rule.remoteHost, rule.remotePort)
            val bound = remoteForwarder.bind(
                requested,
                SocketForwardingConnectListener(InetSocketAddress(rule.localHost, rule.localPort))
            )
            val activeConnection = active
            val worker = Thread({
                while (!Thread.currentThread().isInterrupted && activeConnection.client.isConnected) {
                    try {
                        Thread.sleep(1_000)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
                activeConnection.close()
                val removed = forwards.remove(rule.id)
                if (removed != null) {
                    onClosed(ForwardRuntimePolicy.stopped(rule))
                }
            }, "chrono-remote-forward-${rule.id}").apply {
                isDaemon = true
                start()
            }
            forwards[rule.id] = RunningForward(
                connection = active,
                socket = null,
                localForwarder = null,
                remoteForwarder = remoteForwarder,
                remoteForward = bound,
                worker = worker
            )
            ForwardRuntimePolicy.running(
                rule,
                boundAddress = "${bound.address}:${bound.port}",
                lastMessage = "Forwarding ${bound.address}:${bound.port} to ${rule.localHost}:${rule.localPort}"
            )
        } catch (error: Exception) {
            connection?.close()
            throw mapSshError("Remote forward start failed for ${rule.remoteHost}:${rule.remotePort}", error)
        }
    }

    override suspend fun startDynamicSocksForward(
        profile: ServerProfile,
        credential: Credential?,
        rule: PortForwardRule,
        onClosed: (ForwardStatus) -> Unit
    ): ForwardStatus = withContext(Dispatchers.IO) {
        stopForward(rule.id)
        var connection: ConnectedClient? = null
        val hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision = {
            if (knownHostLookup(profile)?.trusted == true) HostKeyDecision.TrustAndRemember
            else HostKeyDecision.Reject
        }
        try {
            val active = connectClient(profile, hostKeyDecision)
            connection = active
            authenticate(active.client, profile, credential, privateKeyPassphrase = savedForwardPassphrase(credential))
            val socket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(rule.localHost, rule.localPort))
            }
            val boundPort = socket.localPort
            val activeConnection = active
            val worker = Thread({
                val listenError = runCatching {
                    while (!socket.isClosed) {
                        val clientSocket = socket.accept()
                        Thread({
                            handleSocksClient(activeConnection, clientSocket)
                        }, "chrono-socks-${rule.id}").apply {
                            isDaemon = true
                            start()
                        }
                    }
                }.exceptionOrNull()?.takeUnless { socket.isClosed }
                runCatching { socket.close() }
                activeConnection.close()
                val removed = forwards.remove(rule.id)
                if (removed != null) {
                    val status = if (listenError == null) {
                        ForwardRuntimePolicy.stopped(rule)
                    } else {
                        ForwardRuntimePolicy.failed(rule, listenError.message ?: listenError::class.java.simpleName)
                    }
                    onClosed(status)
                }
            }, "chrono-socks-listener-${rule.id}").apply {
                isDaemon = true
                start()
            }
            forwards[rule.id] = RunningForward(
                connection = active,
                socket = socket,
                localForwarder = null,
                remoteForwarder = null,
                remoteForward = null,
                worker = worker
            )
            ForwardRuntimePolicy.running(
                rule,
                boundAddress = "${rule.localHost}:$boundPort",
                lastMessage = "SOCKS5 proxy listening on ${rule.localHost}:$boundPort"
            )
        } catch (error: Exception) {
            connection?.close()
            throw mapSshError("Dynamic SOCKS start failed for ${rule.localHost}:${rule.localPort}", error)
        }
    }

    override suspend fun stopForward(ruleId: String): Unit = withContext(Dispatchers.IO) {
        forwards.remove(ruleId)?.close()
        Unit
    }

    private fun handleSocksClient(active: ConnectedClient, socket: Socket) {
        socket.use { client ->
            client.soTimeout = 15_000
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val request = runCatching { Socks5Protocol.readConnectRequest(input, output) }
                .onFailure { runCatching { Socks5Protocol.writeFailure(output) } }
                .getOrNull() ?: return
            val direct = runCatching {
                active.client.newDirectConnection(request.host, request.port).also { it.open() }
            }.onFailure {
                runCatching { Socks5Protocol.writeFailure(output) }
            }.getOrNull() ?: return
            direct.use { channel ->
                Socks5Protocol.writeSuccess(output)
                val upstream = Thread({
                    runCatching { input.copyTo(channel.outputStream) }
                    runCatching { channel.outputStream.close() }
                }, "chrono-socks-upstream").apply {
                    isDaemon = true
                    start()
                }
                runCatching { channel.inputStream.copyTo(output) }
                runCatching { upstream.interrupt() }
            }
        }
    }

    private fun runMoshServer(client: SSHClient, profile: ServerProfile): MoshBootstrap {
        val command = MoshBootstrapCommand.build(
            startupCommand = profile.startupCommand,
            serverCommand = profile.moshConfig.serverCommand,
            locale = profile.moshConfig.locale,
            colors = profile.moshConfig.colors
        )
        client.startSession().use { session ->
            val cmd = session.exec(command)
            val result = readSshjCommandResult(command, cmd, timeoutSeconds = 20L)
            val combined = result.stdout + "\n" + result.stderr
            val match = MoshBootstrapParser.parseConnect(combined)
                ?: throw SshFailure.Unsupported(MoshBootstrapFailure.message(combined))
            val host = runCatching { InetAddress.getByName(profile.host).hostAddress }.getOrDefault(profile.host)
            return MoshBootstrap(host, match.port, match.key)
        }
    }

    private fun runEtTerminal(client: SSHClient, profile: ServerProfile): EtBootstrap {
        val proposal = EtBootstrapProposal.create()
        val config = profile.eternalTerminalConfig
        val command = EtBootstrapCommand.build(
            proposal = proposal,
            terminalType = config.terminalType,
            serverCommand = config.serverCommand
        )
        client.startSession().use { session ->
            val cmd = session.exec(command)
            val result = readSshjCommandResult(command, cmd, timeoutSeconds = 20L)
            val combined = result.stdout + "\n" + result.stderr
            val parsed = EtBootstrapParser.parseIdPasskey(combined)
                ?: throw SshFailure.Unsupported(EtBootstrapFailure.message(combined))
            val host = runCatching { InetAddress.getByName(profile.host).hostAddress }.getOrDefault(profile.host)
            return EtBootstrap(host, config.etServerPort, parsed.clientId, parsed.passkey)
        }
    }

    private fun etBootstrapProfile(profile: ServerProfile): ServerProfile {
        return if (profile.protocol == ConnectionProtocol.EternalTerminal) {
            profile.copy(port = profile.eternalTerminalConfig.sshBootstrapPort)
        } else {
            profile
        }
    }

    private fun newClient(
        profile: ServerProfile,
        hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision
    ): SSHClient {
        SecurityUtils.setRegisterBouncyCastle(false)
        SecurityUtils.setSecurityProvider(null)
        val config = DefaultConfig().also(AndroidSshjCompat::prepare)
        config.compressionFactories = if (profile.sshCompressionEnabled) {
            listOf(DelayedZlibCompression.Factory(), ZlibCompression.Factory(), NoneCompression.Factory())
        } else {
            listOf(NoneCompression.Factory())
        }
        return SSHClient(config).apply {
            addHostKeyVerifier(ChronoHostKeyVerifier(profile, hostKeyDecision))
            connectTimeout = profile.connectTimeoutSeconds.coerceIn(3, 60) * 1_000
            timeout = 20_000
            connection.keepAlive.keepAliveInterval = profile.reconnectPolicy.keepAliveSeconds.coerceAtLeast(10)
        }
    }

    private suspend fun connectClient(
        profile: ServerProfile,
        hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision
    ): ConnectedClient {
        return connectClient(profile, hostKeyDecision, visitedServerIds = emptySet())
    }

    private suspend fun connectClient(
        profile: ServerProfile,
        hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision,
        visitedServerIds: Set<String>
    ): ConnectedClient {
        if (profile.id in visitedServerIds) {
            throw SshFailure.Unsupported("ProxyJump chain cannot loop back to ${profile.name}.")
        }
        val nextVisited = visitedServerIds + profile.id
        val jump = proxyJumpLookup(profile)
        if (jump == null) {
            val client = newClient(profile, hostKeyDecision)
            client.connect(profile.host, profile.port)
            return ConnectedClient(client)
        }
        if (knownHostLookup(jump.profile)?.trusted != true) {
            throw SshFailure.HostKeyRejected("Approve the jump host key for ${jump.profile.name} before using it.")
        }
        val jumpConnection = connectClient(jump.profile, { HostKeyDecision.TrustAndRemember }, nextVisited)
        return try {
            authenticate(jumpConnection.client, jump.profile, jump.credential, privateKeyPassphrase = savedForwardPassphrase(jump.credential))
            val targetClient = newClient(profile, hostKeyDecision)
            try {
                targetClient.connectVia(jumpConnection.client.newDirectConnection(profile.host, profile.port))
                ConnectedClient(targetClient, listOf(jumpConnection.client) + jumpConnection.upstreamClients)
            } catch (error: Exception) {
                runCatching { targetClient.close() }
                throw error
            }
        } catch (error: Exception) {
            jumpConnection.close()
            throw error
        }
    }

    private suspend fun authenticate(
        client: SSHClient,
        profile: ServerProfile,
        credential: Credential?,
        privateKeyPassphrase: String?
    ) {
        if (credential == null || !credential.encryptedPayloadRef.startsWith("secret-")) {
            throw SshFailure.Authentication(credentialNotReadyMessage(profile, credential))
        }
        val secret = secretStore.loadSecret(credential.encryptedPayloadRef).toString(Charsets.UTF_8)
        if (secret.isBlank()) {
            val repair = when (credential.type) {
                CredentialType.Password -> "Enter and save the password again."
                CredentialType.PrivateKey -> "Re-import the private key or paste the full private-key text."
                CredentialType.HardwareKey -> "Select a password or private-key identity."
            }
            throw SshFailure.Authentication("Saved ${credential.type.name.lowercase()} credential '${credential.label}' is empty. $repair")
        }
        try {
            when (credential.type) {
                CredentialType.Password -> {
                    val passwordFinder = StaticPasswordFinder(secret)
                    client.auth(
                        profile.username,
                        AuthPassword(passwordFinder),
                        AuthKeyboardInteractive(PasswordResponseProvider(passwordFinder))
                    )
                }
                CredentialType.PrivateKey -> {
                    val normalizedSecret = normalizePrivateKeyMaterial(secret)
                    val keyInfo = KeyMaterialInspector.inspectPrivateKey(normalizedSecret)
                    if (!keyInfo.valid) {
                        val repairHint = if (KeyMaterialInspector.looksLikePrivateKeyPath(normalizedSecret)) {
                            " Re-import this key from the host editor or Vault so ChronoSSH stores the private-key text instead of a temporary file path."
                        } else {
                            ""
                        }
                        throw SshFailure.Authentication("Private key '${credential.label}' is not usable: ${keyInfo.summary}.$repairHint")
                    }
                    val isOpenSshPrivateKey = normalizedSecret.contains("-----BEGIN OPENSSH PRIVATE KEY-----")
                    val tempKeyFiles = mutableListOf<File>()
                    val passphrase = resolvePrivateKeyPassphrase(
                        privateKeyPassphrase = privateKeyPassphrase,
                        savedPassphraseRef = credential.passphraseRef,
                        loadSecret = secretStore::loadSecret
                    )
                    if (keyInfo.encrypted && passphrase.isNullOrEmpty()) {
                        throw SshFailure.Authentication("Private key '${credential.label}' is encrypted. Enter its passphrase to connect.")
                    }
                    try {
                        val inMemoryOpenSshProvider = AndroidSshjCompat.loadOpenSshIdentity(
                            client = client,
                            privateKeyMaterial = normalizedSecret,
                            passphrase = passphrase
                        )
                        val providerLoadErrors = mutableListOf<String>()
                        val providers = buildList {
                            inMemoryOpenSshProvider?.let(::add)
                            AndroidSshjCompat.loadPemRsaIdentity(
                                client = client,
                                privateKeyMaterial = normalizedSecret,
                                passphrase = passphrase
                            )?.let(::add)
                            if (isOpenSshPrivateKey) {
                                runCatching {
                                    OpenSSHKeyV1KeyFile().apply {
                                        init(StringReader(normalizedSecret), null as java.io.Reader?, StaticPasswordFinder(passphrase.orEmpty()))
                                    }
                                }.onFailure {
                                    providerLoadErrors += "openssh-v1=${it.sanitizedKeyError()}"
                                }.getOrNull()?.let(::add)
                            }
                            if (!isOpenSshPrivateKey) {
                                runCatching {
                                    OpenSSHKeyFile().apply {
                                        init(StringReader(normalizedSecret), null as java.io.Reader?, StaticPasswordFinder(passphrase.orEmpty()))
                                    }
                                }.onFailure {
                                    providerLoadErrors += "reader=${it.sanitizedKeyError()}"
                                }.getOrNull()?.let(::add)
                            }
                            AndroidSshjCompat.loadPkcs8Ed25519Identity(
                                client = client,
                                privateKeyMaterial = normalizedSecret,
                                publicKeyMaterial = credential.publicKeyPreview,
                                passphrase = passphrase
                            )?.let(::add)
                            if (!isOpenSshPrivateKey) {
                                tempKeyProvider(client, credential, normalizedSecret, passphrase, tempKeyFiles, providerLoadErrors)?.let(::add)
                            }
                        }.distinctBy { it.type.toString() + ":" + runCatching { it.public.toString() }.getOrDefault(it.toString()) }
                        if (providers.isEmpty()) {
                            val formatHint = when {
                                isOpenSshPrivateKey && keyInfo.encrypted ->
                                    "Encrypted modern OpenSSH keys need the correct passphrase. Enter its passphrase to connect or save it intentionally."
                                isOpenSshPrivateKey ->
                                    "This build loads OpenSSH v1 keys through SSHJ plus Android RSA/Ed25519 compatibility. FIDO/security-key private keys are not supported locally."
                                keyInfo.encrypted ->
                                    "Enter the key passphrase or save it in the identity."
                                else ->
                                    "Import the full private-key text, not a file path or public key."
                            }
                            val loadDetail = providerLoadErrors.distinct().take(3).joinToString("; ").ifBlank { "no compatible provider accepted the key" }
                            throw SshFailure.Authentication("Private key '${credential.label}' could not be loaded. Format: ${keyInfo.summary}. $formatHint Loader detail: $loadDetail.")
                        }
                        var lastError: Exception? = null
                        val authErrors = mutableListOf<String>()
                        val providerTypes = providers.map { runCatching { it.type.toString() }.getOrDefault(it::class.java.simpleName) }
                        for (provider in providers) {
                            try {
                                client.authPublickey(profile.username, provider)
                                return
                            } catch (error: Exception) {
                                lastError = error
                                authErrors += "${runCatching { provider.type.toString() }.getOrDefault(provider::class.java.simpleName)}=${error.sanitizedKeyError()}"
                            }
                        }
                        val allowedMethods = client.userAuth.allowedMethods
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString(", ")
                            ?: "not reported"
                        val partialSuccess = client.userAuth.hadPartialSuccess()
                        val methodHint = SshAuthFailureHints.privateKeyRejected(
                            allowedMethods = allowedMethods,
                            keyInfo = keyInfo,
                            passphraseProvided = !passphrase.isNullOrEmpty()
                        )
                        throw SshFailure.Authentication(
                            "Private-key auth failed for '${credential.label}'. $methodHint Format=${keyInfo.summary}; encrypted=${keyInfo.encrypted}; passphraseProvided=${!passphrase.isNullOrEmpty()}; providers=${providerTypes.joinToString()}; serverAllowed=$allowedMethods; partialSuccess=$partialSuccess; attempts=${authErrors.distinct().take(4).joinToString("; ").ifBlank { lastError?.sanitizedKeyError() ?: "none" }}."
                        )
                    } finally {
                        tempKeyFiles.forEach(::wipeAndDelete)
                    }
                }
                CredentialType.HardwareKey -> throw SshFailure.Unsupported("Hardware keys are not wired in this local build yet.")
            }
        } catch (error: SshFailure) {
            throw error
        } catch (error: Exception) {
            val allowedMethods = client.userAuth.allowedMethods
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: "not reported"
            val partialSuccess = client.userAuth.hadPartialSuccess()
            throw SshFailure.Authentication(
                "Authentication failed with ${credential.type.name.lowercase()} credential '${credential.label}': ${error.message ?: error::class.java.simpleName}. Server allowed methods: $allowedMethods. Partial success: $partialSuccess."
            )
        }
    }

    private suspend fun savedForwardPassphrase(credential: Credential?): String? {
        if (credential?.type != CredentialType.PrivateKey) return null
        return resolvePrivateKeyPassphrase(
            privateKeyPassphrase = null,
            savedPassphraseRef = credential.passphraseRef,
            loadSecret = secretStore::loadSecret
        )
    }

    private inner class ChronoHostKeyVerifier(
        private val profile: ServerProfile,
        private val decide: suspend (HostKeyPrompt) -> HostKeyDecision
    ) : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val algorithm = key.algorithm
            val fingerprint = sha256Fingerprint(key)
            val existing = knownHostLookup(profile)
            val observed = KnownHost(
                id = existing?.id ?: "known-${profile.id}",
                host = profile.host,
                port = profile.port,
                algorithm = algorithm,
                fingerprint = fingerprint,
                trusted = false,
                firstSeenEpochMillis = existing?.firstSeenEpochMillis ?: System.currentTimeMillis(),
                lastSeenEpochMillis = System.currentTimeMillis(),
                trustState = HostKeyTrustState.Unknown
            )
            val state = HostKeyTrustEvaluator.evaluate(observed, existing)
            onHostKeySeen(profile, algorithm, fingerprint, state)
            if (state == HostKeyTrustState.Trusted) return true
            if (state in setOf(HostKeyTrustState.Changed, HostKeyTrustState.Rejected)) return false
            val decision = kotlinx.coroutines.runBlocking {
                decide(
                    HostKeyPrompt(
                        host = hostname,
                        port = port,
                        algorithm = algorithm,
                        fingerprint = fingerprint,
                        state = state,
                        message = if (state == HostKeyTrustState.Changed) {
                            "Host key changed. Verify this fingerprint before continuing."
                        } else {
                            "Unknown host key. Verify this fingerprint before continuing."
                        }
                    )
                )
            }
            return decision != HostKeyDecision.Reject
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): MutableList<String> {
            return knownHostLookup(profile)
                ?.takeIf { it.trusted }
                ?.let { mutableListOf(it.algorithm) }
                ?: mutableListOf()
        }
    }

    private class SshjShellSession(
        override val serverId: String,
        private val connection: ConnectedClient,
        private val directSession: Session,
        private val shell: Session.Shell
    ) : SshSession {
        override val id: String = "sshj-$serverId-${UUID.randomUUID()}"
        private val transcript = StringBuilder()
        private var outputSink: (ByteArray) -> Unit = {}
        @Volatile private var closeHandler: () -> Unit = {}
        private val closed = AtomicBoolean(false)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        init {
            scope.launch {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                try {
                    while (sshjShellCanUseTerminal(connection.client.isConnected, shell.isOpen, shell.isEOF)) {
                        val read = runCatching { shell.inputStream.read(buffer) }.getOrElse { -1 }
                        if (read <= 0) break
                        val bytes = buffer.copyOf(read)
                        transcript.append(bytes.toString(Charsets.UTF_8))
                        outputSink(bytes)
                    }
                } finally {
                    runCatching { closeTransportResources() }
                    closeHandler()
                }
            }
        }

        override val transcriptPreview: String
            get() = transcript.toString().takeLast(1200)
        override val isConnected: Boolean get() = canUseTerminal()

        override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult = withContext(Dispatchers.IO) {
            if (!connection.client.isConnected) {
                return@withContext CommandResult(command, -1, "", "SSH connection is closed.")
            }
            connection.client.startSession().use { execSession ->
                val cmd = execSession.exec(command)
                readSshjCommandResult(command, cmd, timeoutSeconds)
            }
        }

        override suspend fun resizeTerminal(columns: Int, rows: Int) = withContext(Dispatchers.IO) {
            if (!canUseTerminal()) {
                return@withContext
            }
            runCatching { shell.changeWindowDimensions(columns, rows, 0, 0) }
                .onFailure {
                    if (!canUseTerminal() || it.message?.contains("Socket closed", ignoreCase = true) == true) {
                        closeTransportResources()
                    } else {
                        throw SshFailure.Network("SSH resize failed: ${it.message ?: it::class.java.simpleName}", it)
                    }
                }
        }

        override suspend fun writeTerminal(input: String) = withContext(Dispatchers.IO) {
            if (!canUseTerminal()) {
                throw SshFailure.Network("SSH shell is closed.")
            }
            runCatching {
                shell.outputStream.write(input.toByteArray(Charsets.UTF_8))
                shell.outputStream.flush()
            }.getOrElse { throw SshFailure.Network("SSH write failed: ${it.message ?: it::class.java.simpleName}", it) }
        }

        override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) {
            outputSink = onData
        }

        override fun setTerminalCloseHandler(onClosed: () -> Unit) {
            closeHandler = onClosed
        }

        private fun canUseTerminal(): Boolean {
            return !closed.get() && sshjShellCanUseTerminal(connection.client.isConnected, shell.isOpen, shell.isEOF)
        }

        private fun closeTransportResources() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { shell.close() }
            runCatching { directSession.close() }
            connection.close()
        }

        override suspend fun close(): Unit = withContext(Dispatchers.IO) {
            closeTransportResources()
            scope.cancel()
            Unit
        }
    }

    private class SshjExecSession(
        override val serverId: String,
        private val connection: ConnectedClient
    ) : SshSession {
        override val id: String = "sshj-exec-$serverId-${UUID.randomUUID()}"
        override val transcriptPreview: String = ""

        override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult = withContext(Dispatchers.IO) {
            if (!connection.client.isConnected) {
                return@withContext CommandResult(command, -1, "", "SSH connection is closed.")
            }
            connection.client.startSession().use { execSession ->
                val cmd = execSession.exec(command)
                readSshjCommandResult(command, cmd, timeoutSeconds)
            }
        }

        override suspend fun resizeTerminal(columns: Int, rows: Int) = Unit

        override suspend fun writeTerminal(input: String) {
            throw SshFailure.Unsupported("This SSH session is for exec commands, not an interactive terminal.")
        }

        override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) = Unit

        override suspend fun close(): Unit = withContext(Dispatchers.IO) {
            connection.close()
            Unit
        }
    }

    private class SshjSftpClient(
        private val connection: ConnectedClient,
        private val sftp: net.schmizz.sshj.sftp.SFTPClient,
        private val downloadDir: File
    ) : SftpClient {
        override suspend fun realpath(path: String): String = withContext(Dispatchers.IO) {
            requireConnected()
            val target = normalizeRemotePath(path)
            runCatching { sftp.canonicalize(target) }.getOrElse { target }
        }

        override suspend fun list(path: String): List<SftpEntry> = withContext(Dispatchers.IO) {
            requireConnected()
            val basePath = normalizeRemotePath(path)
            runCatching { sftp.ls(basePath) }.getOrElse { error ->
                throw IllegalStateException(SftpErrorMapper.message("list", basePath, error), error)
            }
                .filterNot { it.name == "." || it.name == ".." }
                .map { entry ->
                    val attrs = entry.attributes
                    val fullPath = entry.path
                        .takeIf { it.isNotBlank() && it != entry.name }
                        ?: basePath.trimEnd('/').let { base ->
                            if (base == "/" || base.isBlank()) "/${entry.name}" else "$base/${entry.name}"
                        }
                    SftpEntry(
                        name = entry.name,
                        path = fullPath,
                        directory = attrs.type == FileMode.Type.DIRECTORY,
                        sizeBytes = attrs.size,
                        modifiedEpochMillis = attrs.mtime * 1000L,
                        type = when (attrs.type) {
                            FileMode.Type.DIRECTORY -> SftpEntryType.Directory
                            FileMode.Type.SYMLINK -> SftpEntryType.Symlink
                            else -> SftpEntryType.File
                        },
                        permissions = attrs.permissions.toOctalPermissions(),
                        owner = attrs.uid.takeIf { it >= 0 }?.toString(),
                        group = attrs.gid.takeIf { it >= 0 }?.toString()
                    )
                }
        }

        override suspend fun download(
            remotePath: String,
            localDisplayName: String,
            onProgress: (Float) -> Unit
        ): TransferHandle = withContext(Dispatchers.IO) {
            val safeName = SftpPathResolver.leafName(localDisplayName.ifBlank { remotePath })
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val target = File(downloadDir, "${System.currentTimeMillis()}-$safeName")
            runCatching {
                requireConnected()
                val source = normalizeRemotePath(remotePath)
                val totalBytes = runCatching { sftp.size(source) }.getOrDefault(0L)
                sftp.open(source, setOf(OpenMode.READ)).use { remote ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var offset = 0L
                        while (true) {
                            ensureActive()
                            val read = remote.read(offset, buffer, 0, buffer.size)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            offset += read
                            onProgress(transferProgress(offset, totalBytes))
                        }
                    }
                }
                onProgress(1f)
            }.getOrElse { error ->
                throw IllegalStateException(SftpErrorMapper.message("download", remotePath, error), error)
            }
            TransferHandle("download-${remotePath.hashCode()}-${System.currentTimeMillis()}", target.name, 1f, TransferState.Complete, target.absolutePath)
        }

        override suspend fun downloadTo(
            remotePath: String,
            localDisplayName: String,
            output: OutputStream,
            onProgress: (Float) -> Unit
        ): TransferHandle = withContext(Dispatchers.IO) {
            runCatching {
                requireConnected()
                val source = normalizeRemotePath(remotePath)
                val totalBytes = runCatching { sftp.size(source) }.getOrDefault(0L)
                sftp.open(source, setOf(OpenMode.READ)).use { remote ->
                    val buffer = ByteArray(64 * 1024)
                    var offset = 0L
                    while (true) {
                        ensureActive()
                        val read = remote.read(offset, buffer, 0, buffer.size)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        offset += read
                        onProgress(transferProgress(offset, totalBytes))
                    }
                    output.flush()
                }
                onProgress(1f)
            }.getOrElse { error ->
                throw IllegalStateException(SftpErrorMapper.message("download", remotePath, error), error)
            }
            TransferHandle("download-${remotePath.hashCode()}-${System.currentTimeMillis()}", localDisplayName, 1f, TransferState.Complete)
        }

        override suspend fun upload(
            localDisplayName: String,
            remotePath: String,
            onProgress: (Float) -> Unit
        ): TransferHandle = withContext(Dispatchers.IO) {
            val localFile = File(localDisplayName)
            require(localFile.exists()) { "Selected local file is no longer available." }
            runCatching {
                requireConnected()
                val destination = normalizeRemotePath(remotePath)
                val totalBytes = localFile.length()
                uploadWithFallback(destination, totalBytes, { localFile.inputStream() }, onProgress)
                onProgress(1f)
            }.getOrElse { error ->
                throw IllegalStateException(SftpErrorMapper.message("upload to", remotePath, error), error)
            }
            TransferHandle("upload-${remotePath.hashCode()}-${System.currentTimeMillis()}", localFile.name, 1f, TransferState.Complete)
        }

        override suspend fun uploadFrom(
            localDisplayName: String,
            remotePath: String,
            totalBytes: Long,
            input: InputStream,
            onProgress: (Float) -> Unit
        ): TransferHandle = withContext(Dispatchers.IO) {
            val safeName = SftpPathResolver.leafName(localDisplayName)
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val spoolFile = File(downloadDir, "upload-spool-${UUID.randomUUID()}-$safeName")
            try {
                runCatching {
                    requireConnected()
                    input.use { source ->
                        spoolFile.outputStream().use { target ->
                            val buffer = ByteArray(64 * 1024)
                            var copied = 0L
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = source.read(buffer)
                                if (read <= 0) break
                                target.write(buffer, 0, read)
                                copied += read
                                onProgress(if (totalBytes > 0L) (copied.toFloat() / totalBytes.toFloat() * 0.05f).coerceIn(0.01f, 0.05f) else 0.02f)
                            }
                        }
                    }
                    val destination = normalizeRemotePath(remotePath)
                    val uploadBytes = spoolFile.length().takeIf { it > 0L } ?: totalBytes
                    uploadWithFallback(
                        destination = destination,
                        totalBytes = uploadBytes,
                        inputFactory = { spoolFile.inputStream() },
                        onProgress = { progress -> onProgress((0.05f + progress * 0.95f).coerceIn(0.05f, 1f)) }
                    )
                    onProgress(1f)
                }.getOrElse { error ->
                    throw IllegalStateException(SftpErrorMapper.message("upload to", remotePath, error), error)
                }
            } finally {
                runCatching { spoolFile.delete() }
            }
            TransferHandle("upload-${remotePath.hashCode()}-${System.currentTimeMillis()}", localDisplayName, 1f, TransferState.Complete)
        }

        override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
            requireConnected()
            runCatching { sftp.rename(normalizeRemotePath(from), normalizeRemotePath(to)) }.getOrElse { error ->
                throw IllegalStateException(SftpErrorMapper.message("rename", from, error), error)
            }
        }

        override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
            requireConnected()
            val target = normalizeRemotePath(path)
            val type = runCatching { sftp.type(target) }.getOrNull()
            runCatching {
                if (type == FileMode.Type.DIRECTORY) {
                    sftp.rmdir(target)
                } else {
                    runCatching { sftp.rm(target) }.getOrElse { rmError ->
                        if (SftpDeletePolicy.shouldRetryAsDirectory(rmError)) {
                            sftp.rmdir(target)
                        } else {
                            throw rmError
                        }
                    }
                }
            }.getOrElse { error ->
                throw IllegalStateException(SftpErrorMapper.message("delete", target, error), error)
            }
        }

        override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
            requireConnected()
            runCatching { sftp.mkdirs(normalizeRemotePath(path)) }.getOrElse { error ->
                throw IllegalStateException(SftpErrorMapper.message("create folder", path, error), error)
            }
        }

        override suspend fun chmod(path: String, mode: Int) = withContext(Dispatchers.IO) {
            requireConnected()
            runCatching { sftp.chmod(normalizeRemotePath(path), mode) }.getOrElse { error ->
                throw IllegalStateException(SftpErrorMapper.message("chmod", path, error), error)
            }
        }

        override suspend fun close(): Unit = withContext(Dispatchers.IO) {
            runCatching { sftp.close() }
            connection.close()
            Unit
        }

        private fun requireConnected() {
            if (!connection.client.isConnected) throw SshFailure.Network("SFTP connection is closed.")
        }

        private fun normalizeRemotePath(path: String): String {
            return when (val normalized = SftpPathResolver.normalize(path)) {
                "~", "~/" -> "."
                else -> normalized.replace(Regex("^~/(?=.)"), "./")
            }
        }

        private suspend fun uploadWithFallback(
            destination: String,
            totalBytes: Long,
            inputFactory: () -> InputStream,
            onProgress: (Float) -> Unit
        ) {
            runCatching {
                inputFactory().use { input ->
                    uploadAtomically(destination, totalBytes, input, onProgress)
                }
            }.getOrElse { atomicError ->
                if (!SftpUploadFallbackPolicy.shouldTryDirectUpload(atomicError)) throw atomicError
                inputFactory().use { input ->
                    uploadDirect(destination, totalBytes, input, onProgress)
                }
            }
        }

        private suspend fun uploadAtomically(
            destination: String,
            totalBytes: Long,
            input: InputStream,
            onProgress: (Float) -> Unit
        ) {
            val tempPath = normalizeRemotePath(
                SftpAtomicUploadPolicy.tempPathFor(destination, UUID.randomUUID().toString())
            )
            try {
                sftp.open(tempPath, setOf(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)).use { remote ->
                    val buffer = ByteArray(64 * 1024)
                    var offset = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        remote.write(offset, buffer, 0, read)
                        offset += read
                        onProgress(transferProgress(offset, totalBytes))
                    }
                }
                runCatching { sftp.rename(tempPath, destination) }.getOrElse { renameError ->
                    runCatching { sftp.rm(destination) }.getOrNull()
                    runCatching { sftp.rename(tempPath, destination) }.getOrElse { throw renameError }
                }
            } catch (error: Throwable) {
                runCatching { sftp.rm(tempPath) }
                throw error
            }
        }

        private suspend fun uploadDirect(
            destination: String,
            totalBytes: Long,
            input: InputStream,
            onProgress: (Float) -> Unit
        ) {
            sftp.open(destination, setOf(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC)).use { remote ->
                val buffer = ByteArray(64 * 1024)
                var offset = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read <= 0) break
                    remote.write(offset, buffer, 0, read)
                    offset += read
                    onProgress(transferProgress(offset, totalBytes))
                }
            }
        }

        private fun transferProgress(doneBytes: Long, totalBytes: Long): Float {
            return if (totalBytes <= 0L) 0.05f else (doneBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 1f)
        }
    }

    private class SshjScpClient(
        private val connection: ConnectedClient,
        private val cacheDir: File
    ) : ScpClient {
        private val scp = connection.client.newSCPFileTransfer()

        override suspend fun downloadTo(
            remotePath: String,
            localDisplayName: String,
            output: OutputStream,
            onProgress: (Float) -> Unit
        ): TransferHandle = withContext(Dispatchers.IO) {
            val source = ScpTransferPolicy.normalizeRemotePath(remotePath)
                ?: throw IllegalArgumentException("Choose a remote file path for SCP download.")
            val safeName = ScpTransferPolicy.safeDisplayName(localDisplayName, source)
            val target = File(cacheDir, "scp-download-${UUID.randomUUID()}-$safeName")
            try {
                scp.download(source, FileSystemFile(target))
                val totalBytes = target.length()
                target.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(ScpTransferPolicy.progress(copied, totalBytes))
                    }
                    output.flush()
                }
                onProgress(1f)
                TransferHandle("scp-download-${source.hashCode()}-${System.currentTimeMillis()}", safeName, 1f, TransferState.Complete)
            } catch (error: Exception) {
                throw IllegalStateException("SCP download failed for $source: ${error.message ?: error::class.java.simpleName}", error)
            } finally {
                runCatching { target.delete() }
            }
        }

        override suspend fun uploadFrom(
            localDisplayName: String,
            remotePath: String,
            totalBytes: Long,
            input: InputStream,
            onProgress: (Float) -> Unit
        ): TransferHandle = withContext(Dispatchers.IO) {
            val destination = ScpTransferPolicy.normalizeRemotePath(remotePath)
                ?: throw IllegalArgumentException("Choose a remote file path for SCP upload.")
            val safeName = ScpTransferPolicy.safeDisplayName(localDisplayName, destination)
            val spool = File(cacheDir, "scp-upload-${UUID.randomUUID()}-$safeName")
            try {
                input.use { source ->
                    spool.outputStream().use { target ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = source.read(buffer)
                            if (read <= 0) break
                            target.write(buffer, 0, read)
                            copied += read
                            onProgress((ScpTransferPolicy.progress(copied, totalBytes) * 0.1f).coerceIn(0.01f, 0.1f))
                        }
                    }
                }
                scp.upload(spool.absolutePath, destination)
                onProgress(1f)
                TransferHandle("scp-upload-${destination.hashCode()}-${System.currentTimeMillis()}", safeName, 1f, TransferState.Complete)
            } catch (error: Exception) {
                throw IllegalStateException("SCP upload failed for $destination: ${error.message ?: error::class.java.simpleName}", error)
            } finally {
                runCatching { spool.delete() }
            }
        }

        override suspend fun close(): Unit = withContext(Dispatchers.IO) {
            connection.close()
            Unit
        }
    }

    private data class RunningForward(
        val connection: ConnectedClient,
        val socket: ServerSocket?,
        val localForwarder: LocalPortForwarder?,
        val remoteForwarder: RemotePortForwarder?,
        val remoteForward: RemotePortForwarder.Forward?,
        val worker: Thread?
    ) {
        fun close() {
            val remote = remoteForward
            val remoteOwner = remoteForwarder
            if (remoteOwner != null && remote != null) {
                runCatching { remoteOwner.cancel(remote) }
            }
            runCatching { localForwarder?.close() }
            runCatching { socket?.close() }
            connection.close()
            runCatching { worker?.interrupt() }
        }
    }

    private data class ConnectedClient(
        val client: SSHClient,
        val upstreamClients: List<SSHClient> = emptyList()
    ) : AutoCloseable {
        override fun close() {
            runCatching { client.disconnect() }
            runCatching { client.close() }
            upstreamClients.forEach { upstream ->
                runCatching { upstream.disconnect() }
                runCatching { upstream.close() }
            }
        }
    }

    private fun sha256Fingerprint(key: PublicKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        return "SHA256:${Base64.getEncoder().withoutPadding().encodeToString(digest)}"
    }

    private class StaticPasswordFinder(private val password: String) : PasswordFinder {
        override fun reqPassword(resource: Resource<*>?): CharArray = password.toCharArray()

        override fun shouldRetry(resource: Resource<*>?): Boolean = false
    }

    private fun tempKeyProvider(
        client: SSHClient,
        credential: Credential,
        secret: String,
        passphrase: String?,
        tempKeyFiles: MutableList<File>,
        loadErrors: MutableList<String>
    ): net.schmizz.sshj.userauth.keyprovider.KeyProvider? {
        val keyDir = File(appContext.cacheDir, "ssh-keys").apply { mkdirs() }
        val keyFile = File.createTempFile("chrono-${credential.id.take(12)}-", ".key", keyDir)
        return try {
            keyFile.writeText(secret, Charsets.UTF_8)
            tempKeyFiles += keyFile
            client.loadKeys(keyFile.absolutePath, StaticPasswordFinder(passphrase.orEmpty()))
        } catch (error: Exception) {
            loadErrors += "file=${error.sanitizedKeyError()}"
            wipeAndDelete(keyFile)
            null
        }
    }

    private fun wipeAndDelete(file: File) {
        runCatching {
            if (file.exists()) file.writeText("", Charsets.UTF_8)
        }
        runCatching { file.delete() }
    }

    private fun normalizePrivateKeyMaterial(secret: String): String {
        val normalized = secret
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
        return if (normalized.endsWith("\n")) normalized else "$normalized\n"
    }

    private fun mapSshError(message: String, error: Exception): SshFailure {
        if (error is SshFailure) return error
        val detail = error.message ?: error::class.java.simpleName
        SshConnectionErrorClassifier.classify(message, detail, error)?.let { return it }
        val lowerDetail = detail.lowercase()
        return when (error) {
            is UserAuthException -> SshFailure.Authentication(
                "$message: server rejected the selected credential. Check username, password/key, key passphrase, authorized_keys, and whether this server allows that auth method."
            )
            is NoRouteToHostException -> SshFailure.Network(
                "$message: no route to host. Check Wi-Fi/VPN, VM network mode, subnet/firewall rules, and whether this Android device can reach ${error.message ?: "the host"}.",
                error
            )
            is java.net.SocketTimeoutException,
            is java.net.ConnectException,
            is java.net.UnknownHostException -> SshFailure.Network("$message: $detail", error)
            else -> when {
                lowerDetail.contains("exhausted available authentication methods") -> SshFailure.Authentication(
                    "$message: server rejected every auth method the app could offer. Check the username, selected identity, saved key payload, key passphrase, authorized_keys entry, and server PasswordAuthentication/PubkeyAuthentication settings."
                )
                lowerDetail.contains("supported pem key type not found") && lowerDetail.contains("openssh private key") -> SshFailure.Authentication(
                    "$message: the key is modern OpenSSH format. ChronoSSH uses its Android OpenSSH loader first; if auth still failed, this specific key type may be unsupported or the server rejected the public key."
                )
                lowerDetail.contains("enoent") && lowerDetail.contains("ssh-keys") -> SshFailure.Authentication(
                    "$message: an internal temporary key-file fallback failed. Re-import the key so the vault stores the full private-key text, then retry."
                )
                lowerDetail.contains("host key") &&
                    (lowerDetail.contains("reject") ||
                        lowerDetail.contains("verify") ||
                        lowerDetail.contains("changed") ||
                        lowerDetail.contains("not trusted") ||
                        lowerDetail.contains("unknown") ||
                        lowerDetail.contains("fingerprint")) -> SshFailure.HostKeyRejected(
                    "$message: host key is not trusted yet. Scan and approve this host key before opening SSH or SFTP."
                )
                lowerDetail.contains("enetunreach") || lowerDetail.contains("network is unreachable") || lowerDetail.contains("no route to host") -> SshFailure.Network(
                    "$message: no route to host. Check Wi-Fi/VPN, VM network mode, subnet/firewall rules, and whether this Android device can reach the VM.",
                    error
                )
                else -> SshFailure.Network("$message: $detail", error)
            }
        }
    }

    private fun Throwable.sanitizedKeyError(): String {
        return sanitizeKeyLoaderError(message ?: javaClass.simpleName)
    }

}

internal fun shellQuoteForTerminalStartup(value: String): String {
    return "'" + value.replace("'", "'\\''") + "'"
}

internal fun credentialNotReadyMessage(profile: ServerProfile, credential: Credential?): String {
    val repair = when {
        credential == null ->
            "Link a password or private-key identity to this host."
        credential.encryptedPayloadRef == BackupCredentialPolicy.IMPORT_REQUIRED_REF ->
            "This identity was restored from metadata only. Open Vault, replace/re-import '${credential.label}', then retry."
        else ->
            "Replace '${credential.label}' in Vault so ChronoSSH stores a fresh secret payload."
    }
    return "Credential for ${profile.name} is not ready. $repair"
}

internal fun sanitizeKeyLoaderError(raw: String): String {
    return raw
        .replace(Regex("/data/user/0/[^\\s:]+/cache/ssh-keys/[^\\s:]+"), "<temporary-key-file>")
        .replace(Regex("\\\\cache\\\\ssh-keys\\\\[^\\s:]+"), "\\cache\\ssh-keys\\<temporary-key-file>")
        .ifBlank { "unknown key-loader error" }
}

private fun Set<FilePermission>.toOctalPermissions(): String? {
    if (isEmpty()) return null
    return FilePermission.toMask(this).and(0xFFF).toString(8).padStart(3, '0')
}

internal suspend fun resolvePrivateKeyPassphrase(
    privateKeyPassphrase: String?,
    savedPassphraseRef: String?,
    loadSecret: suspend (String) -> ByteArray
): String? {
    privateKeyPassphrase?.takeIf { it.isNotEmpty() }?.let { return it }
    val ref = savedPassphraseRef?.trim()?.takeIf { it.startsWith("secret-") } ?: return null
    return loadSecret(ref).toString(Charsets.UTF_8).takeIf { it.isNotEmpty() }
}
