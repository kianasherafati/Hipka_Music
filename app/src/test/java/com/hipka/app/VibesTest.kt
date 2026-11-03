package com.hipka.app

import com.hipka.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VibesTest {
    private fun song(id: String, genre: String = "", plays: Int = 0) = Song("audius:$id", id, "Artist", "", "", genre = genre, playCount = plays)
    @Test fun metadataOutranksPopularityAndNeverInventsTracks() {
        val calm = song("calm", "Ambient", 1)
        val popular = song("dance", "House", 100000)
        val mix = Vibes.mix(listOf(popular, calm), Vibe.Calm)
        assertEquals(listOf(calm), mix.songs)
        assertFalse(mix.usedFallback)
    }
    @Test fun fallbackIsStableAndDeduplicated() {
        val input = listOf(song("b"), song("a"), song("a"))
        val mix = Vibes.mix(input, Vibe.Focus)
        assertTrue(mix.usedFallback)
        assertEquals(listOf("audius:a", "audius:b"), mix.songs.map { it.id })
        assertEquals(mix, Vibes.mix(input.reversed(), Vibe.Focus))
    }
    @Test fun cachedHistoryWithoutArtistIdsStillPersonalizesFreshTracks() {
        val preferred = song("preferred", "Ambient", 1).copy(artistId = "stable-id", artistName = "Preferred")
        val popular = song("popular", "Ambient", 1000).copy(artistName = "Other")
        val history = preferred.copy(id = "audius:old", artistId = "")
        assertEquals(preferred, Vibes.mix(listOf(popular, preferred), Vibe.Calm, listOf(history)).songs.first())
    }
    @Test fun emptyAndNonProviderDataCannotProduceFakeMusic() {
        assertTrue(Vibes.mix(emptyList(), Vibe.Calm).songs.isEmpty())
        assertTrue(Vibes.mix(listOf(song("a").copy(id = "legacy")), Vibe.Calm).songs.isEmpty())
    }
}
