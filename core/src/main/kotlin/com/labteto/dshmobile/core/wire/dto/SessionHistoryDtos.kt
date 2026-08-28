@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.labteto.dshmobile.core.wire.dto

import com.labteto.dshmobile.core.wire.decodeFromJsonElement
import com.labteto.dshmobile.core.wire.encodeToJsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Session journal wire types, ported from `packages/api/session-controller/src/types.ts`
 * (v0.1.2-alpha.1).
 *
 * These replace `session.history`. Harness 0.1.2 splits reading a transcript into a live
 * `session/follow` stream and a `session/page` unary read, and the two are not independent: a
 * page must be pinned to the follow generation's opening cursor. See [SessionPageRequest].
 */

/**
 * Durable identity selecting an ordinary session or one direct subagent child.
 *
 * One address protocol now covers both, which is why `subagents/history` no longer exists. A
 * subagent address names the parent as well as the child because a cold host read verifies
 * durable ownership rather than authorizing access from the child id alone.
 */
@Serializable(with = SessionAddressSerializer::class)
sealed class SessionAddress {
    /** The wire discriminant. */
    abstract val kind: String

    /** An ordinary session. */
    @Serializable
    data class Session(
        @SerialName("kind") override val kind: String = "session",
        @SerialName("sessionId") val sessionId: String,
    ) : SessionAddress()

    /** One direct subagent child, addressed through the parent whose authority is claimed. */
    @Serializable
    data class Subagent(
        @SerialName("kind") override val kind: String = "subagent",
        @SerialName("parentSessionId") val parentSessionId: String,
        @SerialName("childSessionId") val childSessionId: String,
        /** 'one-shot' | 'continuable'. */
        @SerialName("mode") val mode: String,
    ) : SessionAddress()
}

/** Custom `kind`-dispatching serializer for [SessionAddress]. */
object SessionAddressSerializer : KSerializer<SessionAddress> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SessionAddress") {
        element("kind", buildSerialDescriptor("kotlin.String", PrimitiveKind.STRING))
    }

    override fun serialize(encoder: Encoder, value: SessionAddress) {
        val json = when (value) {
            is SessionAddress.Session -> encodeToJsonElement(SessionAddress.Session.serializer(), value)
            is SessionAddress.Subagent -> encodeToJsonElement(SessionAddress.Subagent.serializer(), value)
        }
        (encoder as JsonEncoder).encodeJsonElement(json)
    }

    override fun deserialize(decoder: Decoder): SessionAddress {
        val json = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return when (val kind = json["kind"]?.jsonPrimitive?.contentOrNull ?: "") {
            "session" -> decodeFromJsonElement(SessionAddress.Session.serializer(), json)
            "subagent" -> decodeFromJsonElement(SessionAddress.Subagent.serializer(), json)
            else -> throw IllegalArgumentException("unknown session address kind \"$kind\"")
        }
    }
}

/**
 * One session event in wire form.
 *
 * `data` stays opaque here; the fold layer owns event-kind recognition, and an unknown `type`
 * has always been something this client passes through rather than fails on.
 */
@Serializable
data class SessionWireEvent(
    @SerialName("type") val type: String,
    @SerialName("seq") val seq: Int,
    @SerialName("time") val time: Long,
    @SerialName("data") val data: JsonElement = JsonObject(emptyMap()),
    /** Present when this event merges earlier ones; carries their sequence numbers. */
    @SerialName("sourceEventSeqs") val sourceEventSeqs: List<Int>? = null,
    /** Surface-mutation intent for events that revise an earlier rendered block. */
    @SerialName("surfaceOp") val surfaceOp: JsonElement? = null,
)

/**
 * One history record: either a raw event or a packed run of consecutive assistant deltas.
 *
 * The packed variant is new in 0.1.2 and is the reason history pages are readable at all on a
 * long transcript — upstream measured one 416,756-event tail as 696 records. It is lossless: a
 * run carries the original fragment and timestamp-gap arrays rather than a summary.
 *
 * A run is *not* a durable event. It exists only in history transport; live follow frames stay
 * scalar, so the same content arrives one way on reconnect and another way while streaming.
 */
@Serializable(with = SessionHistoryRecordSerializer::class)
sealed class SessionHistoryRecord {
    /** The wire discriminant: `event` or `chunks`. */
    abstract val type: String

    /** The inner event-shaped value; aligned `type`/`seq`/`time`/`data` in both variants. */
    abstract val event: SessionWireEvent

    /** One ordinary logical event. */
    @Serializable
    data class Event(
        @SerialName("type") override val type: String = "event",
        @SerialName("event") override val event: SessionWireEvent,
    ) : SessionHistoryRecord()

    /**
     * One lossless run of consecutive same-block assistant delta events.
     *
     * The inner `event.type` is `chunkrow/text-chunks`, `chunkrow/reasoning-chunks`, or
     * `chunkrow/tool-call-chunks`; `seq` and `time` identify the run's *first* member.
     */
    @Serializable
    data class Chunks(
        @SerialName("type") override val type: String = "chunks",
        @SerialName("event") override val event: SessionWireEvent,
    ) : SessionHistoryRecord()
}

/** Custom `type`-dispatching serializer for [SessionHistoryRecord]. */
object SessionHistoryRecordSerializer : KSerializer<SessionHistoryRecord> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SessionHistoryRecord") {
        element("type", buildSerialDescriptor("kotlin.String", PrimitiveKind.STRING))
    }

    override fun serialize(encoder: Encoder, value: SessionHistoryRecord) {
        val json = when (value) {
            is SessionHistoryRecord.Event ->
                encodeToJsonElement(SessionHistoryRecord.Event.serializer(), value)
            is SessionHistoryRecord.Chunks ->
                encodeToJsonElement(SessionHistoryRecord.Chunks.serializer(), value)
        }
        (encoder as JsonEncoder).encodeJsonElement(json)
    }

    override fun deserialize(decoder: Decoder): SessionHistoryRecord {
        val json = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return when (val type = json["type"]?.jsonPrimitive?.contentOrNull ?: "") {
            "chunks" -> decodeFromJsonElement(SessionHistoryRecord.Chunks.serializer(), json)
            // An unrecognised record class is read as an ordinary event rather than dropped: the
            // outer discriminator only selects how to expand the inner value, and the inner value
            // is shaped the same either way. Dropping it would open a sequence gap and send the
            // journal into a repair it cannot resolve.
            else -> decodeFromJsonElement(SessionHistoryRecord.Event.serializer(), json)
        }
    }
}

/**
 * Named arguments of `session/page`.
 *
 * [throughSeq] is mandatory and comes from the matching `session/follow` generation's opening
 * cursor: it fixes the read at the same log cut, which is what lets a page and the live tail be
 * joined without a gap. `-1` denotes an empty log. [beforeSeq] selects an *older* page before
 * that cut and cannot stand in for the cursor.
 */
@Serializable
data class SessionPageRequest(
    @SerialName("address") val address: SessionAddress,
    @SerialName("throughSeq") val throughSeq: Int,
    /** Absent for the tail page, which must end exactly at [throughSeq]. */
    @SerialName("beforeSeq") val beforeSeq: Int? = null,
    /** Caps user/assistant message count without dropping chunks, tools or state between them. */
    @SerialName("maxMessages") val maxMessages: Int? = null,
)

/** One contiguous backwards page of a session log. */
@Serializable
data class SessionPage(
    @SerialName("records") val records: List<SessionHistoryRecord> = emptyList(),
    @SerialName("hasMore") val hasMore: Boolean = false,
)

/** Named arguments of the `session/follow` stream. */
@Serializable
data class SessionFollowRequest(
    @SerialName("address") val address: SessionAddress,
    @SerialName("maxMessages") val maxMessages: Int? = null,
)

/**
 * One frame of the `session/follow` stream.
 *
 * Every generation opens with exactly one [Snapshot] — including after a reconnect, which sends
 * a complete replacement rather than a delta. There is no `afterSeq`: the protocol has no way to
 * resume mid-stream, and the client repairs by paging instead.
 */
@Serializable(with = SessionFollowFrameSerializer::class)
sealed class SessionFollowFrame {
    /** The complete opening window. */
    @Serializable
    data class Snapshot(
        @SerialName("type") val type: String = "snapshot",
        @SerialName("header") val header: JsonElement = JsonObject(emptyMap()),
        /** The log cut this generation opened at; pass it as `throughSeq` when paging. */
        @SerialName("cursor") val cursor: Int,
        @SerialName("records") val records: List<SessionHistoryRecord> = emptyList(),
        @SerialName("hasMore") val hasMore: Boolean = false,
        /** Projection baseline no later than [cursor]; merge by watermark against live updates. */
        @SerialName("projections") val projections: JsonObject = JsonObject(emptyMap()),
    ) : SessionFollowFrame()

    /** One live event appended after the opening cursor. Always scalar — never packed. */
    data class Entry(val record: SessionHistoryRecord) : SessionFollowFrame()
}

/**
 * Custom serializer for [SessionFollowFrame].
 *
 * The union is not uniformly tagged: the opening frame carries `type: "snapshot"`, and every
 * later item is a bare history record whose own `type` is `event` or `chunks`. Discriminating on
 * `snapshot` specifically — rather than assuming a closed tag set — is what keeps a future
 * record class from being mistaken for an opening frame.
 */
object SessionFollowFrameSerializer : KSerializer<SessionFollowFrame> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SessionFollowFrame") {
        element("type", buildSerialDescriptor("kotlin.String", PrimitiveKind.STRING))
    }

    override fun serialize(encoder: Encoder, value: SessionFollowFrame) {
        val json = when (value) {
            is SessionFollowFrame.Snapshot ->
                encodeToJsonElement(SessionFollowFrame.Snapshot.serializer(), value)
            is SessionFollowFrame.Entry ->
                encodeToJsonElement(SessionHistoryRecordSerializer, value.record)
        }
        (encoder as JsonEncoder).encodeJsonElement(json)
    }

    override fun deserialize(decoder: Decoder): SessionFollowFrame {
        val json = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return if (json["type"]?.jsonPrimitive?.contentOrNull == "snapshot") {
            decodeFromJsonElement(SessionFollowFrame.Snapshot.serializer(), json)
        } else {
            SessionFollowFrame.Entry(decodeFromJsonElement(SessionHistoryRecordSerializer, json))
        }
    }
}

/**
 * One frame of the host-wide `session/control` stream.
 *
 * One stream serves every live session, so a client can watch transient state without opening a
 * journal per transcript. Each generation emits exactly one [Baseline] first; queue and jobs
 * frames are complete replacement values applied last-wins, never deltas.
 */
@Serializable(with = SessionControlFrameSerializer::class)
sealed class SessionControlFrame {
    /** The wire discriminant. */
    abstract val type: String

    /** The complete opening state for every live session. */
    @Serializable
    data class Baseline(
        @SerialName("type") override val type: String = "baseline",
        @SerialName("value") val value: SessionControlBaseline,
    ) : SessionControlFrame()

    /** The authoritative pending queue for one session. */
    @Serializable
    data class Queue(
        @SerialName("type") override val type: String = "queue",
        @SerialName("sessionId") val sessionId: String,
        @SerialName("items") val items: List<QueuedInboxItem> = emptyList(),
    ) : SessionControlFrame()

    /** The complete background-job set for one session. */
    @Serializable
    data class Jobs(
        @SerialName("type") override val type: String = "jobs",
        @SerialName("sessionId") val sessionId: String,
        @SerialName("jobs") val jobs: List<JobView> = emptyList(),
    ) : SessionControlFrame()

    /** One projection unit's finished value and its durable watermark. */
    @Serializable
    data class Projection(
        @SerialName("type") override val type: String = "projection",
        @SerialName("sessionId") val sessionId: String,
        @SerialName("key") val key: String,
        @SerialName("value") val value: JsonElement,
        @SerialName("seq") val seq: Int,
    ) : SessionControlFrame()

    /** A frame of an unknown `type`, preserved verbatim. */
    data class Unknown(
        override val type: String,
        val raw: JsonElement,
    ) : SessionControlFrame()
}

/** The complete live-control baseline emitted once per control-stream generation. */
@Serializable
data class SessionControlBaseline(
    @SerialName("queues") val queues: Map<String, List<QueuedInboxItem>> = emptyMap(),
    @SerialName("jobs") val jobs: Map<String, List<JobView>> = emptyMap(),
    @SerialName("projections") val projections: Map<String, JsonObject> = emptyMap(),
)

/** Custom `type`-dispatching serializer for [SessionControlFrame]. */
object SessionControlFrameSerializer : KSerializer<SessionControlFrame> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SessionControlFrame") {
        element("type", buildSerialDescriptor("kotlin.String", PrimitiveKind.STRING))
    }

    override fun serialize(encoder: Encoder, value: SessionControlFrame) {
        val json: JsonElement = when (value) {
            is SessionControlFrame.Baseline ->
                encodeToJsonElement(SessionControlFrame.Baseline.serializer(), value)
            is SessionControlFrame.Queue ->
                encodeToJsonElement(SessionControlFrame.Queue.serializer(), value)
            is SessionControlFrame.Jobs ->
                encodeToJsonElement(SessionControlFrame.Jobs.serializer(), value)
            is SessionControlFrame.Projection ->
                encodeToJsonElement(SessionControlFrame.Projection.serializer(), value)
            is SessionControlFrame.Unknown -> value.raw
        }
        (encoder as JsonEncoder).encodeJsonElement(json)
    }

    override fun deserialize(decoder: Decoder): SessionControlFrame {
        val json = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return when (val type = json["type"]?.jsonPrimitive?.contentOrNull ?: "") {
            "baseline" -> decodeFromJsonElement(SessionControlFrame.Baseline.serializer(), json)
            "queue" -> decodeFromJsonElement(SessionControlFrame.Queue.serializer(), json)
            "jobs" -> decodeFromJsonElement(SessionControlFrame.Jobs.serializer(), json)
            "projection" -> decodeFromJsonElement(SessionControlFrame.Projection.serializer(), json)
            else -> SessionControlFrame.Unknown(type, json)
        }
    }
}

/**
 * One frame of the `workspace/follow` stream.
 *
 * The [Order] frame is authoritative and complete: display order is never inferred from the
 * arrival order of upserts, which is what makes the list converge after a reconnect baseline.
 */
@Serializable(with = WorkspaceFollowFrameSerializer::class)
sealed class WorkspaceFollowFrame {
    /** The wire discriminant. */
    abstract val type: String

    /** The complete opening registry state. */
    @Serializable
    data class Baseline(
        @SerialName("type") override val type: String = "baseline",
        @SerialName("workspaces") val workspaces: List<WorkspaceView> = emptyList(),
        @SerialName("workspaceIds") val workspaceIds: List<String> = emptyList(),
        @SerialName("archivedSessionIds") val archivedSessionIds: List<String> = emptyList(),
    ) : WorkspaceFollowFrame()

    /** One workspace was added or changed. */
    @Serializable
    data class Upsert(
        @SerialName("type") override val type: String = "upsert",
        @SerialName("workspace") val workspace: WorkspaceView,
    ) : WorkspaceFollowFrame()

    /** One workspace registration was removed. */
    @Serializable
    data class Remove(
        @SerialName("type") override val type: String = "remove",
        @SerialName("workspaceId") val workspaceId: String,
    ) : WorkspaceFollowFrame()

    /** The complete, authoritative registry display order. */
    @Serializable
    data class Order(
        @SerialName("type") override val type: String = "order",
        @SerialName("workspaceIds") val workspaceIds: List<String> = emptyList(),
    ) : WorkspaceFollowFrame()

    /** The complete registry-global archived set. */
    @Serializable
    data class Archived(
        @SerialName("type") override val type: String = "archived",
        @SerialName("archivedSessionIds") val archivedSessionIds: List<String> = emptyList(),
    ) : WorkspaceFollowFrame()

    /** A frame of an unknown `type`, preserved verbatim. */
    data class Unknown(
        override val type: String,
        val raw: JsonElement,
    ) : WorkspaceFollowFrame()
}

/** Custom `type`-dispatching serializer for [WorkspaceFollowFrame]. */
object WorkspaceFollowFrameSerializer : KSerializer<WorkspaceFollowFrame> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("WorkspaceFollowFrame") {
        element("type", buildSerialDescriptor("kotlin.String", PrimitiveKind.STRING))
    }

    override fun serialize(encoder: Encoder, value: WorkspaceFollowFrame) {
        val json: JsonElement = when (value) {
            is WorkspaceFollowFrame.Baseline ->
                encodeToJsonElement(WorkspaceFollowFrame.Baseline.serializer(), value)
            is WorkspaceFollowFrame.Upsert ->
                encodeToJsonElement(WorkspaceFollowFrame.Upsert.serializer(), value)
            is WorkspaceFollowFrame.Remove ->
                encodeToJsonElement(WorkspaceFollowFrame.Remove.serializer(), value)
            is WorkspaceFollowFrame.Order ->
                encodeToJsonElement(WorkspaceFollowFrame.Order.serializer(), value)
            is WorkspaceFollowFrame.Archived ->
                encodeToJsonElement(WorkspaceFollowFrame.Archived.serializer(), value)
            is WorkspaceFollowFrame.Unknown -> value.raw
        }
        (encoder as JsonEncoder).encodeJsonElement(json)
    }

    override fun deserialize(decoder: Decoder): WorkspaceFollowFrame {
        val json = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        return when (val type = json["type"]?.jsonPrimitive?.contentOrNull ?: "") {
            "baseline" -> decodeFromJsonElement(WorkspaceFollowFrame.Baseline.serializer(), json)
            "upsert" -> decodeFromJsonElement(WorkspaceFollowFrame.Upsert.serializer(), json)
            "remove" -> decodeFromJsonElement(WorkspaceFollowFrame.Remove.serializer(), json)
            "order" -> decodeFromJsonElement(WorkspaceFollowFrame.Order.serializer(), json)
            "archived" -> decodeFromJsonElement(WorkspaceFollowFrame.Archived.serializer(), json)
            else -> WorkspaceFollowFrame.Unknown(type, json)
        }
    }
}
