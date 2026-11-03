package com.hipka.app.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hipka.app.domain.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playlistStore by preferencesDataStore("hipka_local_playlists_v2")

@Serializable
data class LocalPlaylist(val id: String = UUID.randomUUID().toString(), val name: String,
    val description: String = "", val songs: List<Song> = emptyList(),
    val ownerId: String? = null, val revision: Long = 0, val dirty: Boolean = true, val deleted: Boolean = false, val isPublic: Boolean = false)

/** Separate from the existing Room database: no destructive library migration. */
@Singleton
class LocalPlaylists @Inject constructor(@ApplicationContext private val context: Context, private val session: SessionManager) {
    private val key = stringPreferencesKey("playlists")
    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(value: String?) = value?.let { json.decodeFromString<List<LocalPlaylist>>(it) } ?: emptyList()
    internal val all = context.playlistStore.data.map { decode(it[key]) }
    val playlists = combine(all, session.currentUserId) { rows, user -> visibleFor(rows, user) }
    suspend fun create(name: String, description: String = "", songs: List<Song> = emptyList()) {
        require(name.isNotBlank() && name.length <= 100)
        val owner = session.currentUserId.first()
        mutate { it + LocalPlaylist(name = name.trim(), description = description.take(1000), songs = songs.distinctBy(Song::id), ownerId = owner) }
    }
    suspend fun rename(id: String, name: String, description: String) {
        require(name.isNotBlank() && name.length <= 100)
        mutate { rows -> rows.map { if (it.id == id) it.copy(name = name.trim(), description = description.take(1000), dirty = true) else it } }
    }
    suspend fun setPublic(id: String, value: Boolean) {
        val owner = session.currentUserId.first() ?: error("Sign in required")
        mutate { rows -> rows.map { if (it.id == id && it.ownerId == owner) it.copy(isPublic = value, dirty = true) else it } }
    }
    suspend fun delete(id: String) = mutate { rows -> rows.map { if (it.id == id) it.copy(deleted = true, dirty = true) else it } }
    suspend fun add(id: String, song: Song) = update(id) { require(it.size < 1000 || it.any { saved -> saved.id == song.id }); (it + song).distinctBy(Song::id) }
    suspend fun remove(id: String, songId: String) = update(id) { it.filterNot { song -> song.id == songId } }
    suspend fun move(id: String, songId: String, delta: Int) = update(id) { reorder(it, songId, delta) }
    private suspend fun update(id: String, change: (List<Song>) -> List<Song>) = mutate { rows ->
        rows.map { if (it.id == id) it.copy(songs = change(it.songs), dirty = true) else it }
    }
    internal suspend fun mutate(change: (List<LocalPlaylist>) -> List<LocalPlaylist>) {
        context.playlistStore.edit { it[key] = json.encodeToString(change(decode(it[key]))) }
    }
    companion object {
        fun visibleFor(rows: List<LocalPlaylist>, owner: String?): List<LocalPlaylist> =
            rows.filter { !it.deleted && (it.ownerId == null || it.ownerId == owner) }

        fun reorder(songs: List<Song>, id: String, delta: Int): List<Song> {
            val index = songs.indexOfFirst { it.id == id }
            if (index < 0) return songs
            val target = (index + delta).coerceIn(0, songs.lastIndex)
            return songs.toMutableList().apply { add(target, removeAt(index)) }
        }
    }
}
