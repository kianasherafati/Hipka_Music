package com.hipka.app.presentation.features.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hipka.app.data.local.datastore.LocalPlaylists
import com.hipka.app.domain.model.Song
import com.hipka.app.domain.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistsViewModel @Inject constructor(val store: LocalPlaylists, private val songs: SongRepository, private val sync: com.hipka.app.data.repository.PlaylistSync, private val network: com.hipka.app.core.network.NetworkMonitor) : ViewModel() {
    val syncStatus = MutableStateFlow<String?>(null)
    init { viewModelScope.launch { network.isOnline.distinctUntilChanged().collect { if (it) synchronize() } } }
    fun synchronize() { viewModelScope.launch {
        try {
            val conflict = sync.sync()
            syncStatus.value = if (conflict == null) "Device playlists are saved locally. Sign in to create account playlists." else if (conflict) "A concurrent edit was preserved as a conflict copy. Sync again to upload it." else "Account playlists synced. Device playlists stay on this device."
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            syncStatus.value = "Changes are saved on this device. Account sync will retry when connected or when you tap Sync."
        }
    } }
    val error = MutableStateFlow<String?>(null)
    val playlists = store.playlists.catch { error.value = "Could not read playlists. Your saved data has not been replaced." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val library = songs.getSongs().catch { error.value = "Music is unavailable. Try again when connected." }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun act(block: suspend () -> Unit) { viewModelScope.launch {
        try { block(); error.value = null; synchronize() } catch (e: Exception) {
            if (e is CancellationException) throw e
            error.value = "Could not save your change. Please try again."
        }
    } }
    fun play(playlist: List<Song>, onReady: (List<Song>) -> Unit) = act {
        // Refresh local-file state: deleting a download must not leave stale paths in playlists.
        val resolved = playlist.map { songs.getSongById(it.id) ?: it.copy(isDownloaded = false, localFilePath = null) }
        onReady(resolved)
    }
}
