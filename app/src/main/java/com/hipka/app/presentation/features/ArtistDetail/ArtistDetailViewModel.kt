package com.hipka.app.presentation.features.ArtistDetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hipka.app.domain.repository.SongRepository
import com.hipka.app.presentation.features.ArtistDetail.ArtistDetailIntent
import com.hipka.app.presentation.features.ArtistDetail.ArtistDetailUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject



@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val songRepository: SongRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    fun onIntent(intent: ArtistDetailIntent) {
        when (intent) {
            is ArtistDetailIntent.LoadArtistData -> loadArtistSongs(intent.artistName, intent.imageUrl, intent.artistId)
        }
    }

    private fun loadArtistSongs(artistName: String, imageUrl: String, artistId: String) {
        _uiState.update { it.copy(artistName = artistName, imageUrl = imageUrl, isLoading = true) }
        viewModelScope.launch {
            val songs = try { songRepository.getSongsByArtist(if (artistId.isBlank()) artistName else "audius-artist:$artistId") } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, errorMessage = "Unable to load artist. Please try again.") }; return@launch
            }
            _uiState.update {
                it.copy(
                    songs = songs,
                    isLoading = false,
                    errorMessage = if (songs.isEmpty()) "هیچ آهنگی یافت نشد" else null
                )
            }
        }
    }
}