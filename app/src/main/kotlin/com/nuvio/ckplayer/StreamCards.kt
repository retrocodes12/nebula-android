@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.nuvio.ckplayer

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Stream card designs (Settings › Streams › Stream cards) — the shared player's `SC_DESIGNS`, `relTitle`, `relTags`,
 * `releaseCardHtml` (web 2026.11.04.2). A design is a layout (stacked or one line), a spacing and which parts show;
 * Classic (null) is the plate-first row StreamRow has always drawn.
 */
object StreamCards {
    data class Spec(
        val layout: String = "stack", val dense: Boolean = false,
        val tags: Boolean = true, val size: Boolean = true, val seeds: Boolean = true, val res: Boolean = true,
        val marks: Boolean = true, val langs: Boolean = true, val addon: Boolean = true, val service: Boolean = true,
        val play: Boolean = true, val file: Boolean = true,
    ) {
        val row: Boolean get() = layout == "row"
        fun has(part: String): Boolean = when (part) {
            "tags" -> tags; "size" -> size; "seeds" -> seeds; "res" -> res; "marks" -> marks; "langs" -> langs
            "addon" -> addon; "service" -> service; "play" -> play; "file" -> file; else -> false
        }
        fun with(part: String, on: Boolean): Spec = when (part) {
            "tags" -> copy(tags = on); "size" -> copy(size = on); "seeds" -> copy(seeds = on); "res" -> copy(res = on)
            "marks" -> copy(marks = on); "langs" -> copy(langs = on); "addon" -> copy(addon = on); "service" -> copy(service = on)
            "play" -> copy(play = on); "file" -> copy(file = on); else -> this
        }
    }

    val PARTS = listOf(
        "tags" to "Source · codec", "size" to "Size", "seeds" to "Seeds", "res" to "Quality", "marks" to "Picture & sound",
        "langs" to "Languages", "addon" to "Add-on", "service" to "Service", "play" to "Play button", "file" to "File name",
    )

    data class Design(val id: String, val name: String, val sub: String, val spec: Spec?)

    val RELEASE = Spec()
    val DESIGNS = listOf(
        Design("release", "Release", "Everything, the file’s name at the foot", RELEASE),
        Design("compact", "Compact", "Marks and sources, no file name", Spec(dense = true, service = false, file = false)),
        Design("line", "One line", "Quality, title, size and the add-on",
            Spec(layout = "row", dense = true, seeds = false, marks = false, langs = false, service = false, file = false)),
        Design("minimal", "Minimal", "Quality, title and size — nothing else",
            Spec(layout = "row", dense = true, tags = false, seeds = false, marks = false, langs = false, addon = false,
                service = false, play = false, file = false)),
        Design("classic", "Classic", "The quality plate in front", null),
        Design("custom", "Custom", "Yours: pick the layout and every part", null),
    )

    /** "layout=row;dense=1;tags=0;…" — what the Custom design keeps (device-local, like the web's prefs). Unknown
        or missing parts show, as on the web (a part never saved is on). */
    fun decode(enc: String): Spec {
        var s = RELEASE
        for (kv in enc.split(';')) {
            val k = kv.substringBefore('=').trim(); val v = kv.substringAfter('=', "").trim()
            when {
                k == "layout" -> s = s.copy(layout = if (v == "row") "row" else "stack")
                k == "dense" -> s = s.copy(dense = v == "1")
                PARTS.any { it.first == k } -> s = s.with(k, v != "0")
            }
        }
        return s
    }

    fun encode(s: Spec): String =
        (listOf("layout=" + s.layout, "dense=" + (if (s.dense) "1" else "0")) + PARTS.map { it.first + "=" + (if (s.has(it.first)) "1" else "0") })
            .joinToString(";")

    fun design(id: String): Design = DESIGNS.firstOrNull { it.id == id } ?: DESIGNS[0]

    /** The design the stream list draws: a spec, or null for Classic. */
    fun specFor(style: String, custom: String): Spec? = when (style) {
        "classic" -> null
        "custom" -> decode(custom)
        else -> design(style).spec ?: RELEASE
    }

    // ---- reading a release ----
    private val RC_EXT = Regex("""\.(mkv|mp4|m4v|avi|webm|mov|ts|wmv|flv)$""", RegexOption.IGNORE_CASE)
    private val RC_STOP = Regex(
        "^(2160p|1080p|1080i|720p|576p|480p|360p|4k|uhd|web|webrip|webdl|web-dl|bluray|blu-ray|bdrip|brrip|bdremux|hdtv|dvdrip|remux|" +
            "hdr|hdr10|dv|dovi|x264|x265|h264|h265|hevc|avc|av1|10bit|8bit|amzn|nf|dsnp|hmax|atvp|imax|hybrid|multi|dual|repack|proper|" +
            "extended|unrated|uncut|complete|internal|limited|webm|ddp|dd|aac|truehd|atmos|dts)$"
    )
    private val RE_YEAR_TOK = Regex("""^\(?(19|20)\d\d\)?$""")
    private val RE_EP_TOK = Regex("""^s\d{1,2}(e\d{1,3})?$""")
    private val RE_EP_START = Regex("""^s\d{1,2}e\d""")
    private val RE_EMOJI = Regex("""[←-⇿⌀-➿⬀-⯿️‍]|[\x{1F000}-\x{1FAFF}]""")

    /** "The.Dark.Knight.2008.UHD.BluRay.2160p…mkv" → "The Dark Knight 2008": the words before the year (kept), else
        up to the first release word. "" when nothing sensible is left. */
    fun relTitle(file: String): String {
        val n = file.substringAfterLast('/').substringAfterLast('\\').replace(RC_EXT, "")
            .replace(Regex("[._]+"), " ").replace(Regex("""\s+"""), " ").trim()
            .replace(Regex("""^(\[[^\]]*]\s*)+"""), "")               // a leading "[Group]"
        val toks = n.split(' ').filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        val yi = (1 until toks.size).firstOrNull { RE_YEAR_TOK.matches(toks[it]) } ?: -1
        if (yi > 0) {                                                  // "Charlotte's Web 2006" keeps its Web
            out.addAll(toks.subList(0, yi)); out.add(toks[yi].replace("(", "").replace(")", ""))
        } else for ((i, t) in toks.withIndex()) {
            val tl = t.lowercase().replace("(", "").replace(")", "")
            if (i > 0 && (RC_STOP.matches(tl) || RE_EP_TOK.matches(tl) || RE_EP_START.containsMatchIn(tl) || t.startsWith("[") || t.startsWith("{"))) break
            out.add(t)
        }
        val r = out.joinToString(" ").trimEnd(' ', '-', '–', ':')
        return if (r.length >= 2) r.take(80) else ""
    }

    /** The source and the picture codec, in words: "BDRemux · HEVC". */
    fun relTags(raw: String): List<String> {
        val out = ArrayList<String>()
        fun has(re: String) = Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(raw)
        when {
            has("remux") -> out.add(if (has("""\b(bd|blu[\s._-]?ray|bluray|uhd)""") || has("bdremux")) "BDRemux" else "Remux")
            has("""\b(blu[\s._-]?ray|bluray)\b""") -> out.add("BluRay")
            has("""\b(bdrip|brrip)\b""") -> out.add("BDRip")
            has("""\b(web[\s._-]?dl|webdl)\b""") -> out.add("WEB-DL")
            has("""\b(web[\s._-]?rip|webrip)\b""") -> out.add("WEBRip")
            has("""\bweb\b""") -> out.add("WEB")
            has("""\bhdtv\b""") -> out.add("HDTV")
            has("""\b(dvd[\s._-]?rip|dvdrip)\b""") -> out.add("DVDRip")
            has("""\b(hdcam|camrip|cam)\b""") -> out.add("CAM")
        }
        when {
            has("""\bav1\b""") -> out.add("AV1")
            has("""\b(hevc|h[\s._-]?265|x265)\b""") -> out.add("HEVC")
            has("""\b(avc|h[\s._-]?264|x264)\b""") -> out.add("AVC")
        }
        return out
    }

    private val LANG_CODES = mapOf(
        "English" to "EN", "French" to "FR", "Italian" to "IT", "Spanish" to "ES", "German" to "DE", "Hindi" to "HI", "Tamil" to "TA",
        "Telugu" to "TE", "Malayalam" to "ML", "Kannada" to "KN", "Polish" to "PL", "Portuguese" to "PT", "Russian" to "RU",
        "Japanese" to "JA", "Korean" to "KO", "Chinese" to "ZH", "Arabic" to "AR", "Turkish" to "TR", "Dutch" to "NL",
        "Latino" to "LAT", "Multi" to "MULTI", "Dual Audio" to "DUAL",
    )
    fun langCodes(raw: String, max: Int = 5): List<String> =
        StreamBadges.langList(raw, max).map { LANG_CODES[it] ?: it.take(2).uppercase() }

    /** The add-on's own word for the row ("4KHDHub | Direct" → "Direct"), less what the marks, the plate, the release
        words and the title already say. */
    fun label(name: String, addonName: String, title: String, fired: List<Regex>, provider: String?): String {
        val low = title.lowercase()
        return StreamBadges.cleanName(name, addonName).split(" · ").map { it.trim() }.filter { tk ->
            tk.isNotEmpty() && fired.none { it.containsMatchIn(tk) } && !RC_STOP.matches(tk.lowercase()) &&
                !low.contains(tk.lowercase()) && tk != provider
        }.joinToString(" · ").takeIf { it.length <= 40 }.orEmpty()
    }

    /** Everything a card shows, read once per row. */
    data class Card(
        val title: String, val tags: List<String>, val res: String?, val badges: List<String>, val langs: List<String>,
        val pills: List<String>, val file: String, val desc: String, val size: String?, val line2: String,
    )

    fun build(s: StreamItem, addonName: String, pageTitle: String, spec: Spec, addonMark: String): Card {
        val raw = s.name + "\n" + s.title
        var file = StreamTwin.fileName(s)
        // no file name: a first line that reads like a release stands in for one
        val line1 = s.title.lineSequence().firstOrNull().orEmpty().replace(RE_EMOJI, " ").replace(Regex("""\s+"""), " ").trim()
        if (file.isEmpty() && line1.isNotEmpty()) { val lt = relTitle(line1); if (lt.isNotEmpty() && lt.length < line1.length - 4) file = line1 }
        val plate = StreamBadges.plate(raw)
        val all = StreamBadges.match(raw, if (plate != null) "resolution" else null)
        val f = StreamBadges.facts(s.videoSize, s.title, all.fired)
        val name = StreamBadges.cleanName(s.name, addonName).ifEmpty { f.provider ?: "Stream" }
        val title = relTitle(file).ifEmpty { name }
        val marks = StreamBadges.match(raw, setOf("resolution", "source", "video-codec", "bit-depth", "audio-channels"), 4)
        val pills = ArrayList<String>()
        if (spec.addon && addonName.isNotBlank() && addonMark != "hidden") pills.add(addonName)
        if (spec.service) {
            label(s.name, addonName, title, all.fired, f.provider).takeIf { it.isNotEmpty() }?.let { pills.add(it) }
            f.provider?.takeIf { it != addonName }?.let { pills.add(it) }
        }
        val desc = if (file.isEmpty()) StreamBadges.cleanDesc(f.desc, addonName, pageTitle) else ""
        return Card(
            title = title,
            tags = if (spec.tags) relTags(raw) else emptyList(),
            res = if (spec.res) plate?.res?.let { if (it.all { c -> c.isDigit() }) it + "p" else it } else null,
            badges = if (spec.marks) marks.badges else emptyList(),
            langs = if (spec.langs) langCodes(raw) else emptyList(),
            pills = pills,
            file = if (spec.file) file.substringAfterLast('/').substringAfterLast('\\') else "",
            desc = if (spec.file) desc else "",
            size = f.size?.takeIf { spec.size },
            line2 = listOfNotNull(f.seeds?.takeIf { spec.seeds }?.let { "↑ $it seeds" }, f.bitrate?.takeIf { spec.size }).joinToString(" · "),
        )
    }

    /** The row every preview draws: a film's 4K remux from a torrent add-on, as such rows really arrive. */
    val SAMPLE = StreamItem(
        name = "Torrentio\n4k DV | HDR",
        title = "The.Dark.Knight.2008.UHD.BluRay.2160p.TrueHD.Atmos.7.1.DV.HEVC.REMUX-FraMeSToR.mkv\n" +
            "👤 154 💾 53.75 GB ⚙️ TorrentGalaxy\nMulti Audio / 🇬🇧 / 🇳🇱",
        url = "about:blank",
        fileName = "The.Dark.Knight.2008.UHD.BluRay.2160p.TrueHD.Atmos.7.1.DV.HEVC.REMUX-FraMeSToR.mkv",
    )
}

// the pack's logos come in brand colours; the card draws them white, as the marks on a disc box are (grayscale × 1.9)
private val WhiteMarks = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    0.4047f, 1.3585f, 0.1368f, 0f, 0f,
    0.4047f, 1.3585f, 0.1368f, 0f, 0f,
    0.4047f, 1.3585f, 0.1368f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)))

/** A release card's body (the StreamRow wraps it in its FocusCard). [eyebrow] = "SAME AS LAST TIME" / "P2P";
    [warn] = "may stall here" and the like; [extra] = the speed test's line. */
@Composable
internal fun ReleaseCardBody(
    c: StreamCards.Card, spec: StreamCards.Spec, focused: Boolean, usual: Boolean = false,
    eyebrow: String = "", eyebrowAccent: Boolean = false, warn: String? = null, extra: (@Composable () -> Unit)? = null,
) {
    val dense = spec.dense
    val shape = RoundedCornerShape(if (dense) 14.dp else 18.dp)
    Column(
        Modifier.fillMaxWidth().background(SurfaceC, shape).border(1.dp, if (usual) Line2 else LineC, shape)
            .padding(horizontal = if (dense) 14.dp else 16.dp, vertical = if (dense) 11.dp else 15.dp),
    ) {
        if (eyebrow.isNotEmpty()) Text(
            eyebrow, color = if (eyebrowAccent) Red else MutedC, fontFamily = Mono, fontSize = tinySp(9f),
            fontWeight = FontWeight.Medium, letterSpacing = 1.3.sp, modifier = Modifier.padding(bottom = 4.dp),
        )
        val title = buildAnnotatedString {
            withStyle(SpanStyle(color = TextC, fontWeight = FontWeight.Bold)) { append(c.title) }
            if (c.tags.isNotEmpty()) withStyle(SpanStyle(color = MutedC, fontWeight = FontWeight.Medium)) { append(" · " + c.tags.joinToString(" · ")) }
        }
        val titleSp = if (dense) 15.sp else 16.5.sp
        val spec2 = @Composable {
            if (c.size != null || c.line2.isNotEmpty() || warn != null) Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
                c.size?.let { Text(it, color = TextC, fontFamily = Mono, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold) }
                if (c.line2.isNotEmpty()) Text(c.line2, color = MutedC, fontFamily = Mono, fontSize = 11.5.sp, modifier = Modifier.padding(top = 2.dp))
                warn?.let { Text(it, color = FaintC, fontFamily = Mono, fontSize = tinySp(10f), letterSpacing = 0.5.sp, modifier = Modifier.padding(top = 2.dp)) }
            }
        }
        val disc = @Composable {
            if (spec.play) {
                val d = if (dense) 38.dp else 44.dp
                Box(Modifier.padding(start = 12.dp).size(d).background(if (focused) Red else Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = if (focused) Color.White else Color.Black,
                        modifier = Modifier.size(if (dense) 18.dp else 22.dp))
                }
            }
        }
        if (spec.row) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // a slot one width for every row, so the titles keep one edge ("1080p" is the widest chip)
                if (spec.res) Box(Modifier.width(68.dp)) { c.res?.let { ResChip(it) } }
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = titleSp, fontFamily = Sans, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (c.badges.isNotEmpty() || c.langs.isNotEmpty() || c.pills.isNotEmpty())
                        FlowRow(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Marks(c, small = true); c.pills.forEach { Pill(it, small = true) }
                        }
                }
                spec2(); disc()
            }
        } else {
            Row(verticalAlignment = Alignment.Top) {
                Text(title, fontSize = titleSp, fontFamily = Sans, modifier = Modifier.weight(1f))
                spec2()
            }
            if (c.res != null || c.badges.isNotEmpty() || c.langs.isNotEmpty())
                FlowRow(Modifier.padding(top = if (dense) 7.dp else 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    c.res?.let { ResChip(it) }
                    Marks(c, small = false)
                }
            if (c.pills.isNotEmpty() || spec.play)
                Row(Modifier.padding(top = if (dense) 7.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        c.pills.forEach { Pill(it, small = dense) }
                    }
                    disc()
                }
        }
        extra?.let { Box(Modifier.padding(top = 6.dp)) { it() } }
        val foot = c.file.ifEmpty { c.desc }
        if (foot.isNotEmpty()) {
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(LineC))
            Text(
                foot, color = if (c.file.isNotEmpty()) FaintC else MutedC, fontFamily = if (c.file.isNotEmpty()) Mono else Sans,
                fontSize = if (c.file.isNotEmpty()) 11.5.sp else 12.5.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun ResChip(res: String) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(res, color = Color.Black, fontFamily = Mono, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Marks(c: StreamCards.Card, small: Boolean) {
    c.badges.forEach { b ->
        AsyncImage(
            model = b, contentDescription = null, contentScale = ContentScale.Fit, colorFilter = WhiteMarks,
            modifier = Modifier.height(if (small) 15.dp else 19.dp).widthIn(max = 80.dp).padding(vertical = 1.dp),
        )
    }
    c.langs.forEach { l ->
        Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Surface2).padding(horizontal = 6.dp, vertical = 3.dp)) {
            Text(l, color = MutedC, fontFamily = Mono, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
        }
    }
}

@Composable
private fun Pill(text: String, small: Boolean) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Surface2).padding(horizontal = if (small) 10.dp else 13.dp, vertical = if (small) 4.dp else 7.dp)) {
        Text(text, color = TextC, fontSize = if (small) 11.5.sp else 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A design drawn small: the real card at 0.6 of the screen's density, so it keeps a real row's proportions. */
@Composable
private fun DesignPreview(spec: StreamCards.Spec?, scale: Float) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) {
        if (spec == null) ClassicRowPreview(StreamCards.SAMPLE, "Torrentio")
        else {
            val c = remember(spec, Prefs.addonMark) { StreamCards.build(StreamCards.SAMPLE, "Torrentio", "The Dark Knight", spec, Prefs.addonMark) }
            ReleaseCardBody(c, spec, focused = false)
        }
    }
}

/** Settings › Streams › Stream cards: each design a tile with its live preview; Custom opens its parts below. */
@Composable
internal fun StreamCardDesigns() {
    val ctx = LocalContext.current
    val cur = Prefs.streamStyle.let { s -> if (StreamCards.DESIGNS.any { it.id == s }) s else "release" }
    SettingsHeader("STREAM CARDS", "How each stream in the list looks · Custom is yours to build")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        items(StreamCards.DESIGNS, key = { it.id }) { dsg ->
            val on = dsg.id == cur
            FocusCard(
                shape = RoundedCornerShape(16.dp), landing = false,
                onClick = { pickDesign(ctx, dsg.id, cur) },
            ) {
                Column(
                    Modifier.width(250.dp).background(SurfaceC, RoundedCornerShape(16.dp))
                        .border(if (on) 2.dp else 1.dp, if (on) Red else LineC, RoundedCornerShape(16.dp)).padding(10.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(10.dp)).background(BgPreview).padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { DesignPreview(if (dsg.id == "custom") StreamCards.decode(Prefs.streamCustom) else dsg.spec, 0.6f) }
                    Text(dsg.name, color = TextC, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 9.dp))
                    Text(dsg.sub, color = MutedC, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
    if (cur == "custom") {
        val c = StreamCards.decode(Prefs.streamCustom)
        Spacer(Modifier.height(12.dp))
        SettingsGroup {
            SettingsChips("Layout", "Stacked like Release, or one line per stream", listOf("stack" to "Stacked", "row" to "One line"), c.layout) {
                Prefs.setStreamCustom(ctx, StreamCards.encode(c.copy(layout = it)))
            }
            SettingsChips("Spacing", "Roomy, or less air and a smaller play button", listOf("roomy" to "Roomy", "compact" to "Compact"),
                if (c.dense) "compact" else "roomy") { Prefs.setStreamCustom(ctx, StreamCards.encode(c.copy(dense = it == "compact"))) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("Show", color = TextC, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Each part on or off", color = MutedC, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StreamCards.PARTS.forEach { (k, label) ->
                        Chip(label, c.has(k)) { Prefs.setStreamCustom(ctx, StreamCards.encode(c.with(k, !c.has(k)))) }
                    }
                }
                Box(Modifier.padding(top = 14.dp).fillMaxWidth()) { DesignPreview(c, 1f) }
            }
        }
    }
}

private val BgPreview = Color(0xFF000000)

/** The first Custom starts from the design that was showing, so it is a tweak, not a blank page. */
private fun pickDesign(ctx: Context, id: String, cur: String) {
    if (id == "custom" && Prefs.streamCustom.isEmpty())
        Prefs.setStreamCustom(ctx, StreamCards.encode(StreamCards.specFor(cur, "") ?: StreamCards.RELEASE))
    Prefs.setStreamStyle(ctx, id)
}

