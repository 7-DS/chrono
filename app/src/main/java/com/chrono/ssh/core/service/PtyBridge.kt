package com.chrono.ssh.core.service

object PtyBridge {
    init {
        System.loadLibrary("pty_bridge")
    }

    external fun nativeForkPty(
        cmd: String,
        args: Array<String>,
        env: Array<String>,
        rows: Int,
        cols: Int
    ): IntArray

    external fun nativeSetSize(fd: Int, rows: Int, cols: Int): Int

    external fun nativeWaitPid(pid: Int): Int
}
