package com.chrono.ssh.core.service

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress

data class Socks5ConnectRequest(
    val host: String,
    val port: Int
)

object Socks5Protocol {
    private const val Version = 0x05
    private const val NoAuth = 0x00
    private const val NoAcceptableMethods = 0xff
    private const val Connect = 0x01
    private const val AtypIpv4 = 0x01
    private const val AtypDomain = 0x03
    private const val AtypIpv6 = 0x04

    fun readConnectRequest(input: InputStream, output: OutputStream): Socks5ConnectRequest {
        require(readByte(input) == Version) { "Only SOCKS5 is supported." }
        val methods = ByteArray(readByte(input))
        readFully(input, methods)
        if (!methods.any { it.toInt() and 0xff == NoAuth }) {
            output.write(byteArrayOf(Version.toByte(), NoAcceptableMethods.toByte()))
            output.flush()
            throw IllegalArgumentException("SOCKS5 client offered no no-auth method.")
        }
        output.write(byteArrayOf(Version.toByte(), NoAuth.toByte()))
        output.flush()

        require(readByte(input) == Version) { "Invalid SOCKS5 request version." }
        val command = readByte(input)
        require(readByte(input) == 0x00) { "Invalid SOCKS5 reserved byte." }
        val host = when (readByte(input)) {
            AtypIpv4 -> InetAddress.getByAddress(ByteArray(4).also { readFully(input, it) }).hostAddress
            AtypDomain -> ByteArray(readByte(input)).also { readFully(input, it) }.toString(Charsets.UTF_8)
            AtypIpv6 -> InetAddress.getByAddress(ByteArray(16).also { readFully(input, it) }).hostAddress
            else -> {
                writeReply(output, 0x08)
                throw IllegalArgumentException("Unsupported SOCKS5 address type.")
            }
        }
        val port = (readByte(input) shl 8) or readByte(input)
        if (command != Connect) {
            writeReply(output, 0x07)
            throw IllegalArgumentException("Only SOCKS5 CONNECT is supported.")
        }
        HostEndpointValidator.errorFor(host)?.let { throw IllegalArgumentException("SOCKS5 target $it") }
        require(port in 1..65535) { "SOCKS5 target port must be between 1 and 65535." }
        return Socks5ConnectRequest(host.trim(), port)
    }

    fun writeSuccess(output: OutputStream) = writeReply(output, 0x00)

    fun writeFailure(output: OutputStream) = writeReply(output, 0x01)

    private fun writeReply(output: OutputStream, code: Int) {
        output.write(byteArrayOf(Version.toByte(), code.toByte(), 0x00, AtypIpv4.toByte(), 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun readByte(input: InputStream): Int {
        val value = input.read()
        if (value < 0) throw EOFException("Unexpected end of SOCKS5 request.")
        return value
    }

    private fun readFully(input: InputStream, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val read = input.read(bytes, offset, bytes.size - offset)
            if (read < 0) throw EOFException("Unexpected end of SOCKS5 request.")
            offset += read
        }
    }
}
