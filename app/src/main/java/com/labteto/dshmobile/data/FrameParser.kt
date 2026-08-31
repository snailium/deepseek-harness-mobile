package com.labteto.dshmobile.data

import com.labteto.dshmobile.core.session.QueueItem
import com.labteto.dshmobile.core.session.SessionEventEnvelope
import com.labteto.dshmobile.core.wire.decodeFromJsonElement
import com.labteto.dshmobile.core.wire.encodeToJsonElement
import com.labteto.dshmobile.core.wire.dto.ContentBlock
import com.labteto.dshmobile.core.wire.dto.MessageData
import com.labteto.dshmobile.core.wire.dto.QueuedInboxItem
import com.labteto.dshmobile.core.wire.dto.SessionWireEvent
import com.labteto.dshmobile.core.wire.dto.SessionEvent
import com.labteto.dshmobile.core.wire.dto.SessionEventSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Pure, unit-testable decode helpers shared by [SessionStore] and the notification observer.
 *
 * Every function is lenient: malformed payloads yield `null` (or an empty field) instead of
 * throwing, because stream frames are merge-extensible and may drift.
 */

/**
 * Convert one wire event from a journal record into the raw-envelope shape the fold consumes.
 *
 * The journal's event form is already flat — type, seq, time, raw `data` — so unlike
 * [sessionEventToEnvelope] this needs no round-trip through a typed DTO. `surfaceOp` is
 * stringified for the envelope exactly as the typed path does it.
 */
fun wireEventToEnvelope(event: SessionWireEvent): SessionEventEnvelope = SessionEventEnvelope(
    type = event.type,
    seq = event.seq.toLong(),
    time = event.time,
    data = event.data,
    surfaceOp = event.surfaceOp?.let { raw ->
        (raw as? JsonPrimitive)?.contentOrNull ?: raw.toString()
    },
)

/**
 * Convert a typed [SessionEvent] into the raw-envelope shape the fold consumes. `data` is the
 * raw JSON of the event's payload (re-derived from the serializer so it stays a [JsonElement]
 * exactly as the fold expects); `surfaceOp` is stringified for the envelope.
 */
fun sessionEventToEnvelope(event: SessionEvent): SessionEventEnvelope {
    val json = encodeToJsonElement(SessionEventSerializer, event).jsonObject
    val data = json["data"] ?: JsonObject(emptyMap())
    val surfaceOp = json["surfaceOp"]?.let { raw ->
        (raw as? JsonPrimitive)?.contentOrNull ?: raw.toString()
    }
    return SessionEventEnvelope(
        type = event.type,
        seq = event.seq.toLong(),
        time = event.time,
        data = data,
        surfaceOp = surfaceOp,
    )
}

/**
 * Fast path: build the fold envelope straight from the raw wire `session/event` frame, skipping
 * the decode-to-typed-then-re-encode round trip. The frame payload already carries `event` as raw
 * JSON; we read the envelope fields (`type`, `seq`, `time`) off it directly and hand the raw
 * `data` object to the fold exactly as [sessionEventToEnvelope] would have produced it.
 *
 * Returns null when the frame does not look like a well-formed `session/event` payload — the
 * caller then falls back to the typed path. This is purely an optimization: the fold consumes
 * [SessionEventEnvelope] either way, so correctness is unchanged.
 */
fun rawSessionEventEnvelope(payload: JsonObject): SessionEventEnvelope? = runCatching {
    val event = payload["event"] as? JsonObject ?: return@runCatching null
    val type = event["type"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
    val seq = event["seq"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@runCatching null
    val time = event["time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
    SessionEventEnvelope(
        type = type,
        seq = seq,
        time = time,
        data = event["data"] as? JsonObject ?: JsonObject(emptyMap()),
    )
}.getOrNull()

/** Decode a raw `session/event` event object into a [SessionEventEnvelope], or null on drift. */
fun parseSessionEventEnvelope(eventJson: JsonElement): SessionEventEnvelope? =
    runCatching { sessionEventToEnvelope(decodeFromJsonElement(SessionEventSerializer, eventJson)) }
        .getOrNull()

/** Convert one authoritative queue snapshot item into the renderer-facing [QueueItem]. */
fun queuedInboxItemToQueueItem(item: QueuedInboxItem): QueueItem {
    val preview = item.message.content
        .filterIsInstance<ContentBlock.Text>()
        .firstOrNull()
        ?.text
        ?.take(120)
        .orEmpty()
    val content = encodeToJsonElement(MessageData.serializer(), item.message)
    return QueueItem(
        id = item.id,
        placement = item.placement,
        previewText = preview,
        content = content,
    )
}
