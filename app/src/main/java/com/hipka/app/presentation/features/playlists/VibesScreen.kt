package com.hipka.app.presentation.features.playlists

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hipka.app.R
import com.hipka.app.domain.model.*
import com.hipka.app.domain.repository.SongRepository
import com.hipka.app.data.local.datastore.LocalPlaylists
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VibesViewModel @Inject constructor(private val repository: SongRepository, private val playlists: LocalPlaylists) : ViewModel() {
    val mix = MutableStateFlow(VibeMix(emptyList(), false))
    val mood = MutableStateFlow(Vibe.Calm)
    val busy = MutableStateFlow(false)
    val feedback = MutableStateFlow<String?>(null)
    private var job: kotlinx.coroutines.Job? = null
    init { generate(Vibe.Calm) }
    fun generate(vibe: Vibe) {
        job?.cancel()
        job = viewModelScope.launch {
            busy.value = true; mood.value = vibe; feedback.value = null
            try {
                val candidates = repository.getSongs().last()
                mix.value = Vibes.mix(candidates, vibe, repository.getRecentlyPlayedSongs().first())
                if (mix.value.songs.isEmpty()) feedback.value = "No music available. Connect and refresh."
                else if (mix.value.usedFallback) feedback.value = "Mood metadata is limited. Showing trending music."
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                feedback.value = "Could not load music. Please retry."
            } finally { busy.value = false }
        }
    }
    fun save() { viewModelScope.launch {
        try { playlists.create("Hipka Vibes - ${mood.value.name}", songs = mix.value.songs); feedback.value = "Mix saved to Playlists." }
        catch (e: Exception) { if (e is CancellationException) throw e; feedback.value = "Could not save mix. Please retry." }
    } }
}

@Composable
fun VibesScreen(onPlay: (List<Song>, Song) -> Unit, vm: VibesViewModel = hiltViewModel()) {
    val mix by vm.mix.collectAsStateWithLifecycle()
    val mood by vm.mood.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Hipka Vibes", style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.v2_vibes_note))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(Vibe.entries) { vibe ->
            FilterChip(selected = vibe == mood, onClick = { vm.generate(vibe) }, label = { Text(vibeLabel(vibe)) })
        } }
        feedback?.let { Text(it) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row {
            TextButton(onClick = { vm.generate(mood) }) { Text(stringResource(R.string.v2_refresh)) }
            TextButton(enabled = mix.songs.isNotEmpty() && !busy, onClick = { onPlay(mix.songs, mix.songs.first()) }) { Text(stringResource(R.string.v2_play_all)) }
            TextButton(enabled = mix.songs.isNotEmpty() && !busy, onClick = vm::save) { Text(stringResource(R.string.v2_save_mix)) }
        }
        LazyColumn { items(mix.songs, key = { it.id }) { song ->
            TextButton(onClick = { onPlay(mix.songs, song) }) { Text("${song.title} - ${song.artistName}") }
        } }
    }
}

@Composable
fun vibeLabel(vibe: Vibe): String = stringResource(when (vibe) {
    Vibe.Calm -> R.string.mood_calm
    Vibe.Focus -> R.string.mood_focus
    Vibe.Energetic -> R.string.mood_energetic
    Vibe.Dreamy -> R.string.mood_dreamy
    Vibe.NightDrive -> R.string.mood_night_drive
})
