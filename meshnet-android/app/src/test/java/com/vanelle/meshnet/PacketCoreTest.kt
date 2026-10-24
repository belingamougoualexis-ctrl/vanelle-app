package com.vanelle.meshnet

import org.junit.Assert.*
import org.junit.Test

class PacketCoreTest {
    @Test fun packetRoundTripsUtf8Payload() {
        val packet = VampPacket("packet-1", "node-a", "node-c", 4, 123L, "Salut 👋 réseau")
        val decoded = VampPacket.decode(packet.encode())
        assertNotNull(decoded)
        assertEquals(packet, decoded)
    }

    @Test fun forwardingDecrementsTtlAndExpiresAtOne() {
        val packet = VampPacket("p", "a", "c", 4, 123L, "data")
        assertEquals(3, packet.forwarded()?.ttl)
        assertNull(packet.copy(ttl = 1).forwarded())
    }

    @Test fun duplicatePacketsAreRejected() {
        val cache = PacketDeduplicator(2)
        assertTrue(cache.firstTime("a"))
        assertFalse(cache.firstTime("a"))
        assertTrue(cache.firstTime("b"))
    }

    @Test fun malformedPacketsAreRejected() {
        assertNull(VampPacket.decode("not-a-vamp-packet"))
        assertNull(VampPacket.decode("VAMP1|id|source|dest|999|123|abc"))
    }

    @Test fun deduplicatorRemainsBounded() {
        val cache = PacketDeduplicator(2)
        cache.firstTime("a")
        cache.firstTime("b")
        cache.firstTime("c")
        assertTrue(cache.firstTime("a"))
    }
}
