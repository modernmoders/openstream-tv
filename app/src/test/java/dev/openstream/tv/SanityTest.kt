package dev.openstream.tv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 0 sanity test: proves the unit-test toolchain and the lenient JSON
 * configuration the addon client will rely on (MASTER_PLAN §3.1 mandates
 * lenient parsing because addons return sloppy JSON).
 */
class SanityTest {

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun `lenient json survives unknown keys and unquoted values`() {
        val sloppy = """{ "id": tt0111161, "unknownField": {"nested": true}, "name": "Test" }"""
        val parsed = lenientJson.parseToJsonElement(sloppy).jsonObject
        assertEquals("tt0111161", parsed["id"]?.jsonPrimitive?.content)
        assertEquals("Test", parsed["name"]?.jsonPrimitive?.content)
    }
}
