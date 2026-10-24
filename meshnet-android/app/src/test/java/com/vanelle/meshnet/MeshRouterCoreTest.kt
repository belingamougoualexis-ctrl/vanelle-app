package com.vanelle.meshnet

import org.junit.Assert.*
import org.junit.Test

class MeshRouterCoreTest {
    @Test fun forwardsPacketWithReducedTtlWhenRouteExists() {
        var now = 10_000L
        val router = MeshRouterCore("node-b", nowMillis = { now })
        router.updateRoute("node-c", "node-c-direct", metric = 1)
        val packet = VampPacket("p1", "node-a", "node-c", 4, now, "hello")

        assertEquals(MeshRouterCore.AcceptResult.STORED, router.accept(packet))
        val queued = router.packetsForPeer("node-c-direct")
        assertEquals(1, queued.size)
        assertEquals(3, queued.single().ttl)
        assertTrue(router.acknowledge("p1", "node-c-direct"))
        assertEquals(0, router.queuedCount())
    }

    @Test fun duplicatePacketsAreNotQueuedTwice() {
        val router = MeshRouterCore("node-b")
        val packet = VampPacket("same", "node-a", "node-c", 4, System.currentTimeMillis(), "payload")
        assertEquals(MeshRouterCore.AcceptResult.STORED, router.accept(packet))
        assertEquals(MeshRouterCore.AcceptResult.DUPLICATE, router.accept(packet))
        assertEquals(1, router.queuedCount())
    }

    @Test fun expiresOldPacketsAndRoutes() {
        var now = 100_000L
        val router = MeshRouterCore("node-b", packetLifetimeMillis = 1000L, nowMillis = { now })
        router.updateRoute("node-c", "node-c-direct", metric = 1, validForMillis = 500L)
        val packet = VampPacket("old", "node-a", "node-c", 4, now, "payload")
        assertEquals(MeshRouterCore.AcceptResult.STORED, router.accept(packet))
        now += 1500L
        assertNull(router.nextHopFor("node-c"))
        assertEquals(0, router.queuedCount())
    }

    @Test fun rejectsExpiredOrTooShortTtlPackets() {
        val now = System.currentTimeMillis()
        val router = MeshRouterCore("node-b", packetLifetimeMillis = 1000L)
        assertEquals(MeshRouterCore.AcceptResult.EXPIRED,
            router.accept(VampPacket("old", "a", "c", 4, now - 5000L, "x")))
        assertEquals(MeshRouterCore.AcceptResult.EXPIRED,
            router.accept(VampPacket("ttl", "a", "c", 1, now, "x")))
    }

    @Test fun enforcesQueueCapacityAndAcknowledgementRoute() {
        val router = MeshRouterCore("node-b", maxQueuedPackets = 1)
        router.updateRoute("c", "peer-c", metric = 1)
        assertEquals(MeshRouterCore.AcceptResult.STORED,
            router.accept(VampPacket("p1", "a", "c", 5, System.currentTimeMillis(), "first")))
        assertFalse(router.acknowledge("p1", "wrong-peer"))
        assertEquals(MeshRouterCore.AcceptResult.QUEUE_FULL,
            router.accept(VampPacket("p2", "a", "c", 5, System.currentTimeMillis(), "second")))
    }

    @Test fun localDestinationIsDeliveredWithoutQueueing() {
        val router = MeshRouterCore("node-b")
        assertEquals(MeshRouterCore.AcceptResult.DELIVER_LOCAL,
            router.accept(VampPacket("local", "a", "node-b", 4, System.currentTimeMillis(), "hi")))
        assertEquals(0, router.queuedCount())
    }
}
