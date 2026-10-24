package com.vanelle.meshnet

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * One-hop local TCP transport carried by an Android Wi-Fi Direct group.
 * It does not provide Internet access, multi-hop routing, or encryption by itself.
 */
class MeshTransport {
    companion object { const val PORT = 47474 }
    private val executor = Executors.newCachedThreadPool()
    @Volatile private var server: ServerSocket? = null
    @Volatile private var socket: Socket? = null
    @Volatile private var writer: PrintWriter? = null
    @Volatile private var closed = false

    @Synchronized
    fun startGroupOwner(onMessage: (String) -> Unit, onStatus: (String) -> Unit) {
        closed = false
        executor.execute {
            try {
                val ss = ServerSocket(PORT)
                server = ss
                onStatus("En attente d’un pair sur le réseau local")
                while (!closed) {
                    val peer = ss.accept()
                    attach(peer, onMessage, onStatus)
                }
            } catch (e: Exception) {
                if (!closed) onStatus("Transport local indisponible : ${e.javaClass.simpleName}")
            }
        }
    }

    fun connect(host: String, onMessage: (String) -> Unit, onStatus: (String) -> Unit) {
        closed = false
        executor.execute {
            try {
                val peer = Socket()
                peer.connect(InetSocketAddress(host, PORT), 8000)
                attach(peer, onMessage, onStatus)
            } catch (e: Exception) {
                if (!closed) onStatus("Connexion de messagerie impossible : ${e.javaClass.simpleName}")
            }
        }
    }

    @Synchronized
    private fun attach(peer: Socket, onMessage: (String) -> Unit, onStatus: (String) -> Unit) {
        socket?.takeIf { it !== peer }?.close()
        socket = peer
        writer = PrintWriter(OutputStreamWriter(peer.getOutputStream(), Charsets.UTF_8), true)
        onStatus("Transport de messages local connecté")
        executor.execute {
            try {
                val reader = BufferedReader(InputStreamReader(peer.getInputStream(), Charsets.UTF_8))
                while (!closed && !peer.isClosed) {
                    val line = reader.readLine() ?: break
                    if (line.length <= 8192) onMessage(line)
                }
            } catch (_: Exception) {
                // Connection closure is surfaced below.
            } finally {
                if (!closed) onStatus("Connexion de messagerie interrompue")
            }
        }
    }

    @Synchronized
    fun send(message: String): Boolean {
        if (message.isBlank() || message.length > 4096) return false
        val out = writer ?: return false
        out.println(message.replace("\n", " ").replace("\r", " "))
        return !out.checkError()
    }

    @Synchronized
    fun close() {
        closed = true
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { server?.close() } catch (_: Exception) {}
        writer = null
        socket = null
        server = null
    }

    fun shutdown() {
        close()
        executor.shutdownNow()
    }
}
