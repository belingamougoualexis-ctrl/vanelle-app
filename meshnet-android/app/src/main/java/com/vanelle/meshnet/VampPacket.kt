package com.vanelle.meshnet

import java.util.Base64
import java.util.LinkedHashMap
import java.util.UUID

/** Versioned VAMP envelope used by the local protocol. It is not a radio modulation. */
data class VampPacket(
    val id: String,
    val source: String,
    val destination: String,
    val ttl: Int,
    val createdAtMillis: Long,
    val payload: String
) {
    fun encode(): String {
        require(id.length <= 80 && source.length <= 80 && destination.length <= 80)
        require(ttl in 0..32 && payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES)
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray(Charsets.UTF_8))
        return listOf(VERSION, id, source, destination, ttl.toString(), createdAtMillis.toString(), body).joinToString("|")
    }

    /** Returns null when this packet must expire instead of being forwarded. */
    fun forwarded(): VampPacket? = if (ttl <= 1) null else copy(ttl = ttl - 1)

    companion object {
        const val VERSION = "VAMP1"
        const val MAX_PAYLOAD_BYTES = 4096

        fun create(source: String, destination: String, payload: String, ttl: Int = 8): VampPacket =
            VampPacket(UUID.randomUUID().toString(), source, destination, ttl, System.currentTimeMillis(), payload)

        fun decode(encoded: String): VampPacket? {
            return try {
            val fields = encoded.split("|", limit = 7)
            if (fields.size != 7 || fields[0] != VERSION) return null
            val ttl = fields[4].toIntOrNull() ?: return null
            val timestamp = fields[5].toLongOrNull() ?: return null
            val payloadBytes = Base64.getUrlDecoder().decode(fields[6])
            if (ttl !in 0..32 || payloadBytes.size > MAX_PAYLOAD_BYTES) return null
            VampPacket(fields[1], fields[2], fields[3], ttl, timestamp, payloadBytes.toString(Charsets.UTF_8))
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Bounded in-memory duplicate cache to stop simple forwarding loops. */
class PacketDeduplicator(private val capacity: Int = 2048) {
    private val seen = LinkedHashMap<String, Unit>(capacity.coerceAtLeast(1), 0.75f, false)
    @Synchronized fun firstTime(packetId: String): Boolean {
        if (seen.containsKey(packetId)) return false
        seen[packetId] = Unit
        while (seen.size > capacity.coerceAtLeast(1)) {
            val oldest = seen.keys.iterator()
            if (oldest.hasNext()) { oldest.next(); oldest.remove() } else break
        }
        return true
    }
}
