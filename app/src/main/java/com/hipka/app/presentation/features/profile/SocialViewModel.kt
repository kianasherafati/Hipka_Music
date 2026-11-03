package com.hipka.app.presentation.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hipka.app.data.local.datastore.SessionManager
import com.hipka.app.data.remote.api.*
import com.hipka.app.data.remote.dto.MessageDto
import com.hipka.app.data.remote.dto.UserDto
import com.hipka.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import javax.inject.Inject

@HiltViewModel
class SocialViewModel @Inject constructor(retrofit: Retrofit, private val users: UserRepository, private val chats: com.hipka.app.domain.repository.ChatRepository, session: SessionManager, private val songs: com.hipka.app.domain.repository.SongRepository, private val avatars: com.hipka.app.data.repository.AvatarUploader) : ViewModel() {
    private val api = retrofit.create(SocialApi::class.java)
    private val playlistApi = retrofit.create(com.hipka.app.data.repository.PlaylistSyncApi::class.java)
    val publicPlaylists = MutableStateFlow<List<com.hipka.app.data.repository.CloudPlaylist>>(emptyList())
    private val userApi = retrofit.create(UserApi::class.java)
    val me = session.currentUserId.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val profile = MutableStateFlow<UserDto?>(null)
    val results = MutableStateFlow<List<UserDto>>(emptyList())
    val messages = MutableStateFlow<List<MessageDto>>(emptyList())
    val following = MutableStateFlow<List<String>>(emptyList())
    val followers = MutableStateFlow<List<String>>(emptyList())
    val mineFollowing = MutableStateFlow<List<String>>(emptyList())
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    private var searchJob: Job? = null
    fun run(block: suspend () -> Unit) { viewModelScope.launch {
        busy.value = true
        try { block(); error.value = null }
        catch (e: Exception) { if (e is CancellationException) throw e; error.value = "Could not complete the request. Check your connection and retry." }
        finally { busy.value = false }
    } }
    fun load(id: String) = run {
        profile.value = userApi.getUserById("eq.$id", "*").firstOrNull()
        following.value = users.getFollowingIds(id); followers.value = users.getFollowerIds(id)
        me.value?.let { mineFollowing.value = users.getFollowingIds(it) }
        publicPlaylists.value = playlistApi.list("eq.$id").filter { it.isPublic && !it.deleted }
    }
    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            try {
                // Restrict PostgREST filter grammar; do not interpolate punctuation.
                val clean = query.trim().filter { it.isLetterOrDigit() || it == '_' || it == ' ' }.take(50)
                results.value = if (clean.isBlank()) emptyList() else api.search("(username.ilike.*$clean*,name.ilike.*$clean*)")
                error.value = null
            } catch (e: Exception) { if (e is CancellationException) throw e; error.value = "Search unavailable. Please retry." }
        }
    }
    suspend fun refreshInbox() { messages.value = api.inbox() }
    fun inbox() = run { refreshInbox() }
    fun follow(id: String) = run {
        val current = me.value ?: return@run
        if (id in mineFollowing.value) users.unfollowUser(current, id) else users.followUser(current, id)
        mineFollowing.value = users.getFollowingIds(current); followers.value = users.getFollowerIds(id)
    }
    fun edit(name: String, bio: String, avatar: String) = run {
        val current = me.value ?: return@run
        require(name.isNotBlank() && name.length <= 100 && bio.length <= 1000)
        require(avatar.isBlank() || avatar.startsWith("https://"))
        api.edit("eq.$current", ProfileEdit(name.trim(), bio, avatar.ifBlank { null }))
        profile.value = userApi.getUserById("eq.$current", "*").firstOrNull()
    }
    fun playPublic(playlist: com.hipka.app.data.repository.CloudPlaylist, ready: (List<com.hipka.app.domain.model.Song>) -> Unit) = run {
        val tracks = playlist.local().songs.mapNotNull { songs.getSongById(it.id) }
        require(tracks.isNotEmpty())
        ready(tracks)
    }
    fun uploadAvatar(uri: android.net.Uri) = run {
        val current = me.value ?: return@run
        val url = avatars.upload(current, uri)
        val user = userApi.getUserById("eq.$current", "*").first()
        api.edit("eq.$current", ProfileEdit(user.name, user.bio, url))
        profile.value = userApi.getUserById("eq.$current", "*").firstOrNull()
    }
    fun block(id: String, blocked: Boolean) = run {
        val current = me.value ?: return@run
        if (blocked) api.block(BlockUser(current, id)) else api.unblock("eq.$current", "eq.$id")
    }
    fun people(ids: List<String>) = run {
        results.value = if (ids.isEmpty()) emptyList() else userApi.getUsersByIds("in.(${ids.joinToString(",")})")
    }
    fun share(peer: String, song: com.hipka.app.domain.model.Song, done: () -> Unit) = run {
        val current = me.value ?: return@run
        chats.sendMessage(myUserId = current, receiverId = peer, text = song.title, sharedSongId = song.id)
        done()
    }
    fun logout() = run { users.logout() }
}
