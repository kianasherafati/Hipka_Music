package com.hipka.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.hipka.app.R
import com.hipka.app.domain.model.Song

data class TrackActions(val playlist: (Song) -> Unit = {}, val chat: (Song) -> Unit = {},
    val artist: (Song) -> Unit = {}, val play: (List<Song>, Song) -> Unit = { _, _ -> })
val LocalTrackActions = staticCompositionLocalOf { TrackActions() }

@Composable
fun TrackMenu(song: Song) {
    var expanded by remember { mutableStateOf(false) }
    val actions = LocalTrackActions.current
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = androidx.compose.ui.res.stringResource(com.hipka.app.R.string.track_actions)) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.v2_add_to_playlist)) }, onClick = { expanded = false; actions.playlist(song) })
            DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_47)) }, onClick = { expanded = false; actions.chat(song) })
            DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_55)) }, onClick = { expanded = false; actions.artist(song) })
            if (song.downloadable) DropdownMenuItem(text = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_56)) }, enabled = false, onClick = {})
        }
    }
}
