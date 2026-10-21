package com.labteto.dshmobile.core.wire

import java.util.concurrent.atomic.AtomicLong

/**
 * Decides which of several in-flight rebuilds is allowed to publish.
 *
 * A connection manager rebuilds its transport off the caller's thread, so two rebuilds can overlap:
 * a repeated tap on the reconnect button, or a periodic keep-alive firing while a manual retry is
 * still building. If both publish, the one that lands second wins the field and the one that lands
 * first is unreachable — but it is still running, still holding a socket, and nothing will ever
 * stop it. That is a leak of exactly one connection per overlapping call, which is invisible in a
 * test that calls the rebuild once and obvious on a phone that has retried all day.
 *
 * The rule this encodes is narrow on purpose: a rebuild claims a ticket before it starts work and
 * may only publish if its ticket is still the newest. Anything else — the field-level locking, the
 * ordering of the stop, the transport construction — stays with the caller.
 */
class GenerationTicket {

    private val issued = AtomicLong(0)

    /**
     * Claim the right to publish, invalidating every ticket handed out before it.
     *
     * Returns the ticket to hand back to [mayPublish].
     */
    fun claim(): Long = issued.incrementAndGet()

    /**
     * Whether a rebuild holding [ticket] is still the newest claimant.
     *
     * False after a later [claim] or after [invalidate], which is the answer that makes a stale
     * rebuild stop what it built instead of publishing it.
     */
    fun mayPublish(ticket: Long): Boolean = issued.get() == ticket

    /**
     * Abandon every ticket in flight.
     *
     * Used when the connection is dropped or pointed at a different host: a rebuild that started
     * against the old host must not install itself over the new decision.
     */
    fun invalidate() {
        issued.incrementAndGet()
    }
}
