package dev.openstream.tv.addon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Accepts `"value"`, `["a", "b"]`, or `null` where the spec says "array of
 * strings". Real addons (AIOMetadata among them) send plain strings for
 * fields like `director` — found live during the Phase 1 gate test. Lenient
 * parsing is mandatory (MASTER_PLAN §3.1): never crash a row over this.
 */
object FlexibleStringListSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("FlexibleStringListSerializer only supports JSON")
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> emptyList()
            is JsonPrimitive -> listOf(element.content)
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.content }
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) =
        encoder.encodeSerializableValue(delegate, value)
}
