package com.prism.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.tr
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.theme.LocalContainer

@Composable
internal fun MusicTagSettings() {
    val prefs = LocalContainer.current.artistPrefs
    val saved by prefs.musicTagKey.collectAsState()
    var key by remember(saved) { mutableStateOf(saved) }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(R.string.moods_genres), style = MaterialTheme.typography.titleMedium)
        Text(tr(R.string.music_tag_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(key, { key = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(tr(R.string.music_tag_api)) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
        Row {
            PrismTextButton(onClick = { prefs.setMusicTagKey(key) }) { Text(tr(R.string.save)) }
            PrismTextButton(onClick = { prefs.setMusicTagKey("") }) { Text(tr(R.string.reset)) }
        }
    }
}
