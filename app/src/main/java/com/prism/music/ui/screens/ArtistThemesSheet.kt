package com.prism.music.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.data.artists.ArtistImageRole
import com.prism.music.data.artists.ArtistIdentity
import com.prism.music.data.artists.ArtistTheme
import com.prism.music.tr
import com.prism.music.ui.components.PrismChip
import com.prism.music.ui.components.PrismSheet
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.components.PrismTextField
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val THEME_PHOTO_MIME = "image/*"

@Composable
internal fun ArtistThemesSheet(
    artistId: String, currentColor: Int?, currentPhoto: String?, onColor: () -> Unit, onDismiss: () -> Unit,
) {
    val identities = LocalContainer.current.artistIdentities
    val entries by identities.entries.collectAsState()
    val identity = entries[artistId] ?: ArtistIdentity()
    val active = identity.activeTheme
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var newName by rememberSaveable(artistId) { mutableStateOf("") }
    var editName by rememberSaveable(active?.id, active?.name) { mutableStateOf(active?.name.orEmpty()) }
    var photoFor by rememberSaveable(artistId) { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<ArtistTheme?>(null) }
    fun reportFailure() { Toast.makeText(context, tr(R.string.try_again), Toast.LENGTH_SHORT).show() }
    fun update(change: (ArtistIdentity) -> ArtistIdentity) {
        scope.launch {
            try { identities.store.update(artistId, change) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { reportFailure() }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = photoFor
        photoFor = null
        if (uri != null && target != null) scope.launch {
            busy = true
            try { if (!identities.importThemePhoto(artistId, target, uri)) reportFailure() }
            finally { busy = false }
        }
    }
    PrismSheet(onDismiss = onDismiss) {
        Text(tr(R.string.artist_themes), Modifier.padding(horizontal = 22.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
        LazyColumn(Modifier.heightIn(max = 560.dp), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item("default") {
                PrismChip(active == null, { if (!busy) update { it.copy(activeThemeId = null) } }, tr(R.string.default_text))
            }
            itemsIndexed(identity.themes, key = { _, item -> item.id }) { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrismChip(active?.id == item.id, { if (!busy) update { it.copy(activeThemeId = item.id) } },
                        item.name, modifier = Modifier.weight(1f))
                    IconButton(onClick = { update { it.reorderTheme(item.id, index - 1) } }, enabled = !busy && index > 0) {
                        Icon(PrismIcons.ChevronUp, tr(R.string.artist_move_earlier))
                    }
                    IconButton(onClick = { update { it.reorderTheme(item.id, index + 1) } }, enabled = !busy && index < identity.themes.lastIndex) {
                        Icon(PrismIcons.ChevronDown, tr(R.string.artist_move_later))
                    }
                    IconButton(onClick = { confirmDelete = item }, enabled = !busy) { Icon(PrismIcons.Delete, tr(R.string.remove)) }
                }
            }
            if (active != null) item("edit") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PrismTextField(editName, { editName = it.take(60) }, Modifier.weight(1f), enabled = !busy,
                            label = { Text(tr(R.string.name)) }, singleLine = true)
                        PrismTextButton(onClick = { update { it.editTheme(active.id) { theme -> theme.copy(name = editName.trim()) } } },
                            enabled = !busy && editName.isNotBlank() && editName.trim() != active.name) { Text(tr(R.string.save)) }
                    }
                    for (role in ArtistImageRole.entries) {
                        var expanded by remember(role) { mutableStateOf(false) }
                        val label = tr(if (role == ArtistImageRole.LOGO) R.string.artist_logo else R.string.signature)
                        Box {
                            PrismTextButton(onClick = { expanded = true }, enabled = !busy) { Text(label) }
                            DropdownMenu(expanded, { expanded = false }) {
                                DropdownMenuItem({ Text(tr(R.string.none)) }, {
                                    expanded = false
                                    update { it.editTheme(active.id) { theme -> theme.withAsset(role, null) } }
                                })
                                identity.assets(role).forEachIndexed { index, asset ->
                                    DropdownMenuItem({ Text(tr(R.string.artist_saved_image, index + 1)) }, {
                                        expanded = false
                                        update { it.editTheme(active.id) { theme -> theme.withAsset(role, asset) } }
                                    }, trailingIcon = { if (active.asset(role)?.file == asset.file) Icon(PrismIcons.Check, null) })
                                }
                            }
                        }
                    }
                    PrismTextButton(onClick = onColor, enabled = !busy) { Text(tr(R.string.page_color)) }
                    PrismTextButton(onClick = { photoFor = active.id; picker.launch(THEME_PHOTO_MIME) }, enabled = !busy) {
                        Text(tr(R.string.use_your_own_photo))
                    }
                    if (active.photograph != null) PrismTextButton(onClick = {
                        scope.launch {
                            try { identities.store.setThemePhoto(artistId, active.id, null) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { reportFailure() }
                        }
                    }, enabled = !busy) { Text(tr(R.string.use_youtube_s_photo)) }
                }
            }
            item("create") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PrismTextField(newName, { newName = it.take(60) }, Modifier.weight(1f), enabled = !busy,
                        label = { Text(tr(R.string.name)) }, singleLine = true)
                    PrismTextButton(onClick = {
                        val theme = ArtistTheme(java.util.UUID.randomUUID().toString(), newName.trim(), currentColor,
                            identities.asset(artistId, ArtistImageRole.LOGO), identities.asset(artistId, ArtistImageRole.SIGNATURE))
                        busy = true
                        scope.launch {
                            try {
                                identities.store.update(artistId) { it.copy(themes = it.themes + theme) }
                                if (currentPhoto != null && !identities.importThemePhoto(artistId, theme.id, Uri.parse(currentPhoto))) reportFailure()
                                identities.store.update(artistId) { it.copy(activeThemeId = theme.id) }
                                newName = ""
                            } catch (e: CancellationException) { throw e }
                            catch (_: Exception) { reportFailure() }
                            finally { busy = false }
                        }
                    }, enabled = !busy && newName.isNotBlank() && identity.themes.size < 20) { Text(tr(R.string.create)) }
                }
            }
        }
    }
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(tr(R.string.remove)) }, text = { Text(item.name) },
            confirmButton = {
                PrismTextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        try { identities.store.deleteTheme(artistId, item.id) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { reportFailure() }
                    }
                }) { Text(tr(R.string.remove)) }
            },
            dismissButton = { PrismTextButton(onClick = { confirmDelete = null }) { Text(tr(R.string.cancel)) } },
        )
    }
}
