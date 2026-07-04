package dev.openstream.tv.addon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Addon manifest, served at `<addon base>/manifest.json`.
 * Spec: https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/manifest.md
 *
 * Every field the spec calls "required" still has a default here: we parse
 * leniently and validate separately ([isUsable]), so a slightly-broken addon
 * produces a clear error message instead of a crash (MASTER_PLAN §4.1.8).
 *
 * NOTE: addons are keyed by their manifest URL, not by [id] — multiple
 * instances of the same addon (e.g. 2-3 AIOStreams configs) must coexist
 * (MASTER_PLAN §4.2).
 */
@Serializable
data class Manifest(
    val id: String = "",
    val version: String = "",
    val name: String = "",
    val description: String = "",
    val resources: List<ManifestResource> = emptyList(),
    /** Content types as raw strings — never drop channel/tv (MASTER_PLAN §8). */
    val types: List<String> = emptyList(),
    val idPrefixes: List<String>? = null,
    val catalogs: List<ManifestCatalog> = emptyList(),
    val background: String? = null,
    val logo: String? = null,
    val contactEmail: String? = null,
    val behaviorHints: ManifestBehaviorHints = ManifestBehaviorHints(),
) {
    /** Minimum viability: something to identify it and at least one resource. */
    val isUsable: Boolean get() = id.isNotBlank() && name.isNotBlank() && resources.isNotEmpty()

    /** Resource lookup honoring both string and object notation. */
    fun resource(name: String): ManifestResource? = resources.firstOrNull { it.name == name }

    /**
     * True if the addon declares it can serve [resourceName] for [type]/[id].
     * MASTER_PLAN §4.1.3: never query an addon for something it doesn't declare.
     * `catalog` is special-cased by the spec: idPrefixes never apply to it.
     */
    fun declares(resourceName: String, type: String, id: String? = null): Boolean {
        val res = resource(resourceName) ?: return false
        val effectiveTypes = res.types ?: types
        if (type !in effectiveTypes) return false
        if (resourceName == "catalog" || id == null) return true
        val effectivePrefixes = res.idPrefixes ?: idPrefixes ?: return true
        return effectivePrefixes.any { id.startsWith(it) }
    }
}

/**
 * One entry of `manifest.resources`. The spec allows either a plain string
 * (`"stream"`) or an object (`{ "name": "stream", "types": [...], "idPrefixes": [...] }`);
 * [ManifestResourceSerializer] accepts both. When [types]/[idPrefixes] are
 * null, the manifest-level values apply.
 */
@Serializable(with = ManifestResourceSerializer::class)
data class ManifestResource(
    val name: String,
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
)

/** Surrogate for the object notation of [ManifestResource]. */
@Serializable
private data class ManifestResourceObject(
    val name: String = "",
    val types: List<String>? = null,
    val idPrefixes: List<String>? = null,
)

object ManifestResourceSerializer : KSerializer<ManifestResource> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("dev.openstream.tv.addon.ManifestResource")

    override fun deserialize(decoder: Decoder): ManifestResource {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("ManifestResource only supports JSON")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> ManifestResource(name = element.content)
            is JsonObject -> {
                val obj = jsonDecoder.json.decodeFromJsonElement(
                    ManifestResourceObject.serializer(), element
                )
                ManifestResource(obj.name, obj.types, obj.idPrefixes)
            }
            else -> error("Unexpected JSON for manifest resource: $element")
        }
    }

    override fun serialize(encoder: Encoder, value: ManifestResource) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("ManifestResource only supports JSON")
        if (value.types == null && value.idPrefixes == null) {
            jsonEncoder.encodeString(value.name)
        } else {
            jsonEncoder.encodeSerializableValue(
                ManifestResourceObject.serializer(),
                ManifestResourceObject(value.name, value.types, value.idPrefixes),
            )
        }
    }
}

/**
 * A catalog declared in the manifest. [extra] is the modern notation;
 * [extraSupported]/[extraRequired] are the legacy "short" notation still used
 * by older addons (including Cinemeta) — helpers below merge both.
 */
@Serializable
data class ManifestCatalog(
    val type: String = "",
    val id: String = "",
    val name: String = "",
    val extra: List<CatalogExtra> = emptyList(),
    val extraSupported: List<String> = emptyList(),
    val extraRequired: List<String> = emptyList(),
) {
    fun supportsExtra(extraName: String): Boolean =
        extra.any { it.name == extraName } || extraName in extraSupported

    fun requiresExtra(extraName: String): Boolean =
        extra.any { it.name == extraName && it.isRequired } || extraName in extraRequired

    /** A catalog that requires `search` is search-only: never show as a feed row. */
    val isSearchOnly: Boolean get() = requiresExtra("search")
}

@Serializable
data class CatalogExtra(
    val name: String = "",
    val isRequired: Boolean = false,
    val options: List<String>? = null,
    val optionsLimit: Int = 1,
)

@Serializable
data class ManifestBehaviorHints(
    val adult: Boolean = false,
    val p2p: Boolean = false,
    val configurable: Boolean = false,
    val configurationRequired: Boolean = false,
)
