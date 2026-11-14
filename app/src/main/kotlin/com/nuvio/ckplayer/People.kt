package com.nuvio.ckplayer

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * People (1.115.0): the films and series a cast member, a director or a writer is credited on. A name on a title
 * page is pressed; Nebula Cloud answers `/v1/person?n=<name>&t=tt…` (it finds who the name is through that title,
 * from a public knowledge base, and keeps each answer a week); this process keeps it too, so a person costs one
 * request a run. The request carries the name and the title id and nothing else. Posters come from the catalogue's
 * image host by title id; a person is shown by name. Mirrors the shared player's personOpen.
 */
object People {
    class Item(val meta: MetaItem, val roles: Set<String>)
    class Person(val id: String, val name: String, val items: List<Item>)
    sealed interface Answer {
        class Found(val person: Person) : Answer
        /** Nobody by that name with film or series credits, or nothing to show. */
        data object None : Answer
        /** The cloud could not be reached, or could not answer: asked again on Try again or the next visit. */
        data object Failed : Answer
    }
    /** What a person's page keeps while a title opened from it is on top: the filter and where the grid stood. */
    class Ui { var kind by mutableStateOf("all"); val grid = LazyGridState() }

    private const val URL = "https://play.rifflehq.in/cloud/v1/person"
    private val TT = Regex("^tt\\d{5,10}$")
    private val cache = HashMap<String, Answer>()
    private val uis = HashMap<String, Ui>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)      // a person nobody asked for yet is two lookups on the cloud's side
        .build()

    val KINDS = listOf("all" to "All", "movie" to "Films", "series" to "Series", "director" to "Directed", "writer" to "Written")

    internal fun key(name: String, tt: String?) = name.trim().lowercase() + "|" + (tt ?: "")
    fun ui(name: String, tt: String?): Ui = synchronized(uis) {
        if (uis.size >= 24) uis.clear()
        uis.getOrPut(key(name, tt)) { Ui() }
    }

    internal fun matches(i: Item, kind: String) = kind == "all" || i.meta.type == kind || kind in i.roles
    /** The filters worth offering: All, and each one that would change something; none when nothing would. */
    internal fun kinds(items: List<Item>): List<Pair<String, String>> {
        val l = KINDS.filter { (k, _) -> k == "all" || items.count { matches(it, k) }.let { n -> n > 0 && n < items.size } }
        return if (l.size > 1) l else emptyList()
    }

    /** The cloud's answer as cards, or null when it is not one (pure, for the tests). */
    internal fun parse(body: String): Person? {
        val o = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val arr = o.optJSONArray("items") ?: return null
        val items = (0 until arr.length()).mapNotNull { i ->
            val x = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = x.optString("id")
            val name = x.optString("name")
            if (!TT.matches(id) || name.isEmpty()) return@mapNotNull null
            val type = if (x.optString("type") == "series") "series" else "movie"
            val year = x.optInt("year", 0)
            val roles = x.optJSONArray("as")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.toSet() } ?: emptySet()
            Item(
                MetaItem(id, type, name, "https://images.metahub.space/poster/medium/$id/img", releaseInfo = if (year > 0) year.toString() else null),
                roles,
            )
        }.distinctBy { it.meta.id }                 // the grid is keyed by title
        return Person(o.optString("id"), o.optString("name"), items)
    }

    fun cached(name: String, tt: String?): Answer? = synchronized(cache) { cache[key(name, tt)] }

    suspend fun load(name: String, tt: String?): Answer = withContext(Dispatchers.IO) {
        cached(name, tt)?.let { return@withContext it }
        val got: Answer = runCatching {
            val url = URL + "?n=" + Uri.encode(name) + (if (tt != null) "&t=" + Uri.encode(tt) else "")
            http.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                when {
                    r.code == 404 -> Answer.None
                    !r.isSuccessful -> Answer.Failed
                    else -> parse(r.body?.string() ?: "")?.let { p -> if (p.items.isEmpty()) Answer.None else Answer.Found(p) } ?: Answer.Failed
                }
            }
        }.getOrDefault(Answer.Failed)
        if (got !is Answer.Failed) synchronized(cache) {
            if (cache.size >= 200) cache.clear()
            cache[key(name, tt)] = got
        }
        got
    }
}

/** One person's films and series: the name, the filters that would change something, a grid of ordinary cards. */
@Composable
internal fun PersonScreen(name: String, tt: String?, onBack: () -> Unit, onOpen: (MetaItem) -> Unit) {
    val ctx = LocalContext.current
    val tv = remember(ctx) { Account.isTv(ctx) }
    val ui = remember(name, tt) { People.ui(name, tt) }
    var answer by remember(name, tt) { mutableStateOf(People.cached(name, tt)) }
    var tries by remember { mutableIntStateOf(0) }
    LaunchedEffect(name, tt, tries) {
        if (answer == null || tries > 0) { answer = null; answer = People.load(name, tt) }
    }
    val person = (answer as? People.Answer.Found)?.person
    val kinds = remember(person) { person?.let { People.kinds(it.items) } ?: emptyList() }
    val kind = if (kinds.any { it.first == ui.kind }) ui.kind else "all"
    val shown = remember(person, kind) { person?.items?.filter { People.matches(it, kind) } ?: emptyList() }
    val status = when (answer) {
        null -> "Loading…"
        is People.Answer.Found -> shown.size.toString() + if (shown.size == 1) " title" else " titles"
        else -> null
    }
    val chips: @Composable () -> Unit = {
        if (kinds.isNotEmpty()) LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = chipEdge()),
            modifier = Modifier.padding(bottom = 10.dp),
        ) {
            items(kinds) { (k, label) -> Chip(label, kind == k, modifier = Modifier.returnTo("person-kind/$k")) { ui.kind = k } }
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
        BackBar(name, status, onBack)
        when (answer) {
            is People.Answer.None -> Text("No films or series found for $name.", color = MutedC, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            is People.Answer.Failed -> {
                Text("Could not load the films and series of $name.", color = MutedC, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
                Chip("Try again", false, modifier = Modifier.returnTo("person-again")) { tries++ }
            }
            else -> {
                // the filters: on a TV they ride at the top of the grid and scroll away with it, a phone keeps them fixed
                if (!tv) chips()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp * Prefs.posterScale),
                    state = ui.grid,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                ) {
                    if (tv) item(key = "head", span = { GridItemSpan(maxLineSpan) }) { chips() }
                    if (answer == null) items(12) { SkeletonCell() }
                    else items(shown, key = { it.meta.id }) { i -> MetaCard(i.meta, Modifier.returnTo("p/${i.meta.id}")) { onOpen(i.meta) } }
                }
            }
        }
    }
}
