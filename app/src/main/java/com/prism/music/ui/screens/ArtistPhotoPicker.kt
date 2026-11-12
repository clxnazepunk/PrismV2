package com.prism.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

/** Call at profile scope, outside the dismissible menu. Keep the artist that opened the picker. */
@Composable
internal fun rememberArtistPhotoPicker(artistId: String, onPicked: (String, Uri) -> Unit): () -> Unit {
    var pendingArtist by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = pendingArtist
        pendingArtist = null
        if (uri != null && target != null) onPicked(target, uri)
    }
    return {
        pendingArtist = artistId
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
