package org.kazumi.tv.rules

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class XPathRuleEngineTest {
    private fun rule() = SourceRule(JSONObject("""{"name":"fixture","baseURL":"https://example.org/","searchURL":"https://example.org/search?wd=@keyword","searchList":"//li","searchName":"//a/text()","searchResult":"//a","chapterRoads":"//ul","chapterResult":"//a"}"""))
    @Test fun childSelectorsDoNotLeakAcrossRowsAndTextNodesWork() {
        val matches = XPathRuleEngine().search(rule(), "<ul><li><a href='/a'>甲</a></li><li><a href='b'>乙</a></li><li>无链接</li></ul>")
        assertEquals(listOf(SourceMatch("甲", "https://example.org/a"), SourceMatch("乙", "https://example.org/b")), matches)
    }
    @Test fun roadsKeepTheirOwnEpisodesAndSkipInvalidLinks() {
        val roads = XPathRuleEngine().chapters(rule(), "<ul><a href='/1'>第一集</a><a href='javascript:bad'>广告</a></ul><ul><a href='//cdn.example.org/2'>第二集</a></ul>")
        assertEquals(2, roads.size)
        assertEquals(listOf(Episode("第一集", "https://example.org/1")), roads[0].episodes)
        assertEquals("https://cdn.example.org/2", roads[1].episodes.single().pageUrl)
    }
    @Test fun unionSelectorsStayWithinResultSubtree() {
        val source = rule()
        source.json.put("searchName", "(//a/text() | //b/text())[1]")
        val matches = XPathRuleEngine().search(source, "<li><a href='/a'>甲</a></li><li><a href='/b'>乙</a></li>")
        assertEquals(listOf("甲", "乙"), matches.map { it.title })
    }
    @Test fun keywordIsEncodedAndUnknownFieldsAreRetained() {
        val source = rule()
        source.json.put("futureField", "keep")
        assertEquals("https://example.org/search?wd=%E7%94%B2+%26%E4%B9%99", source.searchUrl("甲 &乙"))
        assertEquals("keep", source.json.getString("futureField"))
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedModeIsNotEmptySuccess() {
        val source = rule()
        source.json.put("searchMode", "script")
        XPathRuleEngine().search(source, "<html></html>")
    }
    @Test(expected = javax.xml.xpath.XPathExpressionException::class) fun malformedSelectorFails() {
        val source = rule()
        source.json.put("searchList", "[[")
        XPathRuleEngine().search(source, "<html></html>")
    }
}
