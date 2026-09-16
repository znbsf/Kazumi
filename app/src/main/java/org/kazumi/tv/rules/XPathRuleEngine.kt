package org.kazumi.tv.rules

import org.jsoup.Jsoup
import org.jsoup.helper.W3CDom
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/** HTML is parsed as HTML, never as an external-entity-capable XML document. */
class XPathRuleEngine {
    private fun nodes(node: Node, expression: String, relative: Boolean = false): List<Node> {
        // Each legacy result is its own document, including unions and text() selectors.
        val context = if (relative) {
            val scoped = node.ownerDocument.implementation.createDocument(null, null, null)
            scoped.appendChild(scoped.importNode(node, true))
            scoped.documentElement
        } else node
        val result = XPathFactory.newInstance().newXPath().evaluate(expression, context, XPathConstants.NODESET) as NodeList
        return List(result.length) { result.item(it) }
    }
    private fun document(html: String) = W3CDom().namespaceAware(false).fromJsoup(Jsoup.parse(html))
    private fun href(node: Node?): String = node?.attributes?.getNamedItem("href")?.nodeValue.orEmpty().trim()
    fun search(rule: SourceRule, html: String): List<SourceMatch> {
        rule.checkSupported()
        return nodes(document(html), rule.selector("searchList")).mapNotNull { row ->
            val title = nodes(row, rule.selector("searchName"), true).firstOrNull()?.textContent.orEmpty().trim()
            val link = href(nodes(row, rule.selector("searchResult"), true).firstOrNull())
            if (title.isBlank() || link.isBlank()) null else runCatching { SourceMatch(title, rule.resolve(link)) }.getOrNull()
        }.distinctBy { it.url }
    }
    fun chapters(rule: SourceRule, html: String): List<Road> {
        rule.checkSupported()
        return nodes(document(html), rule.selector("chapterRoads")).mapNotNull { row ->
            val episodes = nodes(row, rule.selector("chapterResult"), true).mapNotNull { item ->
                val link = href(item)
                if (link.isBlank()) null else runCatching {
                    Episode(item.textContent.trim().ifBlank { "未命名集数" }, rule.resolve(link))
                }.getOrNull()
            }.distinctBy { it.pageUrl }
            episodes.takeIf { it.isNotEmpty() }
        }.mapIndexed { index, episodes -> Road("播放线路 ${index + 1}", episodes) }
    }
}
