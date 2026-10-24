package com.labteto.dshmobile.connection

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.labteto.dshmobile.core.wire.WireJson
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this device learned about a `dsh-mobile` gateway when it paired.
 *
 * Kept beside [HostConfig] rather than in it. `HostConfig` is upstream's file and every upstream
 * release reshapes it; three gateway fields on that data class meant a merge conflict in the file
 * that holds the wire model, twice. Here they are ours alone, keyed by the host id the remembered
 * endpoint already has, and a merge cannot touch them.
 *
 * [instanceId] is not just a label: it is the fingerprint of the certificate the gateway serves,
 * and it is what makes a re-connect to the wrong computer fail before anything is sent.
 */
@Serializable
data class GatewayIdentity(
    /** The gateway's id for this device, from the claim. Its device list shows this. */
    val deviceId: String = "",
    /** The gateway's stable instance id, as the pairing input spelled it. */
    val instanceId: String = "",
    /** Base64 DER of the CA this listener is signed by, when it serves a private one. */
    val caDerB64: String? = null,
    /** Lower-case hex SHA-256 of [caDerB64]; what the pairing input was checked against. */
    val caFingerprint: String? = null,
)

/**
 * Persistent home for [GatewayIdentity], keyed by the remembered host's id.
 *
 * A separate preferences key rather than a new file: the app already has one DataStore, and adding
 * a second store would mean a second migration path for no gain. The key is this fork's, so nothing
 * upstream writes can collide with it.
 */
@Singleton
class GatewayIdentityStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val serializer = MapSerializer(String.serializer(), GatewayIdentity.serializer())

    suspend fun get(hostId: String): GatewayIdentity? = all()[hostId]

    suspend fun all(): Map<String, GatewayIdentity> {
        val raw = dataStore.data.first()[KEY] ?: return emptyMap()
        // A blob this build cannot read is dropped, not raised: the host then reads as an ordinary
        // endpoint and asks to pair again, which is the only remedy the user has anyway.
        return runCatching { WireJson.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    suspend fun put(hostId: String, identity: GatewayIdentity) {
        val next = all() + (hostId to identity)
        dataStore.edit { it[KEY] = WireJson.encodeToString(serializer, next) }
    }

    suspend fun remove(hostId: String) {
        val next = all() - hostId
        dataStore.edit { it[KEY] = WireJson.encodeToString(serializer, next) }
    }

    private companion object {
        val KEY = stringPreferencesKey("gateway_identities_json")
    }
}
