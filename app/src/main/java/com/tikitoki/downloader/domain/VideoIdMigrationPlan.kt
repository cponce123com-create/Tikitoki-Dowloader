package com.tikitoki.downloader.domain

import java.util.Locale

/** Planning does not mutate the database; collisions must be handled before migration. */
internal object VideoIdMigrationPlan {
    data class OldRecord(val id: String, val url: String)
    data class Plan(
        val mapping: Map<String, String>,
        val collisions: Map<String, List<String>>,
        val unresolved: List<String>,
    )

    fun canonicalId(oldId: String, url: String): String? {
        val platform = MediaLinkParser.parse(url)?.platform ?: return null
        val prefix = platform.name.lowercase(Locale.ROOT)
        val raw = when {
            oldId.startsWith("$prefix:") -> oldId.removePrefix("$prefix:")
            oldId.startsWith("newpipe:") -> oldId.removePrefix("newpipe:")
            ':' !in oldId -> oldId
            else -> return null
        }
        if (raw.isBlank() || raw.any { it.isWhitespace() || it.isISOControl() || it == ':' }) return null
        return "$prefix:$raw"
    }

    fun build(records: List<OldRecord>): Plan {
        val mapping = linkedMapOf<String, String>()
        val unresolved = mutableListOf<String>()
        records.forEach { record ->
            val target = canonicalId(record.id, record.url)
            if (target == null) unresolved += record.id else mapping[record.id] = target
        }
        val collisions = mapping.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }
        return Plan(mapping, collisions, unresolved)
    }
}
