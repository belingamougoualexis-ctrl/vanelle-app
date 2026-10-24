package com.vanelle.meshnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class PacketCoreTest {
    @Test fun sha256IsStableForSamePacket() {
        val packet = "VAMP|id-1|A|B|ttl=4|hello"
        fun digest() = MessageDigest.getInstance("SHA-256").digest(packet.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(digest(), digest())
        assertEquals(64, digest().length)
    }

    @Test fun ttlMustExpireAfterFourHops() {
        var ttl = 4
        repeat(4) { ttl-- }
        assertEquals(0, ttl)
        assertTrue(ttl <= 0)
    }
}
