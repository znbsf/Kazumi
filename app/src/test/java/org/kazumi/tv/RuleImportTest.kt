package org.kazumi.tv

import org.junit.Test
import org.junit.Assert.*
import org.json.*
import java.util.Base64
import org.kazumi.tv.rules.*

class RuleImportTest {
    private fun valid() = JSONObject().put("name","fixture").put("baseURL","https://example.org/").put("api","8")
        .put("searchURL","https://example.org/search?q=@keyword").put("searchList","//li").put("searchName","//a")
        .put("searchResult","//a").put("chapterRoads","//ul").put("chapterResult","//a")
    @Test fun shareUrlSafeAndPercentEncodedDecode() {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(valid().toString().toByteArray())
        assertEquals("fixture", RuleImport.parse("kazumi://$encoded").rules.single().name)
        val standard = Base64.getEncoder().encodeToString(valid().toString().toByteArray()).replace("=", "%3D").replace("+", "%2B")
        assertEquals(1, RuleImport.parse("kazumi:$standard").rules.size)
    }
    @Test fun batchRetainsGoodRulesAndRejectsNewerApiOrMissingChapter() {
        val raw = JSONArray().put(valid()).put(valid().put("api", "9")).put(valid().apply { remove("chapterResult") }).toString()
        val result = RuleImport.parse(raw)
        assertEquals(1, result.rules.size); assertEquals(2, result.failures.size)
    }
    @Test fun duplicatesCaseInsensitiveAndLastWins() {
        val result = RuleImport.parse(JSONArray().put(valid().put("version","1")).put(valid().put("name","FIXTURE").put("version","2")).toString())
        assertEquals(1,result.duplicates); assertEquals("2",result.rules.single().json.getString("version"))
    }
    @Test fun incompatibleModesNeverCountAsImported() {
        for (rule in listOf(valid().put("searchMode","script"),valid().put("api","garbage"))) {
            assertTrue(RuleImport.parse(rule.toString()).rules.isEmpty())
        }
    }
    @Test fun legacyParserIsImportable() {
        assertEquals(1, RuleImport.parse(valid().put("useLegacyParser",true).toString()).rules.size)
    }
    @Test fun supportsRulesEnvelopeAndRejectsOversize() {
        assertEquals(1,RuleImport.parse(JSONObject().put("rules",JSONArray().put(valid())).toString()).rules.size)
        assertThrows(IllegalArgumentException::class.java) { RuleImport.parse("x".repeat(RuleImport.MAX_CHARS+1)) }
    }
}
