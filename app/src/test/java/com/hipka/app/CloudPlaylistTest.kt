package com.hipka.app

import com.hipka.app.data.repository.CloudPlaylist
import com.hipka.app.domain.model.Song
import org.junit.Assert.*
import org.junit.Test

class CloudPlaylistTest {
    @Test fun cloudCannotInjectLocalFilesOrArbitraryPlaybackHosts() {
        val song = Song("audius:Ab123", "Title", "Artist", "", "https://untrusted.invalid/audio",
            isDownloaded = true, localFilePath = "/private/other-account/file")
        val result = CloudPlaylist("playlist", "account", "Name", snapshot = listOf(song), revision = 7).local()
        assertEquals(7L, result.revision)
        assertFalse(result.dirty)
        assertFalse(result.songs.single().isDownloaded)
        assertNull(result.songs.single().localFilePath)
        assertEquals("https://api.audius.co/v1/tracks/Ab123/stream?app_name=HipkaMusic", result.songs.single().audioUrl)
    }
    @Test fun malformedProviderIdsAreNotImported() {
        val invalid = Song("audius:../secret", "Title", "Artist", "", "")
        assertTrue(CloudPlaylist("p", "a", "Name", snapshot = listOf(invalid), revision = 1).local().songs.isEmpty())
    }
}
