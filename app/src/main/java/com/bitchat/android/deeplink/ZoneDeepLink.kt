package com.bitchat.android.deeplink

import java.net.URI
import java.net.URLDecoder

/** A parsed QRchat zone link: geohash channel plus optional venue name. */
data class ZoneLink(val geohash: String, val venueName: String?)

/**
 * Parses links that open a geohash location channel:
 *  - qrchat://zona/<geohash>[?nome=<venue>]
 *  - https://qradio.grok.me/c/<geohash>[?nome=<venue>]
 * Geohash must be base32 (geohash alphabet), length 2..8 (region..building levels).
 */
object ZoneDeepLink {
    const val WEB_HOST = "qradio.grok.me"
    private const val ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"
    const val MIN_LEN = 2
    const val MAX_LEN = 8

    fun isZoneLink(raw: String?): Boolean {
        val uri = toUri(raw) ?: return false
        return extractGeohashSegment(uri) != null
    }

    fun parse(raw: String?): ZoneLink? {
        val uri = toUri(raw) ?: return null
        val segment = extractGeohashSegment(uri) ?: return null
        val geohash = segment.lowercase()
        if (!isValidGeohash(geohash)) return null
        return ZoneLink(geohash, venueName(uri))
    }

    fun isValidGeohash(value: String): Boolean =
        value.length in MIN_LEN..MAX_LEN && value.all { it in ALPHABET }

    private fun toUri(raw: String?): URI? =
        if (raw.isNullOrBlank()) null else try { URI(raw.trim()) } catch (_: Exception) { null }

    private fun extractGeohashSegment(uri: URI): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val parts = (uri.rawPath ?: "").trim('/').split('/').filter { it.isNotEmpty() }
        return when {
            scheme == "qrchat" && host == "zona" -> parts.singleOrNull()
            (scheme == "https" || scheme == "http") && host == WEB_HOST ->
                if (parts.size == 2 && parts[0] == "c") parts[1] else null
            else -> null
        }
    }

    private fun venueName(uri: URI): String? {
        val query = uri.rawQuery ?: return null
        return query.split('&').firstNotNullOfOrNull { pair ->
            val kv = pair.split('=', limit = 2)
            if (kv.size == 2 && kv[0] == "nome") {
                try { URLDecoder.decode(kv[1], "UTF-8") } catch (_: Exception) { null }
            } else null
        }?.trim()?.take(80)?.ifBlank { null }
    }
}
