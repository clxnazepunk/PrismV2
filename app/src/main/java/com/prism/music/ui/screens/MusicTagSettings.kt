package com.prism.music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.data.ArtistPrefs
import com.prism.music.data.isMusicTagKey
import com.prism.music.data.meta.MetaRepository.TagConnection
import com.prism.music.tr
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.components.PrismTextField
import com.prism.music.ui.theme.LocalAppSettings
import com.prism.music.ui.theme.LocalContainer
import kotlinx.coroutines.launch

@Composable
internal fun MusicTagSettings() {
    val c = LocalContainer.current
    val settings = LocalAppSettings.current
    Group(tr(R.string.moods_genres)) {
        Toggle(tr(R.string.show_moods), null, settings.behavior.playlistMoods) { c.settings.setPlaylistMoods(it) }
        MusicTagKeyForm(c.artistPrefs, c.meta::checkTagConnection)
    }
}

@Composable
internal fun MusicTagKeyForm(prefs: ArtistPrefs, checkConnection: suspend (String) -> TagConnection) {
    val saved by prefs.musicTagKey.collectAsState()
    var key by remember(saved) { mutableStateOf(saved) }
    var message by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    fun save(clear: Boolean = false) {
        focusManager.clearFocus()
        val value = if (clear) "" else key.trim()
        if (!clear && !isMusicTagKey(value)) {
            message = R.string.music_tag_key_format
            return
        }
        busy = true
        scope.launch {
            try {
                val stored = prefs.setMusicTagKey(value)
                if (stored) key = value
                message = when {
                    !stored -> R.string.couldn_t_save_it
                    clear -> R.string.removed
                    else -> R.string.music_tag_saved
                }
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(R.string.music_tag_hint), style = MaterialTheme.typography.bodySmall)
        PrismTextField(
            key, { key = it; message = null }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("lastFmTagKey"),
            enabled = !busy, label = { Text(tr(R.string.music_tag_api)) },
            isError = message == R.string.music_tag_key_format,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            visualTransformation = PasswordVisualTransformation(),
        )
        message?.let { id ->
            val status = when (id) {
                R.string.couldn_t_save_it, R.string.removed -> tr(id, tr(R.string.music_tag_api))
                else -> tr(id)
            }
            Text(status, Modifier.testTag("lastFmTagStatus").semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall)
        }
        Row {
            PrismTextButton(onClick = { save() }, enabled = !busy, modifier = Modifier.testTag("saveLastFmTagKey")) { Text(tr(R.string.save)) }
            PrismTextButton(onClick = { save(clear = true) }, enabled = !busy) { Text(tr(R.string.reset)) }
        }
        Row {
            PrismTextButton(onClick = {
                focusManager.clearFocus()
                val candidate = key.trim()
                if (!isMusicTagKey(candidate)) {
                    message = R.string.music_tag_key_format
                } else {
                    message = R.string.checking
                    busy = true
                    scope.launch {
                        try {
                            message = when (checkConnection(candidate)) {
                                TagConnection.VALID -> R.string.music_tag_connection_ok
                                TagConnection.INVALID -> R.string.music_tag_key_rejected
                                TagConnection.UNAVAILABLE -> R.string.music_tag_connection_failed
                            }
                        } finally {
                            busy = false
                        }
                    }
                }
            }, enabled = !busy) { Text(tr(R.string.music_tag_check)) }
            PrismTextButton(onClick = {
                runCatching { uriHandler.openUri("https://www.last.fm/api/account/create") }
            }) { Text(tr(R.string.music_tag_get_key)) }
        }
    }
}
