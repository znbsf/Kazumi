package org.kazumi.tv.rules

import org.json.*
import org.junit.Assert.*
import org.junit.Test

class ApiRuleEngineTest {
    private val api = ApiRuleEngine()
    private val rule = SourceRule(JSONObject("""{"name":"test","baseURL":"https://example.org/"}"""))
    @Test fun pathsHandleArraysKeysAndMissingValues() {
        val root = JSONObject("""{"data":{"a.b":[{"id":4},{"id":9}]}}""")
        assertEquals(listOf(4, 9), JsonPath.read(root, "$.data['a.b'][*].id"))
        assertEquals(emptyList<Any>(), JsonPath.read(root, "$.missing[*]"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsRecursivePath() { JsonPath.read(JSONObject(), "$..id") }
    @Test fun requestEncodesUrlAndPreservesJsonTypes() {
        val config = JSONObject("""{"url":"https://example.org/search/@keyword","method":"POST","bodyType":"json","body":{"page":"@page","q":"@keyword"}}""")
        val request = api.request(config, mapOf("keyword" to "甲 &乙", "page" to 3))
        assertTrue(request.url.contains("%E7%94%B2%20%26%E4%B9%99"))
        assertEquals(3, JSONObject(request.body!!).getInt("page"))
        assertEquals("甲 &乙", JSONObject(request.body).getString("q"))
    }
    @Test fun searchKeepsOpaqueSourceIdsForChapterRequests() {
        assertEquals(listOf(SourceMatch("节目", "42")), api.search(JSONObject(), """{"data":[{"name":"节目","url":42}]}"""))
    }
    @Test fun explicitQueryOverridesExistingValue() {
        val config = JSONObject("""{"url":"https://example.org/search?page=1&keep=x","query":{"page":"@page"}}""")
        val request = api.request(config, mapOf("page" to 2))
        assertEquals("https://example.org/search?page=2&keep=x", request.url)
    }
    @Test fun nestedRoadsAndEpisodePageVariables() {
        val config = JSONObject("""{"variables":{"id":"$.id"},"episodePage":{"url":"/watch/@id/@roadNumber/@episodeNumber"}}""")
        val roads = api.chapters(rule, config, """{"id":25,"data":{"roads":[{"name":"A","episodes":[{"name":"一","url":"/raw"},{"name":"二","url":"/raw2"}]}]}}""", "source")
        assertEquals("https://example.org/watch/25/1/2", roads.single().episodes[1].pageUrl)
    }
    @Test fun delimitedSourcesRetainUrlDollarCharacters() {
        val config = JSONObject().put("format", "delimited").put("roadNamesPath", "$.names").put("roadEpisodesPath", "$.urls")
        val raw = JSONObject().put("names", listOf("A", "B").joinToString("$$$")).put("urls", listOf("一" + "$" + "/one?x=" + "$" + "value", "二" + "$" + "/two").joinToString("$$$")).toString()
        val roads = api.chapters(rule, config, raw, "source")
        assertEquals(2, roads.size)
        assertEquals("https://example.org/one?x=" + "$" + "value", roads[0].episodes[0].pageUrl)
    }
    @Test(expected = IllegalArgumentException::class) fun unknownVariableFailsBeforeRequest() { api.template("https://example.org/@missing", emptyMap()) }
}
