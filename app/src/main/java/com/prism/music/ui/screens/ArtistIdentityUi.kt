package com.prism.music.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.prism.music.R
import com.prism.music.data.artists.ArtistImageRole
import com.prism.music.data.artists.ArtistImageSources
import com.prism.music.data.model.ArtistPage
import com.prism.music.tr
import com.prism.music.ui.components.ArtistSignature
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.theme.ArtistType
import com.prism.music.ui.theme.LocalContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Cached logo and handwriting are different resources. The handwriting is explicitly credited below. */
@Composable
internal fun ArtistIdentityName(page: ArtistPage, type: ArtistType, ready: Boolean, onEdit: () -> Unit) {
    val identities = LocalContainer.current.artistIdentities
    val entries by identities.entries.collectAsState()
    val automatic by identities.automatic.collectAsState()
    val api by identities.signatureApi.collectAsState()
    val logo = remember(entries[page.id], automatic, api) { identities.asset(page.id, ArtistImageRole.LOGO)?.let { identities.store.file(it) } }
    val signature = remember(entries[page.id], automatic, api) { identities.asset(page.id, ArtistImageRole.SIGNATURE)?.let { identities.store.file(it) } }
    val context = LocalContext.current
    var logoLoaded by remember(logo) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 4.dp)) {
            if (!logoLoaded) ArtistSignature(page.name, type, ready, Modifier.fillMaxWidth(), onLongPress = onEdit)
            if (logo != null) AsyncImage(
                ImageRequest.Builder(context).data(logo).size(1200, 400).build(),
                tr(R.string.artist_logo) + " · " + page.name,
                modifier = Modifier.fillMaxWidth().height(100.dp).alpha(if (logoLoaded) 1f else 0f).clickable(onClick = onEdit),
                contentScale = ContentScale.Fit, onSuccess = { logoLoaded = true }, onError = { logoLoaded = false },
            )
        }
        if (signature != null) {
            var signatureLoaded by remember(signature) { mutableStateOf(false) }
            AsyncImage(
                ImageRequest.Builder(context).data(signature).size(1000, 320).build(),
                tr(R.string.signature) + " · " + page.name,
                modifier = Modifier.fillMaxWidth(0.66f).height(72.dp).alpha(if (signatureLoaded) 1f else 0f).clickable(onClick = onEdit),
                colorFilter = if (entries[page.id]?.localSignature == null && signature.extension == "svg") androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null,
                contentScale = ContentScale.Fit, onSuccess = { signatureLoaded = true }, onError = { signatureLoaded = false },
            )
            if (signatureLoaded) Text(page.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 2.dp))
        }
    }
}

/** Inside the existing signature sheet: keep its native typography picker as the fallback. */
@Composable
internal fun ArtistIdentityControls(page: ArtistPage) {
    val c = LocalContainer.current
    val identities = c.artistIdentities
    val entries by identities.entries.collectAsState()
    val automatic by identities.automatic.collectAsState()
    val api by identities.signatureApi.collectAsState()
    val online by c.network.online.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var role by remember { mutableStateOf(ArtistImageRole.LOGO) }
    var busy by remember { mutableStateOf(false) }
    var apiText by remember(api) { mutableStateOf(api) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                if (!identities.import(page.id, role, uri)) Toast.makeText(context, tr(R.string.couldn_t_use_that_picture), Toast.LENGTH_SHORT).show()
            } finally { busy = false }
        }
    }
    Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (kind in ArtistImageRole.entries) {
            val label = tr(if (kind == ArtistImageRole.LOGO) R.string.artist_logo else R.string.signature)
            val entry = entries[page.id]
            val local = if (kind == ArtistImageRole.LOGO) entry?.localLogo else entry?.localSignature
            val shown = identities.asset(page.id, kind)
            Text(label, style = MaterialTheme.typography.titleMedium)
            shown?.let { asset ->
                AsyncImage(identities.store.file(asset), label, Modifier.fillMaxWidth().height(74.dp), contentScale = ContentScale.Fit)
                asset.source?.takeIf { it.startsWith("https://") }?.let { source ->
                    PrismTextButton(onClick = { runCatching { uriHandler.openUri(source) } }) {
                        Text(asset.license ?: "Apple Music", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrismTextButton(onClick = { role = kind; picker.launch("image/*") }, enabled = !busy) { Text(tr(R.string.choose_a_photo)) }
                if (local != null) PrismTextButton(onClick = { scope.launch { identities.store.clearLocal(page.id, kind) } }, enabled = !busy) { Text(tr(R.string.reset)) }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr(R.string.automatic_artist_images), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = automatic, onCheckedChange = identities::setAutomatic)
        }
        OutlinedTextField(
            apiText, { apiText = it }, label = { Text(tr(R.string.artist_signature_api)) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            isError = apiText.isNotBlank() && ArtistImageSources.baseUrl(apiText) == null,
        )
        Row {
            PrismTextButton(onClick = { identities.setSignatureApi(apiText) }, enabled = apiText.isBlank() || ArtistImageSources.baseUrl(apiText) != null) { Text(tr(R.string.save)) }
            if (automatic && online && !com.prism.music.data.innertube.Parser.isUploadArtist(page.id)) PrismTextButton(onClick = {
                scope.launch {
                    busy = true
                    try { identities.refresh(page, force = true) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { Toast.makeText(context, tr(R.string.try_again), Toast.LENGTH_SHORT).show() }
                    finally { busy = false }
                }
            }, enabled = !busy) { Text(tr(R.string.refresh_the_list)) }
        }
        Text(tr(R.string.artist_identity_backup_hint, tr(R.string.library)),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        Text(tr(R.string.signature_style), style = MaterialTheme.typography.titleMedium)
    }
}
