package com.prism.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.prism.music.data.artists.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import java.io.File
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

private const val ARTIST_IMAGE_MIME_TYPE = "image/*"

/** Selection and edits are per artist; a failed image never hides the native fallback. */
@Composable
internal fun ArtistIdentityName(page: ArtistPage, type: ArtistType, ready: Boolean, onEdit: () -> Unit, canvasHeight: Dp = 100.dp,
    preview: Pair<ArtistImageRole, ArtistImageLayout>? = null,
) {
    val identities = LocalContainer.current.artistIdentities
    val entries by identities.entries.collectAsState()
    val automatic by identities.automatic.collectAsState()
    val api by identities.signatureApi.collectAsState()
    val saved = entries[page.id] ?: ArtistIdentity()
    val entry = when (preview?.first) {
        ArtistImageRole.LOGO -> saved.copy(logoLayout = preview.second, display = ArtistDisplayChoice(true, false, false))
        ArtistImageRole.SIGNATURE -> saved.copy(signatureLayout = preview.second, display = ArtistDisplayChoice(false, true, false))
        null -> saved
    }
    val logo = remember(entry, automatic, api) { identities.store.file(identities.asset(page.id, ArtistImageRole.LOGO)) }
    val signature = remember(entry, automatic, api) { identities.store.file(identities.asset(page.id, ArtistImageRole.SIGNATURE)) }
    var logoFailed by remember(logo) { mutableStateOf(false) }
    var signatureFailed by remember(signature) { mutableStateOf(false) }
    var logoLoaded by remember(logo) { mutableStateOf(false) }
    var signatureLoaded by remember(signature) { mutableStateOf(false) }
    val shown = entry.display?.resolve(logo != null && !logoFailed, signature != null && !signatureFailed)
        ?: ArtistDisplayChoice.automatic(logo != null && !logoFailed, signature != null && !signatureFailed)
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (shown.logo && logo != null) ArtistMark(logo, tr(R.string.artist_logo), entry.logoLayout, 100.dp, canvasHeight = canvasHeight,
            onEdit = onEdit, onSuccess = { logoLoaded = true }, onError = { logoFailed = true })
        if (shown.signature && signature != null) {
            ArtistMark(signature, tr(R.string.signature), entry.signatureLayout, 72.dp, canvasHeight = canvasHeight,
                tint = entry.localSignature == null && signature.extension == "svg",
                onEdit = onEdit, onSuccess = { signatureLoaded = true }, onError = { signatureFailed = true })
            if (entry.signatureCaption && signatureLoaded && !shown.text) Text(page.name,
                style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        }
        if (shown.text || (shown.logo && !logoLoaded) || (shown.signature && !signatureLoaded))
            ArtistSignature(page.name, type, ready, Modifier.fillMaxWidth(), onLongPress = onEdit)
    }
}

/** Fit enlarges small originals too; zoom is visual, never an unbounded image decode. */
@Composable
private fun ArtistMark(
    file: File, label: String, layout: ArtistImageLayout, height: Dp,
    tint: Boolean = false, canvasHeight: Dp = height, onEdit: () -> Unit = {}, onSuccess: () -> Unit = {}, onError: () -> Unit = {},
) {
    val context = LocalContext.current
    val fit = layout.bounded()
    val canvasPixels = with(androidx.compose.ui.platform.LocalDensity.current) { canvasHeight.toPx() }
    Box(Modifier.fillMaxWidth().height(height).clickable(onClick = onEdit)) {
        AsyncImage(
            ImageRequest.Builder(context).data(file).size(1600, 800).build(), label,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = fit.scale; scaleY = fit.scale
                translationX = size.width * fit.x; translationY = (if (fit.headerCoordinates) canvasPixels else size.height) * fit.y
            },
            contentScale = ContentScale.Fit,
            colorFilter = if (tint) androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null,
            onSuccess = { onSuccess() }, onError = { onError() },
        )
    }
}

@Composable
private fun ArtistLayoutEditor(
    page: ArtistPage, role: ArtistImageRole, type: ArtistType, headerWidth: Dp, headerHeight: Dp,
    current: ArtistImageLayout, onSave: (ArtistImageLayout) -> Unit, onDismiss: () -> Unit,
) {
    val slot = if (role == ArtistImageRole.LOGO) 100f else 72f
    var draft by remember(page.id, role) { mutableStateOf(current.forHeader(headerHeight.value, slot)) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(R.string.artist_adjust), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    PrismTextButton(onClick = onDismiss) { Text(tr(R.string.cancel)) }
                    PrismTextButton(onClick = { onSave(draft.bounded()) }) { Text(tr(R.string.save)) }
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val ratio = maxWidth / headerWidth.coerceAtLeast(1.dp)
                    Box(Modifier.fillMaxWidth().height(headerHeight * ratio).clipToBounds()) {
                        Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(headerWidth, headerHeight).graphicsLayer {
                                transformOrigin = TransformOrigin(0f, 0f); scaleX = ratio; scaleY = ratio
                            }) {
                            ArtistHeader(page, type, true, headerHeight, preview = role to draft)
                        }
                    }
                }
                Column(Modifier.padding(22.dp)) {
                    Text(tr(R.string.artist_zoom))
                    Slider(draft.scale, { draft = draft.copy(scale = it) }, valueRange = 0.25f..12f)
                    Text(tr(R.string.artist_horizontal))
                    Slider(draft.x, { draft = draft.copy(x = it) }, valueRange = -1f..1f)
                    Text(tr(R.string.artist_vertical))
                    Slider(draft.y, { draft = draft.copy(y = it) }, valueRange = -1f..1f)
                    Text(tr(R.string.artist_zoom_hint), style = MaterialTheme.typography.bodySmall)
                    PrismTextButton(onClick = { draft = ArtistImageLayout(headerCoordinates = true) }) { Text(tr(R.string.reset)) }
                }
            }
        }
    }
}

/** Inside the existing signature sheet: keep its native typography picker as the fallback. */
@Composable
internal fun ArtistIdentityControls(page: ArtistPage, type: ArtistType, headerWidth: Dp, headerHeight: Dp) {
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
    var adjusting by remember { mutableStateOf<ArtistImageRole?>(null) }
    val current = entries[page.id] ?: ArtistIdentity()
    fun change(update: (ArtistIdentity) -> ArtistIdentity) { scope.launch { identities.store.update(page.id, update) } }
    var apiText by remember(api) { mutableStateOf(api) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                if (!identities.import(page.id, role, uri)) Toast.makeText(context, tr(R.string.couldn_t_use_that_picture), Toast.LENGTH_SHORT).show()
            } finally { busy = false }
        }
    }
    adjusting?.let { kind ->
        identities.store.file(identities.asset(page.id, kind))?.let { file ->
            ArtistLayoutEditor(page, kind, type, headerWidth, headerHeight,
                if (kind == ArtistImageRole.LOGO) current.logoLayout else current.signatureLayout,
                onSave = { layout ->
                    change { if (kind == ArtistImageRole.LOGO) it.copy(logoLayout = layout) else it.copy(signatureLayout = layout) }
                    adjusting = null
                }, onDismiss = { adjusting = null })
        }
    }
    Column(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(R.string.artist_display_auto), Modifier.weight(1f))
            Switch(current.display == null, { auto -> change { it.copy(display = if (auto) null else ArtistDisplayChoice()) } })
        }
        Text(tr(R.string.artist_display_priority), style = MaterialTheme.typography.bodySmall)
        val choice = current.display ?: ArtistDisplayChoice()
        for (index in 0..2) Row(verticalAlignment = Alignment.CenterVertically) {
            val checked = when (index) { 0 -> choice.logo; 1 -> choice.signature; else -> choice.text }
            Checkbox(checked, { value ->
                val next = when (index) { 0 -> choice.copy(logo = value); 1 -> choice.copy(signature = value); else -> choice.copy(text = value) }
                if (next.logo || next.signature || next.text) change { it.copy(display = next) }
            })
            Text(tr(when (index) { 0 -> R.string.artist_logo; 1 -> R.string.signature; else -> R.string.artist_native_text }))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(current.signatureCaption, { value -> change { it.copy(signatureCaption = value) } })
            Text(tr(R.string.artist_signature_caption))
        }
        for (kind in ArtistImageRole.entries) {
            val label = tr(if (kind == ArtistImageRole.LOGO) R.string.artist_logo else R.string.signature)
            val entry = entries[page.id]
            val local = if (kind == ArtistImageRole.LOGO) entry?.localLogo else entry?.localSignature
            val shown = identities.asset(page.id, kind)
            Text(label, style = MaterialTheme.typography.titleMedium)
            shown?.let { asset ->
                identities.store.file(asset)?.let { file ->
                    ArtistMark(file, label, if (kind == ArtistImageRole.LOGO) current.logoLayout else current.signatureLayout, 74.dp,
                        tint = kind == ArtistImageRole.SIGNATURE && local == null && file.extension == "svg")
                }
                asset.source?.takeIf { it.startsWith("https://") }?.let { source ->
                    PrismTextButton(onClick = { runCatching { uriHandler.openUri(source) } }) {
                        Text(asset.license ?: tr(R.string.source), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (shown != null) Row {
                PrismTextButton(onClick = { adjusting = kind }, enabled = !busy) { Text(tr(R.string.artist_adjust)) }
                PrismTextButton(onClick = {
                    scope.launch {
                        busy = true
                        try { ArtistImageEdits.invert(context, identities.store, page.id, kind, shown) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { Toast.makeText(context, tr(R.string.couldn_t_use_that_picture), Toast.LENGTH_SHORT).show() }
                        finally { busy = false }
                    }
                }, enabled = !busy) { Text(tr(R.string.artist_invert)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrismTextButton(onClick = { role = kind; picker.launch(ARTIST_IMAGE_MIME_TYPE) }, enabled = !busy) { Text(tr(R.string.choose_a_photo)) }
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
