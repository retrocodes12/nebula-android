package com.nuvio.ckplayer

/**
 * Autoplay (Android TV; the shared player's "shelf mode"): Play on a film or an episode starts its best stream with no list.
 * The rules alone — which row is best, when to stop waiting for slower add-ons, a debrid clip in place of the film — pure,
 * so AutoplayTest pins them; the card, the run and the player's part live in AutoplayUi.kt.
 */
internal object Autoplay {
    /** The settings (Settings › Streams › Autoplay the best stream) with their defaults. [max] = the quality rank aimed for (4K = 4). */
    data class Cfg(
        val max: Int = 4,
        val instant: Boolean = true,
        val wait: String = "balanced",   // quick / balanced / all
        val retry: Int = 5,              // 0 / 3 / 5
        val startS: Int = 45,            // 20 / 45 / 90: seconds a pick has to show its first picture
        val back: String = "title",      // title / list
    )

    fun cfg(): Cfg = Cfg(
        max = when (Prefs.apMax) { 1080 -> 3; 720 -> 2; else -> 4 },
        instant = Prefs.apInstant, wait = Prefs.apWait, retry = Prefs.apRetry, startS = Prefs.apStart, back = Prefs.apBack,
    )

    /** A row of a debrid service: ready to play at once, not in the service's cloud yet, or a button for the account. */
    enum class Debrid { NONE, READY, WAIT, ACTION }

    // Comet names a file TorBox holds "[TB⚡] Comet 2160p" and one it would first fetch "[TB⬇️] …"; Torrentio-style add-ons
    // write "[TB+]" and "[TB download]"; "[TB🔄] Comet Sync" is a button, not a film
    private val RE_DEB = Regex("""\[[A-Za-z]{2,3} ?(⚡|\+|⬇|⏳|download)""", RegexOption.IGNORE_CASE)
    private val RE_TORBOX = Regex("""^\s*\[TB\b|torbox""", RegexOption.IGNORE_CASE)
    private val RE_DEB_ADDON = Regex("""\b(TB|RD|AD|PM)\b""")
    private val RE_DEB_WORD = Regex("""torbox|debrid|premiumize""", RegexOption.IGNORE_CASE)

    fun debrid(name: String, url: String): Debrid {
        if (name.contains("🔄") || url.contains("/debrid-sync/")) return Debrid.ACTION
        val m = RE_DEB.find(name) ?: return Debrid.NONE
        val mark = m.groupValues[1]
        return if (mark == "⚡" || mark == "+") Debrid.READY else Debrid.WAIT
    }
    fun debrid(s: StreamItem): Debrid = debrid(s.name, s.url)
    /** Never picked by itself: a debrid row that is not ready yet, or a button. */
    fun held(s: StreamItem): Boolean = debrid(s).let { it == Debrid.WAIT || it == Debrid.ACTION }
    /** 2 = ready in TorBox, 1 = ready in another service, 0 = any other row. */
    fun instant(s: StreamItem): Int {
        if (debrid(s) != Debrid.READY) return 0
        return if (RE_TORBOX.containsMatchIn(s.name)) 2 else 1
    }
    /** A debrid add-on by its name ("Comet | ElfHosted | TB", "Torrentio RD"): one still being asked may yet have a 4K. */
    fun debridAddon(name: String): Boolean = RE_DEB_ADDON.containsMatchIn(name) || RE_DEB_WORD.containsMatchIn(name)

    /** 4K = 4, 1080 = 3, 720 = 2, SD = 1, unknown = 0. */
    fun quality(s: StreamItem): Int = StreamBadges.resRank(s.name + "\n" + s.title)
    /** Quality as the pick sees it: a row above "Aim for" ranks just under the rows at it, and over the lower ones. */
    fun capped(q: Int, max: Int): Double = if (q > max) max - 0.5 else q.toDouble()

    private class Keyed(val row: Pair<StreamItem, Addon>, val i: Int, val k: DoubleArray)

    /**
     * Rows best first: [keepWaiting] false (the pick) drops held rows; true (the player's Streams list) sinks the not-ready
     * ones to the end and drops only buttons. Then ready-in-TorBox › another service (when [Cfg.instant]), quality capped at
     * "Aim for", one this connection carries, one this device decodes smoothly; ties keep the add-on order. Torrent rows
     * (they take most of a minute to find) are never the pick.
     */
    fun rank(
        rows: List<Pair<StreamItem, Addon>>, cfg: Cfg,
        slow: (StreamItem) -> Boolean, stutter: (StreamItem) -> Boolean, keepWaiting: Boolean = false,
    ): List<Pair<StreamItem, Addon>> = rows.asSequence()
        .filter { (s, _) -> !s.isTorrent && debrid(s) != Debrid.ACTION && (keepWaiting || !held(s)) }
        .mapIndexed { i, r ->
            val s = r.first
            Keyed(r, i, doubleArrayOf(
                if (held(s)) 0.0 else 1.0,
                if (cfg.instant) instant(s).toDouble() else 0.0,
                capped(quality(s), cfg.max),
                if (slow(s)) 0.0 else 1.0,
                if (stutter(s)) 0.0 else 1.0,
            ))
        }
        .sortedWith(Comparator<Keyed> { x, y ->
            for (j in x.k.indices) if (x.k[j] != y.k[j]) return@Comparator y.k[j].compareTo(x.k[j])
            x.i - y.i
        })
        .map { it.row }.toList()

    /** What the streams page does next while the add-ons answer. */
    enum class Verdict { WAIT, PICK, SETTLE, NONE }

    const val QUICK_MS = 8_000L
    const val BALANCED_MS = 25_000L
    const val CAP_MS = 90_000L

    /**
     * When to pick ("Wait for add-ons"): [best] = the best row so far (null = none), [asking] = the add-ons still being
     * asked, by name. PICK = now (the page waits 300 ms for a row arriving with it); SETTLE = every add-on answered (pick
     * after ~700 ms); NONE = nothing playable came, the list stays.
     * Quick: the best so far is instant or at the aimed quality, else at 8 s with whatever is there. Balanced: instant AND
     * at the aimed quality, or instant with no debrid add-on still asked; else at 25 s (nothing yet: on to 90 s). All: every
     * add-on (90 s at most).
     */
    fun verdict(cfg: Cfg, elapsedMs: Long, best: StreamItem?, asking: List<String>, allAnswered: Boolean): Verdict {
        if (allAnswered) return if (best == null) Verdict.NONE else Verdict.SETTLE
        if (elapsedMs >= CAP_MS) return if (best == null) Verdict.NONE else Verdict.PICK
        if (best == null) return Verdict.WAIT
        val inst = instant(best) > 0
        val at = quality(best) >= cfg.max
        return when (cfg.wait) {
            "quick" -> if (inst || at || elapsedMs >= QUICK_MS) Verdict.PICK else Verdict.WAIT
            "all" -> Verdict.WAIT
            else -> if ((inst && (at || asking.none { debridAddon(it) })) || elapsedMs >= BALANCED_MS) Verdict.PICK else Verdict.WAIT
        }
    }

    /** A debrid row that sent a few seconds instead of the film (Comet's 8 s "not ready" clip): never a start, never watched. */
    fun briefClip(name: String, url: String, type: String?, durationMs: Long): Boolean =
        debrid(name, url).let { it == Debrid.READY || it == Debrid.WAIT } &&
            (type == "movie" || type == "series") && durationMs in 1 until 30_000

    /** The next row to try after [failed] ones and the one that just failed ([cur]), from the ranked [queue]. */
    fun next(queue: List<Pair<StreamItem, Addon>>, cur: String, failed: Set<String>): Pair<StreamItem, Addon>? =
        queue.firstOrNull { it.first.url != cur && it.first.url !in failed }

    /** "4K" / "1080p" / "720p" / "SD" / "" — as the card and the Streams list say it. */
    fun qualityWord(s: StreamItem): String = when (StreamBadges.plate(s.name + "\n" + s.title)?.res) {
        "4K" -> "4K"; "1080" -> "1080p"; "720" -> "720p"; "SD" -> "SD"; else -> ""
    }

    /** The card's line for a start: "Starting 4K · Comet · instant" / "That one didn't play · trying 1080p · Comet". */
    fun startLine(s: StreamItem, addonName: String, why: String? = null): String {
        val lead = when (why) {
            null -> "Starting"
            "slow" -> "That one was slow to start · trying"
            "brief" -> "That one wasn’t ready · trying"
            else -> "That one didn’t play · trying"
        }
        val q = qualityWord(s)
        return lead + (if (q.isNotEmpty()) " $q" else "") + (if (addonName.isNotEmpty()) " · $addonName" else "") +
            (if (instant(s) > 0) " · instant" else "")
    }

    /** "Asking A and B for streams…" / "Found 12 streams · still asking A" / "Found 12 streams · picking the best". */
    fun askLine(found: Int, asking: List<String>): String {
        val who = when (asking.size) { 0 -> ""; 1 -> asking[0]; 2 -> asking[0] + " and " + asking[1]; else -> "${asking.size} add-ons" }
        return when {
            found > 0 -> "Found $found stream" + (if (found > 1) "s" else "") + (if (asking.isNotEmpty()) " · still asking $who" else " · picking the best")
            asking.isNotEmpty() -> "Asking $who for streams…"
            else -> "Finding the best stream…"
        }
    }
}
