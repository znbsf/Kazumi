package org.kazumi.tv.rules

import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory
import org.w3c.dom.NodeList

class SourceVerificationRequired(val pageUrl: String) : IllegalStateException("来源需要网页验证，请完成后重试")

object SourcePageChecks {
    fun looksLikeChallengeTitle(title: String): Boolean = title.trim().lowercase().let { value ->
        value in listOf("系统安全验证", "安全验证", "人机验证", "just a moment...", "just a moment…", "security verification")
    }
    fun check(rule: SourceRule, html: String, pageUrl: String): String {
        if (!html.trimStart().startsWith("<")) return html
        val doc = Jsoup.parse(html)
        var challenge = looksLikeChallengeTitle(doc.title())
        val config = rule.json.optJSONObject("antiCrawlerConfig")
        if (config?.optBoolean("enabled") == true) {
            val value = config.optString("captchaDetectValue")
            if (value.isNotBlank()) challenge = challenge || when (config.optInt("captchaDetectType",1)) {
                1 -> try {
                    val xml = W3CDom().namespaceAware(false).fromJsoup(doc)
                    (XPathFactory.newInstance().newXPath().evaluate(value,xml,XPathConstants.NODESET) as NodeList).length > 0
                } catch (_: Exception) { throw IllegalArgumentException("验证检测 XPath 无效") }
                2 -> html.contains(value)
                else -> throw IllegalArgumentException("此规则的验证检测方式尚未适配")
            }
        }
        if (challenge) throw SourceVerificationRequired(pageUrl)
        return html
    }
}
