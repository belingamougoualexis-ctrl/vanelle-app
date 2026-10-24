package com.vanelle.meshnet

/**
 * Bounded store-and-forward core for Vanelle MeshNet.
 *
 * This class is deliberately transport-agnostic. A radio transport must call [accept],
 * consult [nextHopFor], and send packets to the returned peer. It does not by itself
 * create multi-hop connectivity or persist data across process/device restarts.
 */
class MeshRouterCore(
    private val localNodeId: String,
    private val maxQueuedPackets: Int = 512,
    private val maxQueuedBytes: Int = 1_048_576,
    private val packetLifetimeMillis: Long = 24 * 60 * 60 * 1000L,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    data class Route(val destination: String, val nextHop: String, val metric: Int, val expiresAtMillis: Long)
    data class QueuedPacket(val packet: VampPacket, val storedAtMillis: Long, val encodedBytes: Int)

    enum class AcceptResult { STORED, DUPLICATE, EXPIRED, INVALID, QUEUE_FULL, DELIVER_LOCAL }

    private val deduplicator = PacketDeduplicator()
    private val routes = LinkedHashMap<String, Route>()
    private val queue = LinkedHashMap<String, QueuedPacket>()
    private var queuedBytes = 0

    init {
        require(localNodeId.isNotBlank() && localNodeId.length <= 80)
        require(maxQueuedPackets > 0 && maxQueuedBytes > 0 && packetLifetimeMillis > 0)
    }

    @Synchronized
    fun updateRoute(destination: String, nextHop: String, metric: Int, validForMillis: Long = 60_000L) {
        require(destination.isNotBlank() && destination.length <= 80)
        require(nextHop.isNotBlank() && nextHop.length <= 80)
        require(metric >= 0 && validForMillis > 0)
        if (nextHop == localNodeId || destination == localNodeId) return
        val current = routes[destination]
        if (current == null || current.expiresAtMillis <= nowMillis() || metric <= current.metric) {
            routes[destination] = Route(destination, nextHop, metric, nowMillis() + validForMillis)
        }
    }

    @Synchronized
    fun nextHopFor(destination: String): String? {
        pruneExpired()
        return routes[destination]?.nextHop
    }

    @Synchronized
    fun accept(packet: VampPacket): AcceptResult {
        pruneExpired()
        if (packet.id.isBlank() || packet.source.isBlank() || packet.destination.isBlank() ||
            packet.ttl !in 0..32 || packet.payload.toByteArray(Charsets.UTF_8).size > VampPacket.MAX_PAYLOAD_BYTES
        ) return AcceptResult.INVALID
        if (nowMillis() - packet.createdAtMillis > packetLifetimeMillis || packet.createdAtMillis > nowMillis() + 60_000L) {
            return AcceptResult.EXPIRED
        }
        if (packet.destination == localNodeId) return AcceptResult.DELIVER_LOCAL
        if (!deduplicator.firstTime(packet.id)) return AcceptResult.DUPLICATE
        if (packet.ttl <= 1) return AcceptResult.EXPIRED

        val forwarded = packet.forwarded() ?: return AcceptResult.EXPIRED
        val bytes = forwarded.encode().toByteArray(Charsets.UTF_8).size
        if (queue.size >= maxQueuedPackets || queuedBytes + bytes > maxQueuedBytes) return AcceptResult.QUEUE_FULL
        queue[forwarded.id] = QueuedPacket(forwarded, nowMillis(), bytes)
        queuedBytes += bytes
        return AcceptResult.STORED
    }

    /** Returns queued packets for which [peerId] is currently the selected next hop. */
    @Synchronized
    fun packetsForPeer(peerId: String, limit: Int = 32): List<VampPacket> {
        require(limit > 0)
        pruneExpired()
        return queue.values.asSequence()
            .filter { routes[it.packet.destination]?.nextHop == peerId }
            .take(limit)
            .map { it.packet }
            .toList()
    }

    /** Remove a packet only after the transport confirms delivery to the next hop. */
    @Synchronized
    fun acknowledge(packetId: String, peerId: String): Boolean {
        val item = queue[packetId] ?: return false
        if (routes[item.packet.destination]?.nextHop != peerId) return false
        queue.remove(packetId)
        queuedBytes = (queuedBytes - item.encodedBytes).coerceAtLeast(0)
        return true
    }

    @Synchronized
    fun queuedCount(): Int {
        pruneExpired()
        return queue.size
    }

    @Synchronized
    fun queuedBytes(): Int {
        pruneExpired()
        return queuedBytes
    }

    @Synchronized
    fun removeRoute(destination: String) { routes.remove(destination) }

    @Synchronized
    private fun pruneExpired() {
        val now = nowMillis()
        routes.entries.removeAll { it.value.expiresAtMillis <= now }
        val expired = queue.filterValues { now - it.storedAtMillis > packetLifetimeMillis }.keys.toList()
        expired.forEach { id -> queue.remove(id)?.let { queuedBytes = (queuedBytes - it.encodedBytes).coerceAtLeast(0) } }
    }
}
