package com.labteto.dshmobile.core.wire

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only the newest in-flight rebuild may publish a connection.
 *
 * The case these pin is the one that leaked sockets: a rebuild that loses the race must be told it
 * lost, so the caller can stop what it built rather than installing it over a loop nothing will
 * ever close. A ticket kept merely for logging, or compared against the wrong counter, passes every
 * single-call test and leaks one connection per overlap in the field.
 */
class GenerationTicketTest {

    @Test
    fun `the newest claim publishes, an older one does not`() {
        val tickets = GenerationTicket()
        val first = tickets.claim()
        val second = tickets.claim()

        assertFalse("the superseded rebuild must not publish", tickets.mayPublish(first))
        assertTrue("the newest rebuild must publish", tickets.mayPublish(second))
    }

    @Test
    fun `a claim publishes while nothing newer has been claimed`() {
        val tickets = GenerationTicket()
        val only = tickets.claim()

        assertTrue(tickets.mayPublish(only))
    }

    @Test
    fun `invalidate abandons a rebuild that would otherwise publish`() {
        val tickets = GenerationTicket()
        val inFlight = tickets.claim()
        assertTrue("sanity: it could publish before the drop", tickets.mayPublish(inFlight))

        // The user dropped the connection, or pointed the app at another host, while the rebuild
        // was still building its transport.
        tickets.invalidate()

        assertFalse("a rebuild started before the drop must not install itself", tickets.mayPublish(inFlight))
    }

    @Test
    fun `overlapping rebuilds leave exactly one publisher`() {
        // The leak this guards against: N rebuilds in flight must produce one connection, not N.
        // Each of these models a rebuild that claimed a ticket and is now deciding whether to
        // install the loop it built.
        val tickets = GenerationTicket()
        val inFlight = (1..50).map { tickets.claim() }

        val published = inFlight.count { tickets.mayPublish(it) }

        assertEquals("exactly one rebuild may publish", 1, published)
    }

    @Test
    fun `a stale rebuild can be identified and stopped instead of published`() {
        val tickets = GenerationTicket()
        val stale = tickets.claim()
        tickets.claim() // a newer rebuild begins before the first one finishes building

        // This is the branch the manager takes on a lost race: stop what was built. If the ticket
        // were wired the wrong way round, the stale rebuild would publish and the fresh one would
        // be the one stopped — a connection that instantly dies, with nothing left to retry it.
        assertFalse(tickets.mayPublish(stale))
    }
}
