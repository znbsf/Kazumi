package org.kazumi.tv.domain

import org.kazumi.tv.rules.Episode

/** A source page is stable within a rule. Cross-road matching requires one unambiguous label. */
object PlaybackIdentity {
    /** Page identity is scoped to a rule; cross-source matching uses only unique normal episode labels. */
    fun matchingAcrossSources(current:Episode,candidates:List<Episode>):Int? {
        val number=EpisodeNumber.parse(current.title) ?: return null
        return candidates.indices.filter { EpisodeNumber.parse(candidates[it].title)==number }.singleOrNull()
    }
    fun matchingEpisode(current: Episode, candidates: List<Episode>): Int? {
        val exact = candidates.indices.filter { candidates[it].pageUrl == current.pageUrl }
        if (exact.size == 1) return exact.single()
        val number = EpisodeNumber.parse(current.title) ?: return null
        return candidates.indices.filter { EpisodeNumber.parse(candidates[it].title) == number }.singleOrNull()
    }
}
