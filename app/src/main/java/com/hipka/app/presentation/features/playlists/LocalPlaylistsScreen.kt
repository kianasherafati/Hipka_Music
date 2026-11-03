package com.hipka.app.presentation.features.playlists

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hipka.app.R
import com.hipka.app.domain.model.Song

@Composable
fun LocalPlaylistsScreen(onPlay: (List<Song>, Song) -> Unit, onVibes: () -> Unit,
    viewModel: LocalPlaylistsViewModel = hiltViewModel()) {
    val rows by viewModel.playlists.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val playlist = rows.firstOrNull { it.id == selected }
    val context = LocalContext.current
    androidx.activity.compose.BackHandler(selected != null) { selected = null }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(playlist?.name ?: stringResource(R.string.nav_playlists), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.local_playlist_note), style = MaterialTheme.typography.bodySmall)
        syncStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = viewModel::synchronize) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.sync_playlists)) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (playlist == null) {
            Row {
                Button(onClick = { selected = null; name = ""; description = ""; editing = true }) { Text(stringResource(R.string.create_playlist)) }
                TextButton(onClick = onVibes) { Text("Hipka Vibes") }
            }
            if (rows.isEmpty()) Text(stringResource(R.string.playlist_empty_hint))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (library.isNotEmpty()) {
                    item { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.editorial_mixes), style = MaterialTheme.typography.titleLarge) }
                    items(com.hipka.app.domain.model.Vibe.entries, key = { "editorial-${it.name}" }) { vibe ->
                        val mix = com.hipka.app.domain.model.Vibes.mix(library, vibe)
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("Hipka ${vibe.name}", style = MaterialTheme.typography.titleMedium)
                                Text(if (mix.usedFallback) androidx.compose.ui.res.stringResource(com.hipka.app.R.string.mood_fallback) else androidx.compose.ui.res.stringResource(com.hipka.app.R.string.mood_selection))
                                Row {
                                    TextButton(enabled = mix.songs.isNotEmpty(), onClick = { viewModel.play(mix.songs) { onPlay(it, it.first()) } }) { Text(stringResource(R.string.v2_play_all)) }
                                    TextButton(enabled = mix.songs.isNotEmpty(), onClick = { viewModel.act { viewModel.store.create("Hipka ${vibe.name}", songs = mix.songs) } }) { Text(stringResource(R.string.v2_save_mix)) }
                                }
                            }
                        }
                    }
                }

                items(rows, key = { it.id }) { row ->
                    Card(Modifier.fillMaxWidth().clickable { selected = row.id }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(row.name, style = MaterialTheme.typography.titleLarge)
                            Text(if (row.ownerId == null) androidx.compose.ui.res.stringResource(com.hipka.app.R.string.on_device) else androidx.compose.ui.res.stringResource(com.hipka.app.R.string.account_playlist))
                            Text(row.description)
                            Text(stringResource(R.string.playlist_track_count, row.songs.size))
                        }
                    }
                }
            }
        } else {
            Text(playlist.description)
            if (playlist.ownerId != null) TextButton(onClick = { viewModel.act { viewModel.store.setPublic(playlist.id, !playlist.isPublic) } }) {
                Text(stringResource(if (playlist.isPublic) R.string.make_private else R.string.publish_playlist))
            }
            Row {
                TextButton(onClick = { selected = null }) { Text(stringResource(R.string.v2_back)) }
                TextButton(enabled = playlist.songs.isNotEmpty(), onClick = { viewModel.play(playlist.songs) { onPlay(it, it.first()) } }) { Text(stringResource(R.string.v2_play_all)) }
                TextButton(enabled = playlist.songs.isNotEmpty(), onClick = { viewModel.play(playlist.songs.shuffled()) { onPlay(it, it.first()) } }) { Text(stringResource(R.string.v2_shuffle)) }
            }
            Row {
                TextButton(onClick = { adding = true }) { Text(stringResource(R.string.v2_add_tracks)) }
                TextButton(onClick = { name = playlist.name; description = playlist.description; editing = true }) { Text(stringResource(R.string.v2_edit)) }
                TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.v2_delete)) }
                TextButton(onClick = {
                    val text = playlist.name + "\n" + playlist.songs.joinToString("\n") { "${it.title} - ${it.artistName}\nhttps://api.audius.co/v1/tracks/${it.id.removePrefix("audius:")}" }
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                }) { Text(stringResource(R.string.v2_share)) }
            }
            LazyColumn {
                items(playlist.songs, key = { it.id }) { song ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(song.title, Modifier.clickable { viewModel.play(playlist.songs) { resolved -> onPlay(resolved, resolved.first { it.id == song.id }) } }, style = MaterialTheme.typography.titleMedium)
                        Text(song.artistName)
                        Row {
                            TextButton(onClick = { viewModel.act { viewModel.store.move(playlist.id, song.id, -1) } }) { Text(stringResource(R.string.v2_move_up)) }
                            TextButton(onClick = { viewModel.act { viewModel.store.move(playlist.id, song.id, 1) } }) { Text(stringResource(R.string.v2_move_down)) }
                            TextButton(onClick = { viewModel.act { viewModel.store.remove(playlist.id, song.id) } }) { Text(stringResource(R.string.v2_remove)) }
                        }
                    }
                }
            }
        }
    }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text(stringResource(R.string.nav_playlists)) }, text = {
        Column {
            OutlinedTextField(name, { name = it.take(100) }, label = { Text(stringResource(R.string.v2_name)) })
            OutlinedTextField(description, { description = it.take(1000) }, label = { Text(stringResource(R.string.v2_description)) })
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
        viewModel.act { if (playlist == null) viewModel.store.create(name, description) else viewModel.store.rename(playlist.id, name, description) }; editing = false
    }) { Text(stringResource(R.string.v2_save)) } }, dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.v2_cancel)) } })
    if (deleting && playlist != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.v2_delete_playlist)) },
        confirmButton = { TextButton(onClick = { viewModel.act { viewModel.store.delete(playlist.id) }; selected = null; deleting = false }) { Text(stringResource(R.string.v2_delete)) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.v2_cancel)) } })
    if (adding && playlist != null) AlertDialog(onDismissRequest = { adding = false }, title = { Text(stringResource(R.string.v2_add_tracks)) }, text = {
        LazyColumn { items(library, key = { it.id }) { song -> TextButton(onClick = { viewModel.act { viewModel.store.add(playlist.id, song) }; adding = false }) { Text("${song.title} - ${song.artistName}") } } }
    }, confirmButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.v2_cancel)) } })
}

@Composable
fun AddToPlaylistDialog(song: Song, onDismiss: () -> Unit, viewModel: LocalPlaylistsViewModel = hiltViewModel()) {
    val rows by viewModel.playlists.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.v2_add_to_playlist)) }, text = {
        Column {
            OutlinedTextField(name, { name = it.take(100) }, label = { Text(stringResource(R.string.create_playlist)) })
            LazyColumn { items(rows, key = { it.id }) { row -> TextButton(onClick = { viewModel.act { viewModel.store.add(row.id, song) }; onDismiss() }) { Text(row.name) } } }
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { viewModel.act { viewModel.store.create(name, songs = listOf(song)) }; onDismiss() }) { Text(stringResource(R.string.v2_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.v2_cancel)) } })
}
