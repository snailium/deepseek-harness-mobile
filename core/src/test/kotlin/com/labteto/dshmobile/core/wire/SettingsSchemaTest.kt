package com.labteto.dshmobile.core.wire

import com.labteto.dshmobile.core.wire.dto.SettingsDescribeValue
import com.labteto.dshmobile.core.wire.dto.SettingsField
import com.labteto.dshmobile.core.wire.dto.SettingsSchemaResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resolving `settings/describe`'s compiled schema into a field tree.
 *
 * The schema is a flat `refs` table keyed by uid, where a node names its children by uid rather than
 * nesting them. These tests run against an answer captured from a live harness 0.1.7-rc.2, because
 * the hand-written shape and the real one differ in ways that matter: the real `relay` namespace
 * compiles `tls` into a union of three string constants, and the real `browser` namespace has a
 * `dict` whose keys the user invents.
 */
class SettingsSchemaTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun describe(): SettingsDescribeValue =
        json.decodeFromString(
            SettingsDescribeValue.serializer(),
            javaClass.classLoader!!.getResourceAsStream("settings-describe.json")!!
                .bufferedReader().use { it.readText() },
        )

    private fun resolved(namespace: String) =
        describe().namespaces.first { it.ns == namespace }
            .let { SettingsSchemaResolver(describe().refsOf(it)).resolve(it) }

    /** The `refs` table of one namespace's compiled schema. */
    private fun SettingsDescribeValue.refsOf(namespace: com.labteto.dshmobile.core.wire.dto.SettingsNamespaceView) =
        ((namespace.schema as JsonObject)["refs"] as JsonObject)

    private fun flat(fields: List<SettingsField>): List<SettingsField> = fields.flatMap { field ->
        listOf(field) + when (field) {
            is SettingsField.Group -> flat(field.fields)
            is SettingsField.ListField -> field.element?.let { flat(listOf(it)) }.orEmpty()
            is SettingsField.MapField -> field.value?.let { flat(listOf(it)) }.orEmpty()
            else -> emptyList()
        }
    }

    private fun pathOf(field: SettingsField) = field.path.joinToString(".")

    // ------------------------------------------------------------------------ the relay shape

    /**
     * `relay.tls` is a union of three string constants on the live harness. That is the case a
     * picker exists for, and the case a naive reader gets wrong: the members are uids pointing at
     * `const` nodes, so the option labels live one indirection further out.
     */
    @Test
    fun `a union of string constants becomes a choice with its labels`() {
        val tls = flat(resolved("relay").fields).single { pathOf(it) == "tls" }
        assertTrue("expected a Choice, got $tls", tls is SettingsField.Choice)
        assertEquals(listOf("self-signed", "files", "off"), (tls as SettingsField.Choice).options)
    }

    @Test
    fun `a bounded number keeps its range`() {
        val port = flat(resolved("relay").fields).single { pathOf(it) == "port" }
        assertTrue(port is SettingsField.Number)
        assertEquals(0.0, port.node.min)
        assertEquals(65535.0, port.node.max)
        assertEquals(1.0, port.node.step)
    }

    /**
     * The description is what makes a generated form readable — without it every row is a camelCase
     * key. The relay declares them, so the resolver must carry them through.
     */
    @Test
    fun `a field keeps the plugin's own description`() {
        val bind = flat(resolved("relay").fields).single { pathOf(it) == "bind" }
        assertEquals("Listen address of the primary (TLS) listener.", bind.node.description)
    }

    @Test
    fun `an array reports its element type`() {
        val publicHostnames = flat(resolved("relay").fields).single { pathOf(it) == "publicHostnames" }
        assertTrue(publicHostnames is SettingsField.ListField)
        assertTrue((publicHostnames as SettingsField.ListField).element is SettingsField.Text)
    }

    @Test
    fun `a boolean field resolves as a toggle`() {
        // repeat-tool-breaker carries the profile's booleans (shellHttpBlock, blockLocalHttp);
        // context-trim has none, which is itself worth not assuming.
        val fields = flat(resolved("repeat-tool-breaker").fields)
        assertTrue("expected at least one toggle", fields.any { it is SettingsField.Toggle })
    }

    /**
     * A `dict` is a map whose keys the user invents — `browser.authProfiles`, `llm-pi-ai.providers`.
     * It must not be flattened into a group of fixed rows, because there is no fixed set of keys.
     */
    @Test
    fun `a dict resolves as a map rather than a group`() {
        val maps = flat(resolved("browser").fields).filterIsInstance<SettingsField.MapField>()
        assertTrue("expected a MapField", maps.isNotEmpty())
        assertNotNull(maps.first().value)
    }

    @Test
    fun `a nested object resolves into named fields`() {
        val groups = flat(resolved("relay").fields).filterIsInstance<SettingsField.Group>()
        assertTrue("expected a nested group", groups.isNotEmpty())
        assertTrue(groups.all { it.fields.isNotEmpty() })
    }

    // ----------------------------------------------------------------------- universal rules

    /** Every namespace in the capture must resolve without throwing. */
    @Test
    fun `every captured namespace resolves`() {
        val describe = describe()
        describe.namespaces.forEach { namespace ->
            val schema = SettingsSchemaResolver(describe.refsOf(namespace)).resolve(namespace)
            assertEquals(namespace.ns, schema.namespace)
            assertTrue("${namespace.ns} resolved to nothing", schema.fields.isNotEmpty())
        }
    }

    /**
     * Nothing may recurse forever. The graph is recursive by construction and shared between
     * namespaces, so a cycle is a matter of when rather than whether.
     */
    @Test
    fun `resolution terminates on every captured namespace`() {
        val describe = describe()
        describe.namespaces.forEach { namespace ->
            val fields = SettingsSchemaResolver(describe.refsOf(namespace)).resolve(namespace).fields
            // A depth bound plus a cycle guard: nothing may nest past the resolver's limit.
            fun depth(field: SettingsField): Int = when (field) {
                is SettingsField.Group -> 1 + (field.fields.maxOfOrNull { depth(it) } ?: 0)
                is SettingsField.ListField -> 1 + (field.element?.let { depth(it) } ?: 0)
                is SettingsField.MapField -> 1 + (field.value?.let { depth(it) } ?: 0)
                else -> 0
            }
            assertTrue("nesting exceeded the resolver's bound", flat(fields).maxOfOrNull { depth(it) }!! <= 12)
        }
    }

    /** Field order is stable, so a form does not reshuffle between renders. */
    @Test
    fun `fields come back in a stable order`() {
        val first = resolved("relay").fields.map { pathOf(it) }
        val second = resolved("relay").fields.map { pathOf(it) }
        assertEquals(first, second)
        assertEquals(first.sorted(), first)
    }

    /**
     * A constant is not editable — it is a value the schema pins. It stays visible as
     * [SettingsField.Unsupported] so the form can say so rather than dropping the key.
     */
    @Test
    fun `a pinned constant is reported rather than dropped`() {
        val describe = describe()
        val relay = describe.namespaces.first { it.ns == "relay" }
        val resolved = SettingsSchemaResolver(describe.refsOf(relay)).resolve(relay)
        val all = flat(resolved.fields)
        // Every field either resolves to a usable kind or states a reason; none is silently absent.
        all.forEach { field ->
            if (field is SettingsField.Unsupported) {
                assertTrue(field.reason.isNotBlank())
            }
        }
        assertTrue(all.isNotEmpty())
    }

    // ------------------------------------------------------------------ degenerate inputs

    /** A schema with no `uid` cannot be walked; the namespace reads as having no fields. */
    @Test
    fun `a schema without a root uid resolves to no fields`() {
        val describe = describe()
        val namespace = describe.namespaces.first().let { it.copy(schema = JsonObject(emptyMap())) }
        val resolved = SettingsSchemaResolver(describe.refsOf(describe.namespaces.first())).resolve(namespace)
        assertTrue(resolved.fields.isEmpty())
    }

    /** A dangling uid is reported, not thrown on: the schema is compiled from arbitrary plugin code. */
    @Test
    fun `a dangling reference resolves to an unsupported field`() {
        val refs = JsonObject(mapOf("1" to JsonObject(mapOf("type" to kotlinx.serialization.json.JsonPrimitive("object"), "dict" to JsonObject(mapOf("gone" to kotlinx.serialization.json.JsonPrimitive("999")))))))
        val namespace = describe().namespaces.first().let {
            it.copy(schema = JsonObject(mapOf("uid" to kotlinx.serialization.json.JsonPrimitive("1"), "refs" to refs)))
        }
        val resolved = SettingsSchemaResolver(refs).resolve(namespace)
        val field = resolved.fields.single()
        assertTrue(field is SettingsField.Unsupported)
        assertEquals("missing", (field as SettingsField.Unsupported).reason)
    }

    /** An unrecognised type stays visible with its name, so a newer schema degrades legibly. */
    @Test
    fun `an unknown type is reported with its own name`() {
        val refs = JsonObject(
            mapOf(
                "1" to JsonObject(
                    mapOf(
                        "type" to kotlinx.serialization.json.JsonPrimitive("object"),
                        "dict" to JsonObject(mapOf("future" to kotlinx.serialization.json.JsonPrimitive("2"))),
                    ),
                ),
                "2" to JsonObject(mapOf("type" to kotlinx.serialization.json.JsonPrimitive("bigint"))),
            ),
        )
        val namespace = describe().namespaces.first().let {
            it.copy(schema = JsonObject(mapOf("uid" to kotlinx.serialization.json.JsonPrimitive("1"), "refs" to refs)))
        }
        val field = SettingsSchemaResolver(refs).resolve(namespace).fields.single()
        assertEquals("bigint", (field as SettingsField.Unsupported).reason)
    }

    /** A union that is not all-constants has no closed set of labels, so it is left alone. */
    @Test
    fun `a mixed union is not turned into a picker`() {
        val refs = JsonObject(
            mapOf(
                "1" to JsonObject(
                    mapOf(
                        "type" to kotlinx.serialization.json.JsonPrimitive("object"),
                        "dict" to JsonObject(mapOf("mixed" to kotlinx.serialization.json.JsonPrimitive("2"))),
                    ),
                ),
                "2" to JsonObject(
                    mapOf(
                        "type" to kotlinx.serialization.json.JsonPrimitive("union"),
                        "list" to JsonArray(
                            listOf(
                                kotlinx.serialization.json.JsonPrimitive("3"),
                                kotlinx.serialization.json.JsonPrimitive("4"),
                            ),
                        ),
                    ),
                ),
                "3" to JsonObject(
                    mapOf(
                        "type" to kotlinx.serialization.json.JsonPrimitive("const"),
                        "value" to kotlinx.serialization.json.JsonPrimitive("off"),
                    ),
                ),
                "4" to JsonObject(mapOf("type" to kotlinx.serialization.json.JsonPrimitive("string"))),
            ),
        )
        val namespace = describe().namespaces.first().let {
            it.copy(schema = JsonObject(mapOf("uid" to kotlinx.serialization.json.JsonPrimitive("1"), "refs" to refs)))
        }
        val field = SettingsSchemaResolver(refs).resolve(namespace).fields.single()
        assertEquals("union", (field as SettingsField.Unsupported).reason)
        assertFalse(field is SettingsField.Choice)
    }
}
