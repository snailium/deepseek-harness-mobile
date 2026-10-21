package com.labteto.dshmobile.core.wire

import com.labteto.dshmobile.core.wire.dto.RemoteEventFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Every socket a generation opens must be closed again, on every path that ends it.
 *
 * A phone left running for hours was observed holding a growing number of connections to its host.
 * The loop opens one socket per attempt, so the plausible shape of that leak is a path that ends a
 * generation without closing its mux. [ConnectionLoop.runLoop] covers the ordinary ones — a failed
 * attempt and a stream that ends both close explicitly — but a cancellation that unwinds through
 * the loop body leaves that line unreached. These tests pin the closed-socket count so such a path
 * cannot be added later without a failure.
 */
class ConnectionLoopTeardownTest {

    /** A [WsChannel] that records whether it was closed, and never touches a socket. */
    private class CountingChannel(
        private val ownSink: WsChannelSink,
        private val opens: Boolean,
        private val onSend: (CountingChannel, String) -> Unit = { _, _ -> },
    ) : WsChannel("http://stub/api/remote.mux", OkHttpClient(), ownSink) {
        var closed = false
            private set

        override fun start() {
            if (opens) ownSink.onOpen()
        }

        override fun send(text: String): Boolean {
            onSend(this, text)
            return true
        }

        override fun close() {
            closed = true
        }
    }

    private class Recorder : LoopSinks {
        val failures = CopyOnWriteArrayList<Pair<Int, GenerationFailure>>()
        override fun onEventFrame(frame: RemoteEventFrame) = Unit
        override fun onConnected(generation: HostGeneration) = Unit
        override fun onStateChange(state: ConnectionState) = Unit
        override fun onHandshakeStep(step: HandshakeStep) = Unit
        override fun onGenerationFailed(attempt: Int, failure: GenerationFailure) {
            failures.add(attempt to failure)
        }
    }

    private val readyFrame =
        """{"type":"ready","clientId":"client-1","host":{"home":"/home/demo"}}"""

    private fun streamIdOf(openMessage: String): String =
        Regex("\"streamId\":\"([^\"]+)\"").find(openMessage)!!.groupValues[1]

    /** Build a loop whose mux factory records every channel it hands out. */
    private fun recordingLoop(
        created: CopyOnWriteArrayList<CountingChannel>,
        opens: Boolean,
        answerReady: Boolean = false,
    ): ConnectionLoop = ConnectionLoop(
        muxFactory = {
            RemoteStreamMux { sink ->
                // The sink is captured before the channel exists so `send` can answer the open.
                var lateSink: WsChannelSink? = null
                CountingChannel(sink, opens) { channel, text ->
                    if (answerReady && text.contains("\"type\":\"open\"")) {
                        lateSink?.onMessage(
                            """{"type":"item","streamId":"${streamIdOf(text)}","value":$readyFrame}""",
                        )
                    }
                }.also {
                    lateSink = sink
                    created.add(it)
                }
            }
        },
        sinks = Recorder(),
        // No wait between attempts, so several generations accumulate quickly.
        config = LoopConfig(streamOpenTimeoutMs = 20, readyTimeoutMs = 40, delay = { }),
    )

    private suspend fun awaitChannels(created: List<*>, count: Int): Boolean =
        withTimeoutOrNull(5_000) {
            while (created.size < count) delay(5)
            true
        } ?: false

    @Test
    fun `stopping the loop closes a live generation's socket`() = runBlocking {
        val created = CopyOnWriteArrayList<CountingChannel>()
        val loop = recordingLoop(created, opens = true, answerReady = true)
        loop.start()
        assertTrue(awaitChannels(created, 1))
        delay(150)
        loop.stop()
        delay(200)

        assertTrue("stop() must close every socket it opened", created.all { it.closed })
    }

    @Test
    fun `stopping mid-handshake still closes the socket`() = runBlocking {
        val created = CopyOnWriteArrayList<CountingChannel>()
        // The socket opens but never answers `$events`, so the handshake stays suspended and the
        // generation is torn down by cancellation rather than by any in-body close.
        val loop = recordingLoop(created, opens = true, answerReady = false)
        loop.start()
        assertTrue(awaitChannels(created, 1))
        loop.stop()
        delay(200)

        assertTrue(
            "a socket opened but never used must still be closed " +
                "(${created.count { !it.closed }} left open)",
            created.all { it.closed },
        )
    }

    @Test
    fun `every failed attempt closes the socket it opened`() = runBlocking {
        val created = CopyOnWriteArrayList<CountingChannel>()
        val loop = recordingLoop(created, opens = false)
        loop.start()
        assertTrue(awaitChannels(created, 3))
        loop.stop()
        delay(200)

        assertEquals(
            "each attempt's socket must be closed",
            created.size,
            created.count { it.closed },
        )
    }

    @Test
    fun `stopping twice closes each socket once and does not throw`() = runBlocking {
        // `stop()` closes the generation itself and the loop body's `finally` closes it too, so a
        // generation can be closed from both paths. Idempotence is what makes that harmless; this
        // pins it so a future change cannot turn a double stop into an exception on the caller's
        // thread, which on Android would surface as a crash while backgrounding.
        val created = CopyOnWriteArrayList<CountingChannel>()
        val loop = recordingLoop(created, opens = false)
        loop.start()
        assertTrue(awaitChannels(created, 1))
        loop.stop()
        loop.stop()
        delay(200)

        assertEquals("each socket is closed exactly once", created.size, created.count { it.closed })
    }
}
