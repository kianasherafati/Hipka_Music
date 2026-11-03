package com.hipka.app

import com.hipka.app.data.local.datastore.LocalPlaylists
import com.hipka.app.data.local.datastore.LocalPlaylist
import com.hipka.app.domain.model.Song
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlaylistOrderTest {
    private val songs = listOf("a", "b", "c").map { Song("audius:$it", it, "Artist", "", "") }
    @Test fun movePreservesMembershipAndClampsBoundaries() {
        assertEquals(listOf(songs[1], songs[0], songs[2]), LocalPlaylists.reorder(songs, "audius:b", -1))
        assertEquals(songs, LocalPlaylists.reorder(songs, "audius:a", -1))
        assertEquals(songs, LocalPlaylists.reorder(songs, "missing", 1))
        assertEquals(songs, LocalPlaylists.reorder(songs, "audius:c", 100))
    }
    @Test fun accountSwitchNeverExposesAnotherAccountsPlaylists() {
        val guest = LocalPlaylist(name = "Guest")
        val a = LocalPlaylist(name = "A", ownerId = "a")
        val b = LocalPlaylist(name = "B", ownerId = "b")
        val deleted = LocalPlaylist(name = "Deleted", deleted = true)
        val rows = listOf(guest, a, b, deleted)
        assertEquals(listOf(guest), LocalPlaylists.visibleFor(rows, null))
        assertEquals(listOf(guest, a), LocalPlaylists.visibleFor(rows, "a"))
        assertEquals(listOf(guest, b), LocalPlaylists.visibleFor(rows, "b"))
    }
    @Test fun serializationRetainsTrackIdsOrderAndDescription() {
        val playlist = LocalPlaylist(name = "Night drive", description = "My mix", songs = songs.reversed())
        assertEquals(playlist, Json.decodeFromString<LocalPlaylist>(Json.encodeToString(playlist)))
    }
}
