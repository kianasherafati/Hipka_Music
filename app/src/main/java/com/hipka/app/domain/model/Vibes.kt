package com.hipka.app.domain.model

enum class Vibe(val keywords: Set<String>) {
    Calm(setOf("ambient", "chill", "downtempo")),
    Focus(setOf("instrumental", "lo-fi", "classical")),
    Energetic(setOf("dance", "house", "electronic", "drum")),
    Dreamy(setOf("ambient", "dream", "synth")),
    NightDrive(setOf("synth", "wave", "techno", "deep"))
}

data class VibeMix(val songs: List<Song>, val usedFallback: Boolean)

object Vibes {
    /** Stable provider IDs break ties; history adds a small artist preference. */
    fun mix(candidates: List<Song>, vibe: Vibe, recent: List<Song> = emptyList()): VibeMix {
        val artists = recent.flatMap { listOf(it.artistId, it.artistName) }.filter { it.isNotBlank() }.toSet()
        fun matches(song: Song) = vibe.keywords.count { word -> "${song.genre} ${song.tags}".contains(word, true) }
        val unique = candidates.filter { it.id.startsWith("audius:") }.distinctBy { it.id }
        val matching = unique.filter { matches(it) > 0 }
        val pool = matching.ifEmpty { unique }
        return VibeMix(pool.sortedWith(compareByDescending<Song> { matches(it) * 10 +
            if (it.artistId in artists || it.artistName in artists) 2 else 0 }
            .thenByDescending { it.playCount }.thenBy { it.id }).take(25), matching.isEmpty())
    }
}
