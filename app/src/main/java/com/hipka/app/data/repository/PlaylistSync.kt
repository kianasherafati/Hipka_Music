package com.hipka.app.data.repository

import io.github.jan.supabase.gotrue.auth
import com.hipka.app.data.local.datastore.LocalPlaylist
import com.hipka.app.data.local.datastore.LocalPlaylists
import com.hipka.app.data.local.datastore.SessionManager
import com.hipka.app.domain.model.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.http.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CloudPlaylist(val id: String, @SerialName("owner_id") val ownerId: String,
    val name: String, val description: String = "", val snapshot: List<Song> = emptyList(),
    val revision: Long, val deleted: Boolean = false, @SerialName("is_public") val isPublic: Boolean = false) {
    fun local() = LocalPlaylist(id, name, description, snapshot.filter { it.id.matches(Regex("audius:[A-Za-z0-9]+")) }.map {
        it.copy(audioUrl = "https://api.audius.co/v1/tracks/${it.id.removePrefix("audius:")}/stream?app_name=HipkaMusic", localFilePath = null, isDownloaded = false)
    }, ownerId, revision, dirty = false, deleted = deleted, isPublic = isPublic)
}
@Serializable
data class PlaylistWrite(val p_id: String, val p_name: String, val p_description: String,
    val p_snapshot: List<Song>, val p_revision: Long, val p_deleted: Boolean, val p_public: Boolean)
interface PlaylistSyncApi {
    @GET("rest/v1/hipka_playlists")
    suspend fun list(@Query("owner_id") owner: String, @Header("Authorization") authorization: String? = null): List<CloudPlaylist>
    @POST("rest/v1/rpc/hipka_save_playlist")
    suspend fun save(@Body write: PlaylistWrite, @Header("Authorization") authorization: String? = null): Long
}

/** Compare-and-swap snapshots. Concurrent edits become a preserved conflict copy. */
@Singleton
class PlaylistSync @Inject constructor(retrofit: Retrofit, private val local: LocalPlaylists, private val session: SessionManager, private val client: io.github.jan.supabase.SupabaseClient) {
    private val api = retrofit.create(PlaylistSyncApi::class.java)
    private val mutex = Mutex()
    suspend fun sync(): Boolean? = mutex.withLock {
        val owner = session.currentUserId.first() ?: return@withLock null
        val authenticated = client.auth.currentSessionOrNull() ?: error("Session refresh required")
        check(authenticated.user?.id == owner)
        val authorization = "Bearer ${authenticated.accessToken}"
        var conflict = false
        val pending = local.all.first().filter { it.ownerId == owner && it.dirty }
        for (row in pending) {
            try {
                val revision = api.save(PlaylistWrite(row.id, row.name, row.description,
                    row.songs.map { it.copy(localFilePath = null, isDownloaded = false, isLiked = false) }, row.revision, row.deleted, row.isPublic), authorization)
                local.mutate { rows -> rows.map { current ->
                    if (current.id != row.id) current else current.copy(revision = revision, dirty = current != row)
                } }
            } catch (error: HttpException) {
                if (error.code() != 409) throw error
                conflict = true
                // Do not silently replace an offline edit with a remote version.
                local.mutate { rows -> rows.flatMap { current ->
                    if (current.id != row.id) listOf(current)
                    else listOf(current.copy(dirty = false), current.copy(id = UUID.randomUUID().toString(),
                        name = (current.name.take(80) + " (conflict copy)"), revision = 0, dirty = true, deleted = false))
                } }
            }
        }
        val remote = api.list("eq.$owner", authorization)
        local.mutate { rows ->
            val byId = remote.associateBy { it.id }
            rows.map { row -> if (row.ownerId == owner && !row.dirty) byId[row.id]?.local() ?: row else row } +
                remote.filter { cloud -> rows.none { it.id == cloud.id } }.map { it.local() }
        }
        conflict
    }
}
