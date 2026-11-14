package com.nuvio.ckplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A person's films and series: the cloud's answer as cards, and the filters worth offering. */
class PeopleTest {
    private val body = """{"id":"Q500","name":"John Smith","items":[
        {"id":"tt0000201","name":"The Big One","year":1994,"type":"movie","as":["cast"]},
        {"id":"tt0000202","name":"A Show","year":2008,"type":"series","as":["cast","director"]},
        {"id":"tt0000201","name":"The Big One again","year":1994,"type":"movie","as":["cast"]},
        {"id":"nm0000001","name":"Not A Title","year":2012,"type":"movie","as":["cast"]},
        {"id":"tt0000203","name":"","year":2001,"type":"movie","as":["writer"]},
        {"id":"tt0000204","name":"Undated","year":null,"type":"movie","as":["director","writer"]},
        {"id":"tt0000205","name":"Odd Type","year":2010,"type":"game"}
    ]}"""

    @Test fun parse_keepsTitlesWithAnIdAndAName_onceEach_withPosterAndYear() {
        val p = People.parse(body)!!
        assertEquals("Q500", p.id); assertEquals("John Smith", p.name)
        assertEquals(listOf("tt0000201", "tt0000202", "tt0000204", "tt0000205"), p.items.map { it.meta.id })
        val first = p.items[0].meta
        assertEquals("The Big One", first.name)                 // the first of two with the same id
        assertEquals("movie", first.type); assertEquals("1994", first.releaseInfo)
        assertEquals("https://images.metahub.space/poster/medium/tt0000201/img", first.poster)
        assertEquals("series", p.items[1].meta.type)
        assertEquals(setOf("cast", "director"), p.items[1].roles)
        assertNull(p.items[2].meta.releaseInfo)                 // no year: no line under the name
        assertEquals("movie", p.items[3].meta.type)             // anything that is not a series opens as a film
        assertEquals(emptySet<String>(), p.items[3].roles)
    }

    @Test fun parse_refusesWhatIsNotAnAnswer() {
        assertNull(People.parse(""))
        assertNull(People.parse("<html>502</html>"))
        assertNull(People.parse("""{"error":"unknown"}"""))
        assertEquals(0, People.parse("""{"id":"Q1","name":"X","items":[]}""")!!.items.size)
    }

    @Test fun kinds_onlyTheFiltersThatWouldChangeSomething() {
        val p = People.parse(body)!!
        // films 3 of 4, series 1, directed 2, written 1: every one of them narrows the four
        assertEquals(listOf("all", "movie", "series", "director", "writer"), People.kinds(p.items).map { it.first })
        // only films, all as cast: nothing to choose between, so no row at all
        val films = People.parse("""{"id":"Q2","name":"Y","items":[
            {"id":"tt0000301","name":"One","year":2001,"type":"movie","as":["cast"]},
            {"id":"tt0000302","name":"Two","year":2002,"type":"movie","as":["cast"]}]}""")!!
        assertEquals(emptyList<Pair<String, String>>(), People.kinds(films.items))
        // a director who also acted in one of them: Directed would change nothing (all), Films neither
        val dir = People.parse("""{"id":"Q3","name":"Z","items":[
            {"id":"tt0000401","name":"One","year":2001,"type":"movie","as":["director"]},
            {"id":"tt0000402","name":"Two","year":2002,"type":"series","as":["director","writer"]}]}""")!!
        assertEquals(listOf("all", "movie", "series", "writer"), People.kinds(dir.items).map { it.first })
    }

    @Test fun matches_byKindOfTitleOrByWhatTheyDidOnIt() {
        val p = People.parse(body)!!
        assertEquals(4, p.items.count { People.matches(it, "all") })
        assertEquals(listOf("tt0000202"), p.items.filter { People.matches(it, "series") }.map { it.meta.id })
        assertEquals(listOf("tt0000202", "tt0000204"), p.items.filter { People.matches(it, "director") }.map { it.meta.id })
        assertEquals(listOf("tt0000204"), p.items.filter { People.matches(it, "writer") }.map { it.meta.id })
    }

    @Test fun key_aNameOnATitleIsNotTheNameAlone() {
        assertEquals("john smith|tt0000001", People.key(" John Smith ", "tt0000001"))
        assertEquals("john smith|", People.key("John Smith", null))
    }
}
