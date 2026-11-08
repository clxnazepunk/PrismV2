package com.prism.music.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.prism.music.data.PlaylistCovers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Call at profile scope, outside the dismissible menu. Keep the artist that opened the picker. */
@Composable
internal fun rememberArtistPhotoPicker(artistId: String, covers: PlaylistCovers, onError: () -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val error by rememberUpdatedState(onError)
    var pendingArtist by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = pendingArtist
        pendingArtist = null
        if (uri != null && target != null) scope.launch {
            try { if (!covers.usePhoto(covers.artistKey(target), uri)) error() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error() }
        }
    }
    return {
        pendingArtist = artistId
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
