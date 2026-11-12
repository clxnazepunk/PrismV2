package com.prism.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.prism.music.R
import com.prism.music.tr
import com.prism.music.data.artists.ArtistPhotoFrame
import com.prism.music.ui.components.PrismChip
import com.prism.music.ui.components.PrismSlider
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.components.dissolveBottom
import com.prism.music.ui.theme.LocalUi
import kotlin.math.roundToInt

/** Both the editor and artist header draw the same framed photograph and fade. */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
@Composable
internal fun ArtistHeaderPhotograph(
    source: String?,
    frame: ArtistPhotoFrame?,
    modifier: Modifier = Modifier,
    scroll: () -> Int = { 0 },
    gradientFraction: Float,
    onImageSize: (Size) -> Unit = {},
    onLoadError: () -> Unit = {},
) {
    val context = LocalContext.current
    val reduceMotion = LocalUi.current.reduceMotion
    val request = remember(context, source, reduceMotion) {
        ImageRequest.Builder(context).data(source).useExistingImageAsPlaceholder(true)
            .crossfade(if (reduceMotion) 0 else 220).build()
    }
    val scale = remember(frame?.fill, frame?.zoom) {
        if (frame == null) ContentScale.Crop else object : ContentScale {
            override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
                val uniform = frame.scale(dstSize.width, dstSize.height, srcSize.width, srcSize.height)
                return ScaleFactor(uniform, uniform)
            }
        }
    }
    val alignment = remember(frame) {
        if (frame == null) Alignment.Center else Alignment { image, space, _ ->
            IntOffset(
                frame.offset(image.width.toFloat(), space.width.toFloat(), frame.x).roundToInt(),
                frame.offset(image.height.toFloat(), space.height.toFloat(), frame.y).roundToInt(),
            )
        }
    }
    Box(modifier.clipToBounds()) {
        Box(Modifier.matchParentSize().dissolveBottom(0.5f)) {
            AsyncImage(
                request, null, contentScale = scale, alignment = alignment,
                modifier = Modifier.fillMaxSize().graphicsLayer { translationY = scroll() * 0.5f },
                onSuccess = { onImageSize(it.painter.intrinsicSize) },
                onError = { onLoadError() },
            )
        }
        BoxWithConstraints(Modifier.matchParentSize()) {
            Box(Modifier.fillMaxWidth().height(maxHeight * gradientFraction)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent))))
        }
    }
}

private val frameSaver = listSaver<ArtistPhotoFrame, Any>(
    save = { listOf(it.fill, it.zoom, it.x, it.y) },
    restore = { ArtistPhotoFrame(it[0] as Boolean, it[1] as Float, it[2] as Float, it[3] as Float) },
)

@Composable
internal fun ArtistPhotoAdjustment(
    source: String,
    initial: ArtistPhotoFrame,
    headerAspect: Float,
    gradientFraction: Float,
    saving: Boolean,
    onConfirm: (ArtistPhotoFrame) -> Unit,
    onDismiss: () -> Unit,
) {
    var frame by rememberSaveable(source, stateSaver = frameSaver) { mutableStateOf(initial) }
    var imageSize by remember(source) { mutableStateOf(Size.Zero) }
    var failed by remember(source) { mutableStateOf(false) }
    val ready = imageSize.width > 0f && imageSize.height > 0f &&
        imageSize.width.isFinite() && imageSize.height.isFinite()
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            BoxWithConstraints {
                val previewWidth = minOf((maxWidth - 32.dp).coerceAtLeast(1.dp), maxHeight * 0.5f * headerAspect)
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(tr(R.string.artist_adjust_image), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    ArtistHeaderPhotograph(source, frame,
                        Modifier.width(previewWidth).aspectRatio(headerAspect).pointerInput(source, imageSize, saving) {
                            if (ready && !saving) detectTransformGestures { center, pan, zoom, _ ->
                                frame = frame.gesture(size.width.toFloat(), size.height.toFloat(), imageSize.width / imageSize.height,
                                    pan.x, pan.y, zoom, center.x, center.y)
                            }
                        },
                        gradientFraction = gradientFraction,
                        onImageSize = { imageSize = it; failed = false },
                        onLoadError = { failed = true },
                    )
                    if (failed || !ready) {
                        Text(tr(if (failed) R.string.couldn_t_use_that_picture else R.string.loading))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrismChip(!frame.fill, { if (!saving) frame = frame.copy(fill = false) }, tr(R.string.artist_photo_fit))
                        PrismChip(frame.fill, { if (!saving) frame = frame.copy(fill = true) }, tr(R.string.fill))
                    }
                    PhotoFrameSlider(R.string.artist_zoom, frame.zoom, 1f..ArtistPhotoFrame.MAX_ZOOM, ready && !saving) {
                        frame = frame.copy(zoom = it)
                    }
                    PhotoFrameSlider(R.string.artist_horizontal, frame.x, -1f..1f, ready && !saving) { frame = frame.copy(x = it) }
                    PhotoFrameSlider(R.string.artist_vertical, frame.y, -1f..1f, ready && !saving) { frame = frame.copy(y = it) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        PrismTextButton(onClick = { frame = ArtistPhotoFrame() }, enabled = !saving) { Text(tr(R.string.reset)) }
                        PrismTextButton(onClick = onDismiss, enabled = !saving) { Text(tr(R.string.cancel)) }
                        PrismTextButton(onClick = { onConfirm(frame) }, enabled = ready && !failed && !saving) {
                            Text(tr(if (saving) R.string.loading else R.string.save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoFrameSlider(labelId: Int, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    onChange: (Float) -> Unit,
) {
    val label = tr(labelId)
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        PrismSlider(value, onChange, Modifier.fillMaxWidth().semantics { contentDescription = label },
            enabled = enabled, valueRange = range)
    }
}
