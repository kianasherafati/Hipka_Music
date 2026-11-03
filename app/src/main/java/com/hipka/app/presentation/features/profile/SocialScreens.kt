package com.hipka.app.presentation.features.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hipka.app.presentation.common.CoverImage
import kotlinx.coroutines.delay

@Composable
fun AccountScreen(userId: String? = null, onUser: (String) -> Unit, onMessage: (String) -> Unit,
    onSettings: () -> Unit, vm: SocialViewModel = hiltViewModel()) {
    val me by vm.me.collectAsStateWithLifecycle()
    val publicPlaylists by vm.publicPlaylists.collectAsStateWithLifecycle()
    val trackActions = com.hipka.app.presentation.common.LocalTrackActions.current
    val profile by vm.profile.collectAsStateWithLifecycle()
    val following by vm.following.collectAsStateWithLifecycle()
    val followers by vm.followers.collectAsStateWithLifecycle()
    val mine by vm.mineFollowing.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var edit by rememberSaveable { mutableStateOf(false) }
    var people by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableStateOf("") }
    var blockPrompt by rememberSaveable { mutableStateOf(false) }
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::uploadAvatar) }
    val id = userId ?: me
    LaunchedEffect(id) { id?.let(vm::load) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Hipka profile", style = MaterialTheme.typography.headlineMedium)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { id?.let(vm::load) }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_29)) } }
        }
        profile?.let { user ->
            item {
                CoverImage(model = user.avatarUrl.orEmpty(), contentDescription = user.name, modifier = Modifier.size(96.dp))
                Text(user.name, style = MaterialTheme.typography.headlineLarge)
                Text("@${user.username.orEmpty()}"); Text(user.bio)
                Row {
                    TextButton(onClick = { vm.people(followers); people = true }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.followers_count, followers.size)) }
                    TextButton(onClick = { vm.people(following); people = true }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.following_count, following.size)) }
                }
                if (id == me) {
                    TextButton(enabled = !busy, onClick = { avatarPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.upload_avatar))
                    }

                    Button(onClick = { name = user.name; bio = user.bio; avatar = user.avatarUrl.orEmpty(); edit = true }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_30)) }
                    TextButton(onClick = onSettings) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_31)) }
                    TextButton(onClick = vm::logout) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_32)) }
                } else {
                    Row {
                        Button(onClick = { vm.follow(user.id) }, enabled = !busy) { Text(if (user.id in mine) androidx.compose.ui.res.stringResource(com.hipka.app.R.string.unfollow_user) else androidx.compose.ui.res.stringResource(com.hipka.app.R.string.follow_user)) }
                        TextButton(onClick = { onMessage(user.id) }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_33)) }
                    }
                    TextButton(onClick = { blockPrompt = true }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_34)) }
                    TextButton(onClick = { vm.block(user.id, false) }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_35)) }
                }
            }
        }
        items(publicPlaylists, key = { "public-${it.id}" }) { playlist ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(playlist.name, style = MaterialTheme.typography.titleLarge)
                    Text(playlist.description)
                    Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.public_playlist))
                    TextButton(enabled = !busy, onClick = { vm.playPublic(playlist) { tracks -> trackActions.play(tracks, tracks.first()) } }) {
                        Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_play_all))
                    }
                    playlist.local().songs.forEach { Text(it.title, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    if (people) AlertDialog(onDismissRequest = { people = false }, title = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_36)) }, text = {
        LazyColumn { items(results, key = { it.id }) { user -> TextButton(onClick = { people = false; onUser(user.id) }) { Text("${user.name} @${user.username.orEmpty()}") } } }
    }, confirmButton = { TextButton(onClick = { people = false }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_37)) } })
    if (edit) AlertDialog(onDismissRequest = { edit = false }, title = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_30)) }, text = {
        Column {
            OutlinedTextField(name, { name = it.take(100) }, label = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_38)) })
            OutlinedTextField(bio, { bio = it.take(1000) }, label = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_39)) })
            OutlinedTextField(avatar, { avatar = it }, label = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_40)) })
        }
    }, confirmButton = { TextButton(enabled = name.isNotBlank() && (avatar.isBlank() || avatar.startsWith("https://")), onClick = { vm.edit(name, bio, avatar); edit = false }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_save)) } },
        dismissButton = { TextButton(onClick = { edit = false }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_cancel)) } })
    if (blockPrompt && id != null) AlertDialog(onDismissRequest = { blockPrompt = false }, title = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_41)) }, text = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_42)) },
        confirmButton = { TextButton(onClick = { vm.block(id, true); blockPrompt = false }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_43)) } }, dismissButton = { TextButton(onClick = { blockPrompt = false }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_cancel)) } })
}

@Composable
fun ConversationsScreen(onUser: (String) -> Unit, onMessage: (String) -> Unit, vm: SocialViewModel = hiltViewModel()) {
    val me by vm.me.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    // Poll only while this screen is STARTED, including reconnect catch-up.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner, me) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) { vm.inbox(); delay(10000) }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.nav_chats), style = MaterialTheme.typography.headlineLarge)
        OutlinedTextField(query, { query = it; vm.search(it) }, label = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_44)) }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = vm::inbox) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_refresh)) }
        LazyColumn {
            if (query.isNotBlank()) items(results, key = { it.id }) { user ->
                TextButton(onClick = { onUser(user.id) }) { Text("${user.name} @${user.username.orEmpty()}") }
            } else {
                val conversations = messages.groupBy { if (it.senderId == me) it.receiverId else it.senderId }
                if (conversations.isEmpty()) item { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_45)) }
                items(conversations.entries.toList(), key = { it.key }) { (peer, history) ->
                    Card(onClick = { onMessage(peer) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(history.first().text.ifBlank { "Shared song" }, maxLines = 2)
                            val unread = history.count { it.receiverId == me && it.status != "READ" }
                            if (unread > 0) Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.unread_count, unread))
                            TextButton(onClick = { onUser(peer) }) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_46)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShareSongDialog(song: com.hipka.app.domain.model.Song, onDismiss: () -> Unit, vm: SocialViewModel = hiltViewModel()) {
    var query by rememberSaveable { mutableStateOf("") }
    val results by vm.results.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_47)) }, text = {
        Column {
            Text(song.title)
            OutlinedTextField(query, { query = it; vm.search(it) }, label = { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_48)) })
            error?.let { Text(it) }
            LazyColumn { items(results, key = { it.id }) { user ->
                TextButton(enabled = !busy, onClick = { vm.share(user.id, song, onDismiss) }) { Text("${user.name} @${user.username.orEmpty()}") }
            } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.hipka.app.R.string.v2_text_37)) } })
}
