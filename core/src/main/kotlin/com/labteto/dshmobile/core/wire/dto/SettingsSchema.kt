package com.labteto.dshmobile.core.wire.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The settings plane's schema, resolved into something a form can be drawn from.
 *
 * `settings/describe` answers each namespace with a *compiled* schema: a flat `refs` table keyed by
 * integer uid, where a node refers to its children by uid rather than by nesting them. The wire
 * shape is deliberately opaque to a client that only wants to pass values through, and
 * [SettingsNamespaceView] keeps it that way. This file does the opposite: it walks the reference
 * graph once and produces a tree, because a phone that wants to *edit* a plugin's configuration has
 * to know that `relay.tls` is a three-way union of string constants and that `port` is a number
 * bounded to 0..65535.
 *
 * ## Why resolve rather than render the raw graph
 *
 * A resolver failure is contained. The graph is recursive (a `dict` reaches an `object` that reaches
 * further `dict`s) and some nodes are shared between namespaces, so drawing straight from `refs`
 * would mean every composable carrying its own cycle guard and depth limit. Resolving once yields a
 * tree with a bounded depth, and a node that cannot be resolved becomes [SettingsField.Unsupported]
 * instead of a crash — which matters because the schema is compiled from arbitrary plugin code.
 *
 * Cycle safety is a visited set of uids on the path from the root, not a global one: the same node
 * may legitimately appear under two branches, and only a path that returns to a uid already on it is
 * a cycle.
 */
@Serializable
data class SettingsSchemaNode(
    val uid: Long,
    val type: String,
    val meta: Map<String, JsonElement> = emptyMap(),
) {
    /** The author-written description, when the plugin declared one. */
    val description: String? get() = meta["description"]?.jsonPrimitive?.contentOrNull

    /** Whether the plugin requires this field to be present. */
    val required: Boolean get() = meta["required"]?.jsonPrimitive?.booleanOrNull == true

    /** The declared default, when there is one. */
    val default: JsonElement? get() = meta["default"]

    /** Numeric lower bound, when the plugin declared one. */
    val min: Double? get() = meta["min"]?.jsonPrimitive?.doubleOrNull

    /** Numeric upper bound, when the plugin declared one. */
    val max: Double? get() = meta["max"]?.jsonPrimitive?.doubleOrNull

    /** Numeric step, when the plugin declared one. */
    val step: Double? get() = meta["step"]?.jsonPrimitive?.doubleOrNull
}

/**
 * One editable (or merely reported) field, as the form sees it.
 *
 * [path] is the JSON path from the namespace root — `["providers", "b70-smg", "models"]` — and is
 * what `settings/update` addresses. It is carried on the node rather than derived by the caller
 * because the caller would have to reproduce the same walking rules to build it.
 */
sealed interface SettingsField {
    val path: List<String>
    val node: SettingsSchemaNode

    /** A free-text string. */
    data class Text(
        override val path: List<String>,
        override val node: SettingsSchemaNode,
    ) : SettingsField

    /** A number, optionally bounded. */
    data class Number(
        override val path: List<String>,
        override val node: SettingsSchemaNode,
    ) : SettingsField

    /** A boolean. */
    data class Toggle(
        override val path: List<String>,
        override val node: SettingsSchemaNode,
    ) : SettingsField

    /**
     * A closed set of string values, from a `union` of string constants.
     *
     * Only unions whose every member is a constant become this; a union of anything else is
     * [Unsupported], because a picker cannot render a choice it cannot name.
     */
    data class Choice(
        override val path: List<String>,
        override val node: SettingsSchemaNode,
        val options: List<String>,
    ) : SettingsField

    /** An array, whose element type is [element] when it resolved. */
    data class ListField(
        override val path: List<String>,
        override val node: SettingsSchemaNode,
        val element: SettingsField?,
    ) : SettingsField

    /**
     * A nested group of fields.
     *
     * [fields] is sorted by path so a form's order is stable across renders; the wire order of a
     * `dict` is a JSON object's key order, which is not something to depend on for layout.
     */
    data class Group(
        override val path: List<String>,
        override val node: SettingsSchemaNode,
        val fields: List<SettingsField>,
    ) : SettingsField

    /**
     * A free-form map — a `dict` whose keys the user invents.
     *
     * Distinct from [Group] because its keys are not schema-declared: `providers` on `llm-pi-ai` is
     * a dict of user-chosen provider names, so a form cannot lay out a fixed row per key.
     */
    data class MapField(
        override val path: List<String>,
        override val node: SettingsSchemaNode,
        val value: SettingsField?,
    ) : SettingsField

    /**
     * A node this build cannot edit, kept so the form can say so rather than omit it silently.
     *
     * [reason] is short and machine-ish (`object`, `cycle`, `depth`) so the UI can phrase it; a
     * field that simply vanished would make the form look incomplete rather than limited.
     */
    data class Unsupported(
        override val path: List<String>,
        override val node: SettingsSchemaNode,
        val reason: String,
    ) : SettingsField
}

/** Where one namespace's schema came from, for a form to bind against. */
data class ResolvedSettingsSchema(
    val namespace: String,
    /** The declared fields, in a stable order. Empty when the root could not be read. */
    val fields: List<SettingsField>,
    /** Whether the Host reports this namespace as writable through the configuration plane. */
    val writable: Boolean,
)

/**
 * Turns a compiled schema into a field tree.
 *
 * One instance per describe call: the `refs` table is shared by every namespace in one answer, and
 * resolving them separately would re-walk the same nodes.
 */
class SettingsSchemaResolver(
    private val refs: Map<String, JsonElement>,
    /** Nesting limit. A schema deeper than this is reported as unsupported rather than followed. */
    private val maxDepth: Int = 12,
) {
    /** Resolve one namespace's root node, or an empty field list when it cannot be read. */
    fun resolve(namespace: SettingsNamespaceView): ResolvedSettingsSchema {
        val root = rootNode(namespace.schema)
            ?: return ResolvedSettingsSchema(namespace.ns, emptyList(), namespaceIsWritable(namespace))
        val fields = (root["dict"] as? JsonObject).orEmpty().mapNotNull { (name, target) ->
            field(listOf(name), target, emptyList(), 0)
        }.sortedBy { it.path.joinToString(".") }
        return ResolvedSettingsSchema(namespace.ns, fields, namespaceIsWritable(namespace))
    }

    /**
     * Whether a namespace reports itself writable.
     *
     * `applies` is `live` or `restart`: a `restart` namespace can still be written, but the change
     * does not take effect until the harness restarts, and a form should say so.
     */
    private fun namespaceIsWritable(namespace: SettingsNamespaceView): Boolean =
        namespace.applies.isNotBlank()

    /** The root node of a compiled schema, from its `uid`. */
    private fun rootNode(schema: JsonElement): JsonObject? {
        val obj = schema as? JsonObject ?: return null
        val uid = (obj["uid"] as? JsonPrimitive)?.contentOrNull ?: return null
        return (obj["refs"] as? JsonObject)?.get(uid) as? JsonObject
    }

    /**
     * Resolve one node reference.
     *
     * [visited] is the uid path from the root, so a node that recurs on its own branch is reported
     * as a cycle instead of recursing forever. It is copied rather than mutated: a sibling branch
     * must not inherit this branch's history.
     */
    private fun field(
        path: List<String>,
        target: JsonElement,
        visited: List<Long>,
        depth: Int,
    ): SettingsField? {
        val uid = (target as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: return null
        val node = (refs[uid.toString()] as? JsonObject)
            ?: return unsupported(path, uid, "missing")
        // The node's `meta` is a nested object; its siblings (`type`, `dict`, `inner`, `list`) are
        // structure. Passing the whole node here would leave every description and bound unread —
        // `meta["description"]` would look for a key that lives one level down.
        val schemaNode = SettingsSchemaNode(
            uid = uid,
            type = (node["type"] as? JsonPrimitive)?.contentOrNull ?: "",
            meta = (node["meta"] as? JsonObject).orEmpty(),
        )

        if (depth > maxDepth) return SettingsField.Unsupported(path, schemaNode, "depth")
        if (uid in visited) return SettingsField.Unsupported(path, schemaNode, "cycle")
        val nextVisited = visited + uid

        return when (schemaNode.type) {
            "string" -> SettingsField.Text(path, schemaNode)
            "number" -> SettingsField.Number(path, schemaNode)
            "boolean" -> SettingsField.Toggle(path, schemaNode)
            "object" -> group(path, node, schemaNode, nextVisited, depth)
            "dict" -> SettingsField.MapField(
                path, schemaNode,
                (node["inner"] as? JsonElement)?.let { field(path + "*", it, nextVisited, depth + 1) },
            )
            "array" -> SettingsField.ListField(
                path, schemaNode,
                (node["inner"] as? JsonElement)?.let { field(path + "[]", it, nextVisited, depth + 1) },
            )
            "union" -> union(path, node, schemaNode)
            // A constant is not a field: it is a value the schema pins, and a form would only be
            // able to show it as a disabled row. Reported as unsupported so it stays visible.
            "const" -> SettingsField.Unsupported(path, schemaNode, "const")
            else -> SettingsField.Unsupported(path, schemaNode, schemaNode.type.ifBlank { "unknown" })
        }
    }

    private fun group(
        path: List<String>,
        node: JsonObject,
        schemaNode: SettingsSchemaNode,
        visited: List<Long>,
        depth: Int,
    ): SettingsField {
        val dict = node["dict"] as? JsonObject
            ?: return SettingsField.Unsupported(path, schemaNode, "object")
        val fields = dict.mapNotNull { (name, target) ->
            field(path + name, target, visited, depth + 1)
        }.sortedBy { it.path.joinToString(".") }
        return SettingsField.Group(path, schemaNode, fields)
    }

    /**
     * A union of string constants becomes a picker; anything else is unsupported.
     *
     * The constants' `value` fields are the choice strings, which is how `tls: self-signed | files |
     * off` is expressed. A union that mixes a constant with a non-constant has no closed set of
     * labels and is left alone rather than approximated.
     */
    private fun union(
        path: List<String>,
        node: JsonObject,
        schemaNode: SettingsSchemaNode,
    ): SettingsField {
        val members = (node["list"] as? kotlinx.serialization.json.JsonArray).orEmpty()
        val options = members.mapNotNull { member ->
            val uid = (member as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val ref = refs[uid] as? JsonObject ?: return@mapNotNull null
            if ((ref["type"] as? JsonPrimitive)?.contentOrNull != "const") return@mapNotNull null
            (ref["value"] as? JsonPrimitive)?.contentOrNull
        }
        return if (options.size == members.size && options.isNotEmpty()) {
            SettingsField.Choice(path, schemaNode, options)
        } else {
            SettingsField.Unsupported(path, schemaNode, "union")
        }
    }

    /** A node whose uid is unknown, reported with the uid that failed so the gap is visible. */
    private fun unsupported(path: List<String>, uid: Long, reason: String): SettingsField =
        SettingsField.Unsupported(
            path,
            SettingsSchemaNode(uid, "", emptyMap()),
            reason,
        )
}
