package com.nuvio.ckplayer

import kotlin.math.roundToInt

/**
 * Stream-card badges: the Elite "fusion" pack (the default set), matched
 * against a stream's raw name+title text. First hit per group wins, and
 * within a group the table runs specific -> generic (HDR10+ before HDR).
 */
object StreamBadges {
    private const val BASE = "https://raw.githubusercontent.com/leonevz/Elite-Badges/main/Badges/"

    private val PACK: List<Triple<String, Regex, String>> = listOf(
        Triple("resolution", Regex("""\b(4k|2160p|uhd|ultra\s*hd)\b""", RegexOption.IGNORE_CASE), "4k_ultra_hd.png"),
        Triple("resolution", Regex("""\b(1080p|fhd|full\s*hd)\b""", RegexOption.IGNORE_CASE), "1080p_full_hd.png"),
        Triple("resolution", Regex("""\b720p\b""", RegexOption.IGNORE_CASE), "720p_hd.png"),
        Triple("resolution", Regex("""\b480p\b""", RegexOption.IGNORE_CASE), "480p_sd.png"),
        Triple("video-tech", Regex("""\b(dolby\s*vision|dovi|dv)\b""", RegexOption.IGNORE_CASE), "dolby_vision.png"),
        Triple("video-tech", Regex("""\b(hdr10\+|hdr10\s*plus\b|hdr\s*10\s*\+)""", RegexOption.IGNORE_CASE), "hdr10_plus.png"),
        Triple("video-tech", Regex("""\b(hdr10|hdr\s*10)\b(?!\s*\+|\s*plus)""", RegexOption.IGNORE_CASE), "hdr10.png"),
        Triple("video-tech", Regex("""\bhdr\b""", RegexOption.IGNORE_CASE), "hdr.png"),
        Triple("video-tech", Regex("""\bsdr\b""", RegexOption.IGNORE_CASE), "SDR_transparent_4x.png"),
        Triple("video-tech", Regex("""\b(imax[\s._-]*enhanced)\b""", RegexOption.IGNORE_CASE), "imax_enhanced.png"),
        Triple("video-tech", Regex("""\b(imax)\b(?![\s._-]*enhanced)""", RegexOption.IGNORE_CASE), "imax.png"),
        Triple("source", Regex("""\bremux\b""", RegexOption.IGNORE_CASE), "remux.png"),
        Triple("source", Regex("""\b(blu[\s._-]?ray|bluray|bdrip|bdremux)\b""", RegexOption.IGNORE_CASE), "blu_ray_disc.png"),
        Triple("source", Regex("""\b(web[\s._-]?dl|webdl)\b""", RegexOption.IGNORE_CASE), "WEBDL_transparent_4x.png"),
        Triple("source", Regex("""\b(web[\s._-]?rip|webrip)\b""", RegexOption.IGNORE_CASE), "WEBRip_transparent_4x.png"),
        Triple("source", Regex("""\bhdtv\b""", RegexOption.IGNORE_CASE), "HDTV_transparent_4x.png"),
        Triple("source", Regex("""\b(dvd[\s._-]?rip|dvdrip)\b""", RegexOption.IGNORE_CASE), "DVD_RIP_transparent_4x.png"),
        Triple("video-codec", Regex("""\b(hevc|h[\s._-]?265|x265)\b""", RegexOption.IGNORE_CASE), "HEVC_transparent_4x.png"),
        Triple("video-codec", Regex("""\b(avc|h[\s._-]?264|x264)\b""", RegexOption.IGNORE_CASE), "AVC_transparent_4x.png"),
        Triple("bit-depth", Regex("""\b(10[\s._-]?bit|10b|hi10p)\b""", RegexOption.IGNORE_CASE), "10Bit_transparent_4x.png"),
        Triple("bit-depth", Regex("""\b(8[\s._-]?bit|8b)\b""", RegexOption.IGNORE_CASE), "8Bit_transparent_4x.png"),
        Triple("audio-tech", Regex("""\b(dolby\s*atmos|atmos)\b""", RegexOption.IGNORE_CASE), "dolby_atmos.png"),
        Triple("audio-tech", Regex("""\b(truehd|true\s*hd|dolby\s*truehd)\b""", RegexOption.IGNORE_CASE), "truehd.png"),
        Triple("audio-tech", Regex("""\b(ddp[\s._-]*[0-9][\s._-]*[0-9]|ddp|dd\+|dolby[\s._-]*digital[\s._-]*plus|e-?ac-?3)(?![a-z])""", RegexOption.IGNORE_CASE), "dolby_digital_plus.png"),
        Triple("audio-tech", Regex("""\b(dd[\s._-]*[0-9][\s._-]*[0-9]|dd|dolby[\s._-]*digital|ac-?3)(?![\s._-]*plus|\+|p|[a-z])""", RegexOption.IGNORE_CASE), "dolby_digital.png"),
        Triple("audio-tech", Regex("""\b(dts[:\s._-]*x)\b""", RegexOption.IGNORE_CASE), "dts_x.png"),
        Triple("audio-tech", Regex("""\b(dts[\s._-]*hd[\s._-]*ma|dtshd\s*ma|dts[\s._-]*hd[\s._-]*master)\b""", RegexOption.IGNORE_CASE), "dts_hd_master_audio.png"),
        Triple("audio-tech", Regex("""\b(dts[\s._-]*hd|dtshd)(?![\s._-]*(ma|master)|ma)\b""", RegexOption.IGNORE_CASE), "dts_hd.png"),
        Triple("audio-tech", Regex("""\bdts\b(?![\s._:-]*(x|hd))""", RegexOption.IGNORE_CASE), "dts.png"),
        Triple("audio-channels", Regex("""\b(7\.1|7-1|8ch|8\s*channel)\b""", RegexOption.IGNORE_CASE), "7_1_audio.png"),
        Triple("audio-channels", Regex("""\b(5\.1|5-1|6ch|6\s*channel)\b""", RegexOption.IGNORE_CASE), "5_1_audio.png"),
    )

    fun badges(raw: String): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for ((group, re, file) in PACK) {
            if (out.size >= 7) break
            if (group in seen || !re.containsMatchIn(raw)) continue
            seen.add(group)
            out.add(BASE + file)
        }
        return out
    }

    private fun fmtBytes(n: Long): String {
        val g = n / 1073741824.0
        if (g >= 10) return "${g.roundToInt()} GB"
        if (g >= 1) return String.format("%.1f GB", g)
        return "${(n / 1048576.0).roundToInt()} MB"
    }

    data class Facts(val facts: String, val desc: String)

    /** Size, seeders and provider pulled out of the stream text; lines that only
        carried those (Torrentio's emoji line) are consumed, the rest is desc. */
    fun facts(videoSize: Long, text: String): Facts {
        var size: String? = if (videoSize > 0) fmtBytes(videoSize) else null
        var seeds: String? = null
        var provider: String? = null
        val desc = ArrayList<String>()
        for (ln in text.split("\n")) {
            val factLine = ln.contains("\uD83D\uDC64") || ln.contains("\uD83D\uDCBE") || ln.contains('\u2699')
            if (size == null) {
                Regex("""(\d+(?:[.,]\d+)?)\s*(GB|GiB|MB|MiB)\b""", RegexOption.IGNORE_CASE).find(ln)?.let { m ->
                    val u = m.groupValues[2]
                    size = m.groupValues[1].replace(',', '.') + " " +
                        (if (u.length == 3) u[0].uppercaseChar() + "iB" else u.uppercase())
                }
            }
            if (seeds == null) {
                val m = Regex("""\uD83D\uDC64\s*(\d+)""").find(ln)
                    ?: Regex("""\b(?:seeds?|seeders?)[:\s]+(\d+)""", RegexOption.IGNORE_CASE).find(ln)
                if (m != null) seeds = m.groupValues[1]
            }
            if (provider == null) {
                val ix = ln.indexOf('\u2699')
                if (ix >= 0) {
                    val pv = ln.substring(ix + 1).removePrefix("\uFE0F").trim()
                        .substringBefore("\uD83D\uDC64").substringBefore("\uD83D\uDCBE").trim()
                    if (pv.isNotEmpty()) provider = pv
                }
            }
            if (!factLine && ln.isNotBlank()) desc.add(ln.trim())
        }
        val facts = listOfNotNull(size, seeds?.let { "$it seeds" }, provider).joinToString("  \u00b7  ")
        return Facts(facts, desc.joinToString(" \u00b7 "))
    }
}
